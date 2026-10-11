package com.merine.rebuild.agent.chat;

import com.merine.rebuild.agent.chat.dto.ChatErrorCode;
import com.merine.rebuild.agent.chat.dto.ChatRequest;
import com.merine.rebuild.agent.provider.ProviderTarget;
import com.merine.rebuild.agent.provider.dto.ProviderRequests.Vendor;
import com.merine.rebuild.agent.semantics.SemanticPromptContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 模型 HTTP/SSE 适配；工具选择与最终回答有各自请求，凭据和思维链不下发。 */
@Component
final class ProviderChatClient {
    private static final Logger log = LoggerFactory.getLogger(ProviderChatClient.class);
    private static final String TEXT_PART_ID = "p-t1";
    private static final int MAX_ERROR_BYTES = 8192;
    private enum RequestPhase { CHAT, TOOL_SELECTION, RESULT_EXPLANATION }
    private enum RejectionReason { REASONING_HISTORY, TOOL_HISTORY, UNKNOWN }
    private final ObjectMapper json;
    private final HttpClient http;
    private final ChatProperties props;
    private final String systemPrompt;
    private final ScheduledExecutorService scheduler;

    ProviderChatClient(ObjectMapper json, ChatProperties props,
                       @Value("classpath:prompts/agent-system.txt") Resource systemPromptResource,
                       SemanticPromptContext semantics,
                       @Qualifier("chatRunScheduler") ScheduledExecutorService scheduler) {
        this.json = json;
        this.props = props;
        this.systemPrompt = readSystemPrompt(systemPromptResource) + "\n\n" + semantics.content();
        this.scheduler = scheduler;
        this.http = HttpClient.newBuilder().connectTimeout(props.runTimeout())
                .version(HttpClient.Version.HTTP_1_1).build();
    }

    record ToolSelection(List<ProviderToolCall> calls) {}

    static String readSystemPrompt(Resource resource) {
        try {
            String prompt = resource.getContentAsString(StandardCharsets.UTF_8).strip();
            if (prompt.isEmpty()) throw new IllegalStateException("海防助手系统提示词不能为空");
            return prompt;
        } catch (IOException error) {
            throw new IllegalStateException("无法读取海防助手系统提示词", error);
        }
    }

    ToolSelection selectTools(ChatStreamWriter writer, ChatPacing pacing, ChatRequest request,
                              ProviderTarget target, AgentQueryTools tools) throws InterruptedException {
        Map<String, Object> body = body(target, messages(request, tools.instructions()), true);
        body.put("tools", tools.definitions());
        body.put("tool_choice", "auto");
        int[] contentChars = {0};
        ProviderToolCallCollector calls = new ProviderToolCallCollector(props);
        read(body, target, pacing, writer, RequestPhase.TOOL_SELECTION, text -> {
            if (text.length() > props.maxTotalChars() - contentChars[0]) throw protocol("模型输出超过上限");
            contentChars[0] += text.length(); // 选择阶段只保留调用，普通文本不展示也不回传。
        }, calls);
        return new ToolSelection(calls.completed());
    }

    void stream(ChatStreamWriter writer, ChatPacing pacing, ChatRequest request, ProviderTarget target)
            throws InterruptedException {
        streamAnswer(writer, pacing, request, target, List.of(), "");
    }

    void streamAnswer(ChatStreamWriter writer, ChatPacing pacing, ChatRequest request,
                      ProviderTarget target, List<String> queryResults, String instructions)
            throws InterruptedException {
        String context = instructions;
        if (!queryResults.isEmpty()) {
            String data = json.writeValueAsString(Map.of("results", queryResults.stream().map(json::readTree).toList()))
                    .replace("<", "\\u003c").replace(">", "\\u003e");
            context += "\n\n<server_query_results>\n" + data + "\n</server_query_results>";
        }
        // 独立解释请求只含文本历史和后台数据；不把非思考工具回合混入供应商的思考历史。
        Map<String, Object> body = body(target, messages(request, context), false);
        if (!writer.startPart(TEXT_PART_ID, "TEXT")) throw new ChatPacing.Stopped(ChatPacing.Reason.DISCONNECTED);
        read(body, target, pacing, writer, queryResults.isEmpty() ? RequestPhase.CHAT : RequestPhase.RESULT_EXPLANATION, text -> {
            // 上游单块可能包含整篇回答；下游事件大小必须落在浏览器解析上限内。
            for (int offset = 0; offset < text.length(); offset += 4096) {
                pacing.check();
                if (!writer.textDelta(TEXT_PART_ID, text.substring(offset, Math.min(text.length(), offset + 4096)))) {
                    throw new ChatPacing.Stopped(ChatPacing.Reason.DISCONNECTED);
                }
            }
        }, null);
        if (!writer.donePart(TEXT_PART_ID)) throw new ChatPacing.Stopped(ChatPacing.Reason.DISCONNECTED);
    }

    private List<Map<String, Object>> messages(ChatRequest request, String instructions) {
        List<Map<String, Object>> messages = new ArrayList<>(request.messages().size() + 4);
        messages.add(Map.of("role", "system", "content", systemPrompt
                + (instructions.isEmpty() ? "" : "\n\n" + instructions)));
        for (ChatRequest.ChatTurn turn : request.messages()) {
            messages.add(Map.of("role", turn.role() == ChatRequest.ChatRole.USER ? "user" : "assistant",
                    "content", turn.text()));
        }
        return messages;
    }

    private Map<String, Object> body(ProviderTarget target, List<Map<String, Object>> messages,
                                   boolean selectingTools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", target.modelId());
        body.put("stream", true);
        body.put("messages", messages);
        if (props.requestUsage()) body.put("stream_options", Map.of("include_usage", true));
        if (selectingTools) {
            // DeepSeek 选择工具时关闭思考；本阶段与最终解释均不回放工具历史。
            if (target.vendor() == Vendor.DEEPSEEK) {
                body.put("thinking", Map.of("type", "disabled"));
                body.put("reasoning_effort", "none");
            } else if (target.reasoningEffort() != null) {
                // 千问平台也托管强制思考模型（如 GLM-5.3），不能按供应商统一关思考。
                body.put("reasoning_effort", target.reasoningEffort().name().toLowerCase(java.util.Locale.ROOT));
            }
        } else if (target.reasoningEffort() != null) {
            body.put("reasoning_effort", target.reasoningEffort().name().toLowerCase(java.util.Locale.ROOT));
        }
        return body;
    }

    private void read(Map<String, Object> body, ProviderTarget target, ChatPacing pacing,
                      ChatStreamWriter writer, RequestPhase phase, Consumer<String> text, ProviderToolCallCollector calls)
            throws InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(target.baseUrl() + "/chat/completions"))
                .timeout(pacing.remaining()).header("Content-Type", "application/json")
                .header("Accept", "text/event-stream").header("Authorization", "Bearer " + target.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
        var responseFuture = http.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
        HttpResponse<InputStream> response;
        boolean headersReceived = false;
        try {
            while (true) {
                pacing.check();
                try {
                    response = responseFuture.get(100, TimeUnit.MILLISECONDS);
                    headersReceived = true;
                    break;
                } catch (TimeoutException waiting) {
                    // 等待响应头时也检查停止与同一轮总期限。
                }
            }
        } catch (ExecutionException failed) {
            if (failed.getCause() instanceof HttpTimeoutException) {
                throw new ChatUpstreamException(ChatErrorCode.MODEL_TIMEOUT, "模型响应超时，请稍后重试", true);
            }
            throw new ChatUpstreamException(ChatErrorCode.MODEL_UNAVAILABLE, "无法连接模型服务，请稍后重试", true);
        } finally {
            if (!headersReceived) {
                // 失败的第二次请求也计为用量未知，不能把第一次的用量当整轮合计。
                writer.recordUsage(null, null);
                responseFuture.thenAccept(received -> close(received.body()));
                if (!responseFuture.isDone()) responseFuture.cancel(true);
            }
        }
        InputStream input = response.body();
        var monitor = scheduler.scheduleAtFixedRate(() -> {
            try {
                pacing.check();
            } catch (ChatPacing.Stopped stopped) {
                close(input);
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
        Integer promptTokens = null;
        Integer completionTokens = null;
        try (input; var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            if (response.statusCode() >= 400) {
                int status = response.statusCode();
                RejectionReason reason = status == 400 || status == 422
                        ? rejectionReason(input, pacing) : RejectionReason.UNKNOWN;
                // 固定分类替代原始上游详情；响应可能回显密钥、提示词和业务数据，任何级别均不记录它。
                log.warn("Model request failed: messageId={} phase={} status={} reason={}",
                        writer.run().messageId(), phase, status, reason);
                throw upstreamStatus(status, phase, reason);
            }
            if (!response.headers().firstValue("Content-Type").orElse("").startsWith("text/event-stream")) {
                throw protocol("模型返回了不支持的响应格式");
            }
            boolean done = false;
            boolean finished = false;
            boolean visibleText = false;
            long receivedChars = 0;
            String payload;
            while ((payload = readPayload(reader)) != null) {
                pacing.check();
                if (payload.equals("[DONE]")) { done = true; break; }
                if (payload.isEmpty()) continue;
                receivedChars += payload.length();
                if (receivedChars > (long) props.maxTotalChars() * 32) throw protocol("模型数据流超过上限");
                JsonNode chunk;
                try {
                    chunk = json.readTree(payload);
                } catch (RuntimeException invalid) {
                    throw protocol("模型返回了无法解析的数据流");
                }
                if (!chunk.isObject() || chunk.hasNonNull("error")) throw protocol("模型返回了失败的数据流");
                var usage = chunk.path("usage");
                if (usage.isObject()) {
                    promptTokens = tokens(usage.path("prompt_tokens"));
                    completionTokens = tokens(usage.path("completion_tokens"));
                }
                var choices = chunk.path("choices");
                if (!choices.isArray()) throw protocol("模型返回了无效的候选答案");
                if (choices.isEmpty()) continue; // 用量块没有候选答案。
                if (choices.size() != 1) throw protocol("模型返回了多个候选答案");
                var choice = choices.get(0);
                if (finished) throw protocol("模型在结束标记之后继续输出答案");
                if (choice.hasNonNull("index") && (!choice.path("index").isIntegralNumber()
                        || !choice.path("index").canConvertToInt() || choice.path("index").asInt() != 0)) {
                    throw protocol("模型候选答案序号无效");
                }
                var delta = choice.path("delta");
                if (!delta.isObject()) throw protocol("模型返回了无效的增量答案");
                var content = delta.path("content");
                if (!content.isMissingNode() && !content.isNull() && !content.isTextual()) {
                    throw protocol("模型回复文本格式无效");
                }
                if (content.isTextual() && !content.asText().isEmpty()) {
                    visibleText |= !content.asText().isBlank();
                    text.accept(content.asText());
                }
                if (delta.hasNonNull("tool_calls")) {
                    if (calls == null) throw protocol("模型请求了本阶段未开放的工具");
                    calls.append(delta.path("tool_calls"));
                }
                if (choice.hasNonNull("finish_reason")) {
                    String finish = choice.path("finish_reason").asText("");
                    if (finish.equals("tool_calls") && calls != null) calls.finish();
                    else if (!finish.equals("stop")) throw protocol("模型回复未正常结束");
                    finished = true;
                }
            }
            pacing.check();
            if (!done || !finished) throw protocol("模型数据流提前断开，回答未完成");
            if (calls == null && !visibleText) throw protocol("模型未返回可展示的回答");
        } catch (IOException disconnected) {
            pacing.check();
            throw new ChatUpstreamException(ChatErrorCode.MODEL_UNAVAILABLE, "模型连接中断，请稍后重试", true);
        } finally {
            monitor.cancel(false);
            writer.recordUsage(promptTokens, completionTokens);
        }
    }

    /** SSE 按空行分帧；同一事件的多行 data 必须合并后再解析 JSON。 */
    private String readPayload(BufferedReader reader) throws IOException {
        StringBuilder data = new StringBuilder();
        boolean hasData = false;
        String line;
        while ((line = readLine(reader)) != null) {
            if (line.isEmpty()) {
                if (hasData) return data.toString().trim();
                continue;
            }
            if (!line.equals("data") && !line.startsWith("data:")) continue;
            String value = line.equals("data") ? "" : line.substring(5);
            if (value.startsWith(" ")) value = value.substring(1);
            if (hasData) data.append('\n');
            data.append(value);
            hasData = true;
            if (data.length() > (long) props.maxTotalChars() * 6 + 4096) throw protocol("模型数据块超过上限");
        }
        if (hasData) throw protocol("模型数据帧提前断开");
        return null;
    }

    private String readLine(BufferedReader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        int value;
        long limit = (long) props.maxTotalChars() * 6 + 4096;
        while ((value = reader.read()) != -1) {
            if (value == '\n') return line.toString();
            if (value == '\r') {
                reader.mark(1);
                if (reader.read() != '\n') reader.reset();
                return line.toString();
            }
            line.append((char) value);
            if (line.length() > limit) throw protocol("模型数据块超过上限");
        }
        return line.isEmpty() ? null : line.toString();
    }

    private static Integer tokens(JsonNode value) {
        return value.isIntegralNumber() && value.canConvertToInt() && value.asInt() >= 0 ? value.asInt() : null;
    }

    private static void close(InputStream input) {
        try { input.close(); } catch (IOException ignored) { /* 停止/期限已由 pacing 记录。 */ }
    }

    static ChatUpstreamException protocol(String message) {
        return new ChatUpstreamException(ChatErrorCode.UPSTREAM_PROTOCOL_ERROR, message, false);
    }

    private RejectionReason rejectionReason(InputStream input, ChatPacing pacing) {
        RejectionReason reason = RejectionReason.UNKNOWN;
        try {
            byte[] error = input.readNBytes(MAX_ERROR_BYTES + 1);
            if (error.length <= MAX_ERROR_BYTES) {
                String message = json.readTree(error).path("error").path("message").asText("")
                        .toLowerCase(java.util.Locale.ROOT);
                if (message.contains("reasoning_content")) reason = RejectionReason.REASONING_HISTORY;
                else if (message.contains("tool_calls") || message.contains("tool_call_id")) reason = RejectionReason.TOOL_HISTORY;
            }
        } catch (IOException | RuntimeException unreadable) {
            // 空、畸形或读取失败的错误体仍按原 HTTP 状态处理；不记录解析异常或原文。
        }
        pacing.check(); // 读取错误体同样受总期限、停止与断连约束。
        return reason;
    }

    private static ChatUpstreamException upstreamStatus(int status, RequestPhase phase, RejectionReason reason) {
        if (status == 401 || status == 403) return new ChatUpstreamException(ChatErrorCode.MODEL_AUTH_FAILED,
                "供应商拒绝了这次调用，请检查 API Key 与账号权限", false);
        if (status == 429) return new ChatUpstreamException(ChatErrorCode.MODEL_RATE_LIMITED,
                "模型服务限流，请稍后重试", true);
        if (status >= 500) return new ChatUpstreamException(ChatErrorCode.MODEL_UNAVAILABLE,
                "模型服务暂不可用，请稍后重试", true);
        if (status == 404) return new ChatUpstreamException(ChatErrorCode.MODEL_REQUEST_REJECTED,
                "模型服务没有这个接口或模型，请核对请求地址与模型标识", false);
        if (status == 400 || status == 422) {
            String message = phase == RequestPhase.RESULT_EXPLANATION
                    ? "后台查询已完成，但模型拒绝了结果解释请求，请核对供应商兼容配置"
                    : "模型服务拒绝了这次请求，请检查模型标识与所选推理强度是否受支持";
            if (reason != RejectionReason.UNKNOWN) message = phase == RequestPhase.RESULT_EXPLANATION
                    ? "后台查询结果已保留，模型未接受结果解释的上下文格式，请核对供应商兼容配置"
                    : "模型未接受工具回合的上下文格式，请核对供应商兼容配置";
            return new ChatUpstreamException(ChatErrorCode.MODEL_REQUEST_REJECTED, message, false);
        }
        return protocol("模型服务返回了无法处理的响应（HTTP " + status + "）");
    }
}
