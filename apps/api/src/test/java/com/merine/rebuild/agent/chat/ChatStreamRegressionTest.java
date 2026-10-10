package com.merine.rebuild.agent.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.merine.rebuild.agent.chat.dto.ChatEvent;
import com.merine.rebuild.agent.chat.dto.MessagePart;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * 真实 HTTP + 真实 SSE 的演示链路回归：事件顺序与终态、同 key 去重/回放/冲突、
 * 查询与停止、追问作答一次性、未登录拒绝。全部内容是脚本化合成数据，不调用外部模型。
 */
class ChatStreamRegressionTest extends ChatStreamSupport {

    private static String key() {
        return UUID.randomUUID().toString();
    }

    @Test
    void directAnswerStreamsOrderedEventsAndTerminalOnce() throws Exception {
        HttpResponse<java.io.InputStream> response = startStream(chatBody(key(), null, "本月情况", null));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type").orElse(""))
                .startsWith("text/event-stream");

        List<ChatEvent> events = readEvents(response.body());
        assertTerminalOnce(events, ChatEvent.MessageStatus.SUCCEEDED);
        ChatEvent.StreamStart start = (ChatEvent.StreamStart) events.getFirst();
        assertThat(start.protocolVersion()).isEqualTo(ChatEvent.PROTOCOL_VERSION);
        assertThat(start.executionMode()).isEqualTo(ChatEvent.ExecutionMode.LOCAL_STUB);
        assertThat(events).anyMatch(ChatEvent.TextDelta.class::isInstance);
        assertThat(events).anyMatch(event -> event instanceof ChatEvent.PartSnapshot part
                && part.part() instanceof MessagePart.Table);
        assertThat(events).anyMatch(event -> event instanceof ChatEvent.PartSnapshot part
                && part.part() instanceof MessagePart.Chart);
        assertThat(events).anyMatch(event -> event instanceof ChatEvent.PartSnapshot part
                && part.part() instanceof MessagePart.Sources);

        List<String> started = events.stream().filter(ChatEvent.PartStart.class::isInstance)
                .map(event -> ((ChatEvent.PartStart) event).partId()).toList();
        List<String> done = events.stream().filter(ChatEvent.PartDone.class::isInstance)
                .map(event -> ((ChatEvent.PartDone) event).partId()).toList();
        assertThat(done).as("每个部件恰好完成一次且顺序一致").containsExactlyElementsOf(started);
    }

    @Test
    void duplicateKeyIsRejectedWhileRunningAndReplayedAfterFinish() throws Exception {
        String key = key();
        String body = chatBody(key, null, "慢速回答", null);
        HttpResponse<java.io.InputStream> running = startStream(body);
        assertThat(running.statusCode()).isEqualTo(200);
        Thread.sleep(200);

        HttpResponse<String> conflict = postJson("/api/agent/chat", body, true);
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(readTree(conflict.body()).get("code").asText()).isEqualTo("CHAT_REQUEST_IN_PROGRESS");

        readEvents(running.body());
        HttpResponse<String> replay = postJson("/api/agent/chat", body, true);
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(readTree(replay.body()).get("data").get("status").asText()).isEqualTo("SUCCEEDED");

        HttpResponse<String> different = postJson("/api/agent/chat",
                chatBody(key, null, "另一问", null), true);
        assertThat(different.statusCode()).isEqualTo(409);
        assertThat(readTree(different.body()).get("code").asText()).isEqualTo("CHAT_REQUEST_CONFLICT");
    }

    @Test
    void statusQueryNeedsLoginAndOwnership() throws Exception {
        String key = key();
        readEvents(startStream(chatBody(key, null, "本月情况", null)).body());
        HttpResponse<String> status = getJson("/api/agent/chat/requests/" + key);
        assertThat(status.statusCode()).isEqualTo(200);
        assertThat(readTree(status.body()).get("data").get("status").asText()).isEqualTo("SUCCEEDED");

        HttpResponse<String> missing = getJson("/api/agent/chat/requests/" + key());
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(readTree(missing.body()).get("code").asText()).isEqualTo("CHAT_REQUEST_NOT_FOUND");

        HttpClient anonymous = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpResponse<String> anonymousResponse = anonymous.send(
                HttpRequest.newBuilder(uri("/api/agent/chat/requests/" + key()))
                        .header("Accept", "application/json")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(anonymousResponse.statusCode()).isEqualTo(401);
    }

    @Test
    void stopIsConfirmedByServerTerminalState() throws Exception {
        String key = key();
        HttpResponse<java.io.InputStream> running = startStream(chatBody(key, null, "慢速回答", null));
        Thread.sleep(400);
        HttpResponse<String> stop = postJson("/api/agent/chat/requests/" + key + "/stop", "", true);
        assertThat(stop.statusCode()).isEqualTo(202);

        List<ChatEvent> events = readEvents(running.body());
        assertThat(events).anyMatch(ChatEvent.CancelRequested.class::isInstance);
        assertTerminalOnce(events, ChatEvent.MessageStatus.ABORTED);
        HttpResponse<String> status = getJson("/api/agent/chat/requests/" + key);
        assertThat(readTree(status.body()).get("data").get("status").asText()).isEqualTo("ABORTED");
    }

    @Test
    void clarificationAnswerIsConsumedExactlyOnce() throws Exception {
        String question = "上个月走私多少？";
        HttpResponse<java.io.InputStream> firstResponse = startStream(chatBody(key(), null, question, null));
        assertThat(firstResponse.statusCode()).isEqualTo(200);
        List<ChatEvent> first = readEvents(firstResponse.body());
        assertTerminalOnce(first, ChatEvent.MessageStatus.AWAITING_INPUT);
        assertThat(first).anyMatch(event -> event instanceof ChatEvent.PartSnapshot part
                && part.part() instanceof MessagePart.Question);
        String generationId = ((ChatEvent.StreamStart) first.getFirst()).generationId();

        Object answers = List.of(Map.of("questionId", "Q1", "values", List.of("CASE")));
        HttpResponse<java.io.InputStream> answered = startStream(
                chatBody(key(), generationId, question, answers));
        List<ChatEvent> second = readEvents(answered.body());
        assertTerminalOnce(second, ChatEvent.MessageStatus.SUCCEEDED);
        assertThat(second).anyMatch(event -> event instanceof ChatEvent.TextDelta delta
                && delta.delta().contains("立案案件"));

        HttpResponse<String> duplicate = postJson("/api/agent/chat",
                chatBody(key(), generationId, question, answers), true);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(readTree(duplicate.body()).get("code").asText())
                .isEqualTo("CHAT_QUESTION_ALREADY_ANSWERED");
    }

    @Test
    void chatEndpointsRequireChatUsePermission() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO sys_role(role_code, role_name, status)
                VALUES('REGR-CHAT-NO-USE','无对话权限回归','ENABLED')
                """);
        long role = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE role_code='REGR-CHAT-NO-USE'", Long.class);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id=?", adminUserId);
        jdbcTemplate.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", adminUserId, role);
        try {
            HttpClient plain = signedInClient();
            var response = plain.send(HttpRequest.newBuilder(uri("/api/agent/chat"))
                            .header("Content-Type", "application/json")
                            .header("Accept", "text/event-stream, application/json")
                            .header("X-XSRF-TOKEN", csrfFor(plain))
                            .POST(HttpRequest.BodyPublishers.ofString(chatBody(key(), null, "你好", null)))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(403);
            assertThat(readTree(response.body()).get("code").asText()).isEqualTo("FORBIDDEN");
        } finally {
            jdbcTemplate.update("DELETE FROM sys_user_role WHERE role_id=?", role);
            jdbcTemplate.update("DELETE FROM sys_role WHERE id=?", role);
        }
    }

    private static String csrfFor(HttpClient client) {
        return ((java.net.CookieManager) client.cookieHandler().orElseThrow())
                .getCookieStore().getCookies().stream()
                .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
                .map(java.net.HttpCookie::getValue)
                .findFirst()
                .orElseThrow();
    }

    private JsonNode readTree(String body) {
        return objectMapper.readTree(body);
    }
}
