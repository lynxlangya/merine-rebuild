package com.merine.rebuild.agent.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.merine.rebuild.agent.chat.dto.ChatErrorCode;
import com.merine.rebuild.agent.chat.dto.ChatEvent;
import com.merine.rebuild.agent.provider.ProviderKeyCipher;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 真实上游模式的回归：用本机假上游覆盖正常流、401、429、坏数据流与「模型未启用」，
 * 不访问任何外部服务。密钥只在测试内合成，断言只检查 Authorization 头形状。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "merine.agent.chat.mode=PROVIDER",
        "merine.agent.chat.demo-enabled=false",
        "merine.agent.chat.run-timeout=8s",
        "merine.agent.chat.heartbeat-interval=2s",
        "merine.agent.chat.max-active-per-user=2",
        "merine.agent.chat.max-records=20"
})
class ProviderChatRegressionTest extends ChatHttpSupport {

    private static final String MODEL_ID = "regr-upstream-model";
    private static final String KEY = "synthetic-upstream-key";

    @Autowired
    ProviderKeyCipher cipher;

    private HttpServer upstream;
    private volatile int upstreamStatus = 200;
    private volatile String upstreamBody = "";
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> upstreamRequestBody = new AtomicReference<>();

    @BeforeEach
    void startUpstream() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/chat/completions", this::handle);
        upstream.start();
    }

    @AfterEach
    void stopUpstream() {
        upstream.stop(0);
        jdbcTemplate.update("""
                DELETE FROM ai_model_provider_model
                 WHERE provider_id IN (SELECT id FROM ai_model_provider WHERE name LIKE 'REGR-UPSTREAM%')
                """);
        jdbcTemplate.update("DELETE FROM ai_model_provider WHERE name LIKE 'REGR-UPSTREAM%'");
    }

    private void handle(HttpExchange exchange) throws IOException {
        upstreamRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        if (upstreamStatus != 200) {
            exchange.sendResponseHeaders(upstreamStatus, -1);
            exchange.close();
            return;
        }
        byte[] payload = upstreamBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, payload.length);
        exchange.getResponseBody().write(payload);
        exchange.close();
    }

    /** 直接落库：URL 校验只允许 HTTPS，假上游是本机 HTTP，所以绕过接口写种子数据。 */
    private String seed(String providerStatus, String modelStatus) {
        String providerId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO ai_model_provider(id,vendor,name,remark,website,base_url,api_key_cipher,status)
                VALUES(?, 'CUSTOM',?, '回归假上游','https://example.com', ?, ?, ?)
                """, providerId, "REGR-UPSTREAM-" + providerId.substring(0, 8),
                "http://127.0.0.1:" + upstream.getAddress().getPort(),
                cipher.encrypt(providerId, KEY), providerStatus);
        jdbcTemplate.update("""
                INSERT INTO ai_model_provider_model(id,provider_id,model_id,display_name,remark,reasoning_efforts,status,sort_order)
                VALUES(?,?,?,?,?,'LOW,HIGH',?,0)
                """, UUID.randomUUID().toString(), providerId, MODEL_ID, "假上游模型", "", modelStatus);
        return providerId;
    }

    private String requestBody(String providerId, String text) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotencyKey", UUID.randomUUID().toString());
        body.put("clientConversationId", "conv-0001-demo");
        body.put("clientTimeZone", "Asia/Shanghai");
        body.put("providerId", providerId);
        body.put("modelId", MODEL_ID);
        body.put("reasoningEffort", "HIGH");
        body.put("messages", List.of(Map.of("role", "USER", "text", text)));
        return objectMapper.writeValueAsString(body);
    }

    private static String chunk(String content) {
        return "data: {\"choices\":[{\"delta\":{\"content\":\"" + content + "\"}}]}\n\n";
    }

    /** 收尾块：官方在最后一个数据块里返回 usage（choices 为空数组）。 */
    private static String usageChunk(int prompt, int completion) {
        return "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":" + prompt
                + ",\"completion_tokens\":" + completion + "}}\n\n";
    }

    @Test
    void streamsUpstreamContentWithBearerKey() throws Exception {
        String providerId = seed("ENABLED", "ENABLED");
        upstreamBody = chunk("你好，") + chunk("世界") + "data: [DONE]\n\n";

        var response = startStream(requestBody(providerId, "打个招呼"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type").orElse("")).startsWith("text/event-stream");

        List<ChatEvent> events = readEvents(response.body());
        assertTerminalOnce(events, ChatEvent.MessageStatus.SUCCEEDED);
        String text = events.stream()
                .filter(ChatEvent.TextDelta.class::isInstance)
                .map(event -> ((ChatEvent.TextDelta) event).delta())
                .reduce("", String::concat);
        assertThat(text).isEqualTo("你好，世界");
        assertThat(authorization.get()).isEqualTo("Bearer " + KEY);
        assertThat(upstreamRequestBody.get()).contains("\"reasoning_effort\":\"high\"");
        // 默认请求用量：两家官方都支持，落库后才能给出 token 数
        assertThat(upstreamRequestBody.get()).contains("\"include_usage\":true");
    }

    @Test
    void usageLandsInExecutionRecord() throws Exception {
        String providerId = seed("ENABLED", "ENABLED");
        upstreamBody = chunk("好") + usageChunk(1234, 56) + "data: [DONE]\n\n";
        var response = startStream(requestBody(providerId, "统计用量"));
        assertTerminalOnce(readEvents(response.body()), ChatEvent.MessageStatus.SUCCEEDED);

        // 落库在流收尾之后：轮询等到执行记录出现
        Map<String, Object> run = null;
        for (int attempt = 0; attempt < 30 && run == null; attempt++) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    SELECT prompt_tokens, completion_tokens, state FROM ai_chat_run
                     WHERE state = 'SUCCEEDED' AND provider_id = ?
                     ORDER BY started_at DESC LIMIT 1
                    """, providerId);
            run = rows.isEmpty() ? null : rows.getFirst();
            if (run == null) Thread.sleep(100);
        }
        assertThat(run).isNotNull();
        assertThat(run.get("prompt_tokens")).isEqualTo(1234);
        assertThat(run.get("completion_tokens")).isEqualTo(56);
    }

    /**
     * 只声明 {@code Accept: text/event-stream} 的客户端（脚本、第三方集成）在业务错误时
     * 也应当拿到 JSON 错误体；内容协商失败会让整个响应变成 500。
     */
    @Test
    void sseOnlyClientStillReceivesJsonError() throws Exception {
        String providerId = seed("ENABLED", "DISABLED");
        var response = postJsonWithAccept("/api/agent/chat", requestBody(providerId, "模型停用"),
                "text/event-stream");
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.headers().firstValue("content-type").orElse("")).startsWith("application/json");
        assertThat(objectMapper.readTree(response.body()).get("code").asText())
                .isEqualTo("CHAT_MODEL_UNAVAILABLE");
        assertThat(authorization.get()).as("开流前失败不应有出站请求").isNull();
    }

    @Test
    void rejectsReasoningEffortOutsideModelConfiguration() throws Exception {
        String providerId = seed("ENABLED", "ENABLED");
        var body = requestBody(providerId, "越界的强度").replace("\"HIGH\"", "\"MAX\"");
        var response = postJson("/api/agent/chat", body, true);
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(objectMapper.readTree(response.body()).get("code").asText())
                .isEqualTo("CHAT_MODEL_UNAVAILABLE");
        assertThat(authorization.get()).as("强度不合法时不应有出站请求").isNull();
    }

    @Test
    void mapsAuthRateLimitAndProtocolErrors() throws Exception {
        String providerId = seed("ENABLED", "ENABLED");

        upstreamStatus = 401;
        assertErrorEvent(providerId, ChatErrorCode.MODEL_AUTH_FAILED, false);

        upstreamStatus = 429;
        assertErrorEvent(providerId, ChatErrorCode.MODEL_RATE_LIMITED, true);

        // 上游对请求内容本身的拒绝（模型标识写错、档位不支持）要与协议错误区分开
        upstreamStatus = 400;
        assertErrorEvent(providerId, ChatErrorCode.MODEL_REQUEST_REJECTED, false);

        upstreamStatus = 404;
        assertErrorEvent(providerId, ChatErrorCode.MODEL_REQUEST_REJECTED, false);

        upstreamStatus = 200;
        upstreamBody = "data: {这不是 JSON}\n\n";
        assertErrorEvent(providerId, ChatErrorCode.UPSTREAM_PROTOCOL_ERROR, true);
    }

    private void assertErrorEvent(String providerId, ChatErrorCode code, boolean retryable)
            throws Exception {
        var response = startStream(requestBody(providerId, "错误用例"));
        assertThat(response.statusCode()).isEqualTo(200);
        List<ChatEvent> events = readEvents(response.body());
        assertThat(events.getLast()).isInstanceOf(ChatEvent.ChatError.class);
        var error = (ChatEvent.ChatError) events.getLast();
        assertThat(error.code()).isEqualTo(code);
        assertThat(error.retryable()).isEqualTo(retryable);
    }

    @Test
    void rejectsConnectionOrModelDisabledBeforeStreaming() throws Exception {
        String providerId = seed("ENABLED", "DISABLED");
        var body = requestBody(providerId, "未启用模型");
        var response = postJson("/api/agent/chat", body, true);
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(objectMapper.readTree(response.body()).get("code").asText())
                .isEqualTo("CHAT_MODEL_UNAVAILABLE");
        assertThat(authorization.get()).as("开流前失败不应有出站请求").isNull();
    }
}
