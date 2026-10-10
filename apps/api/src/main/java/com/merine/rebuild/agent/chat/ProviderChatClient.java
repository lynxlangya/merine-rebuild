package com.merine.rebuild.agent.chat;

import com.merine.rebuild.agent.chat.dto.ChatErrorCode;
import com.merine.rebuild.agent.chat.dto.ChatRequest;
import com.merine.rebuild.agent.provider.ProviderTarget;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * OpenAI Chat Completions 兼容的流式客户端（DeepSeek 等）。
 *
 * 只读取 {@code choices[0].delta.content}：{@code reasoning_content}（思维链）不展示也不下发；
 * 打开 {@code stream_options.include_usage} 时顺带取出 token 用量并写进执行记录；
 * 上游错误按 HTTP 状态映射为本项目错误码，响应原文只用于判定，不回传前端。
 * 取消、断连与执行期限由 {@link ChatPacing} 逐行检查，关闭响应流即取消上游。
 */
@Component
final class ProviderChatClient {

    private static final String TEXT_PART_ID = "p-t1";

    private final ObjectMapper json;
    private final HttpClient http;
    private final ChatProperties props;

    ProviderChatClient(ObjectMapper json, ChatProperties props) {
        this.json = json;
        this.props = props;
        this.http = HttpClient.newBuilder()
                .connectTimeout(props.runTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    void stream(ChatStreamWriter writer, ChatPacing pacing, ChatRequest request, ProviderTarget target)
            throws InterruptedException {
        HttpRequest httpRequest = buildRequest(target, request);
        HttpResponse<Stream<String>> response;
        try {
            response = http.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
        } catch (HttpTimeoutException timeout) {
            throw new ChatUpstreamException(ChatErrorCode.MODEL_TIMEOUT, "模型响应超时，请稍后重试", true);
        } catch (IOException error) {
            throw new ChatUpstreamException(ChatErrorCode.MODEL_UNAVAILABLE, "无法连接模型服务，请稍后重试", true);
        }
        try (Stream<String> lines = response.body()) {
            if (response.statusCode() >= 400) {
                throw upstreamStatus(response.statusCode());
            }
            writer.startPart(TEXT_PART_ID, "TEXT");
            var iterator = lines.iterator();
            while (iterator.hasNext()) {
                pacing.check();
                String line = iterator.next();
                if (line.isEmpty() || line.startsWith(":")) {
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring("data:".length()).trim();
                if (payload.equals("[DONE]")) {
                    break;
                }
                JsonNode chunk;
                try {
                    chunk = json.readTree(payload);
                } catch (RuntimeException error) {
                    throw new ChatUpstreamException(ChatErrorCode.UPSTREAM_PROTOCOL_ERROR,
                            "模型返回了无法解析的数据流", true);
                }
                recordUsage(chunk, writer);
                JsonNode choices = chunk.path("choices");
                if (!choices.isArray() || choices.isEmpty()) {
                    continue;
                }
                JsonNode content = choices.get(0).path("delta").path("content");
                if (content.isTextual() && !content.asText().isEmpty()
                        && !writer.textDelta(TEXT_PART_ID, content.asText())) {
                    // 客户端已断开：交给下一轮 pacing 检查收尾。
                    throw new ChatPacing.Stopped(ChatPacing.Reason.DISCONNECTED);
                }
            }
            writer.donePart(TEXT_PART_ID);
        }
    }

    /**
     * 解析流里的 usage：DeepSeek 每个块都带该字段、只有最后一块有值，千问只在最后一块返回；
     * 因此只在两个计数都是数字时才记录，缺失就让用量保持未知，不猜。
     */
    private static void recordUsage(JsonNode chunk, ChatStreamWriter writer) {
        JsonNode usage = chunk.path("usage");
        if (usage.isMissingNode() || !usage.isObject()) return;
        Integer prompt = usage.path("prompt_tokens").isNumber() ? usage.path("prompt_tokens").asInt() : null;
        Integer completion = usage.path("completion_tokens").isNumber()
                ? usage.path("completion_tokens").asInt() : null;
        if (prompt != null || completion != null) {
            writer.recordUsage(prompt, completion);
        }
    }

    private HttpRequest buildRequest(ProviderTarget target, ChatRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", target.modelId());
        body.put("stream", true);
        if (props.requestUsage()) {
            // 官方文档：两家都需要显式打开，才会在最后一个数据块里返回 usage。
            body.put("stream_options", Map.of("include_usage", true));
        }
        if (target.reasoningEffort() != null) {
            // DeepSeek 等按 OpenAI 兼容的 reasoning_effort 取值（none/low/high/max）原样发送。
            body.put("reasoning_effort", target.reasoningEffort().name().toLowerCase(java.util.Locale.ROOT));
        }
        body.put("messages", request.messages().stream()
                .map(turn -> Map.of(
                        "role", turn.role() == ChatRequest.ChatRole.USER ? "user" : "assistant",
                        "content", turn.text()))
                .toList());
        try {
            return HttpRequest.newBuilder(URI.create(target.baseUrl() + "/chat/completions"))
                    .timeout(props.runTimeout())
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("Authorization", "Bearer " + target.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
        } catch (RuntimeException error) {
            throw new ChatUpstreamException(ChatErrorCode.INTERNAL, "无法组装模型请求", true);
        }
    }

    private static ChatUpstreamException upstreamStatus(int status) {
        if (status == 401 || status == 403) {
            return new ChatUpstreamException(ChatErrorCode.MODEL_AUTH_FAILED,
                    "供应商拒绝了这次调用，请检查 API Key 与账号权限", false);
        }
        if (status == 429) {
            return new ChatUpstreamException(ChatErrorCode.MODEL_RATE_LIMITED,
                    "模型服务限流，请稍后重试", true);
        }
        if (status >= 500) {
            return new ChatUpstreamException(ChatErrorCode.MODEL_UNAVAILABLE,
                    "模型服务暂不可用，请稍后重试", true);
        }
        // 400/404/422 是上游对请求内容本身的拒绝：多见于模型标识写错或该模型不支持所选推理档位。
        if (status == 404) {
            return new ChatUpstreamException(ChatErrorCode.MODEL_REQUEST_REJECTED,
                    "模型服务没有这个接口或模型，请核对请求地址与模型标识", false);
        }
        if (status == 400 || status == 422) {
            return new ChatUpstreamException(ChatErrorCode.MODEL_REQUEST_REJECTED,
                    "模型服务拒绝了这次请求，请检查模型标识与所选推理强度是否受支持", false);
        }
        return new ChatUpstreamException(ChatErrorCode.UPSTREAM_PROTOCOL_ERROR,
                "模型服务返回了无法处理的响应（HTTP " + status + "）", false);
    }
}
