package com.merine.rebuild.agent.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.merine.rebuild.agent.chat.dto.ChatEvent;
import com.merine.rebuild.system.security.SystemAdminRegressionSupport;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 真实 HTTP + RANDOM_PORT 的对话测试基座：真实 Cookie/CSRF 登录、逐行读 SSE。
 *
 * <p>MockHttpSession 不能当作网络 Cookie，所以这里独立建 HttpClient；数据库隔离校验、
 * 管理员 fixture 与登录口令仍复用系统管理回归基座。具体运行模式由子类用
 * {@code @SpringBootTest(properties=...)} 决定：演示替身或真实上游。
 */
abstract class ChatHttpSupport extends SystemAdminRegressionSupport {

    @LocalServerPort
    protected int port;

    @Autowired
    TransactionTemplate transactions;

    protected HttpClient http;
    private final CookieManager cookies = new CookieManager();

    @BeforeEach
    void loginOverHttp() throws Exception {
        http = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        http.send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        HttpResponse<String> login = http.send(HttpRequest.newBuilder(uri("/api/auth/session"))
                        .header("Content-Type", "application/json")
                        .header("X-XSRF-TOKEN", csrf())
                        .POST(HttpRequest.BodyPublishers.ofString(loginBody(ADMIN_LOGIN, ADMIN_PASSWORD)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).as("真实 HTTP 登录").isEqualTo(200);
    }

    /**
     * 对话用例会为登录账号落库会话与执行记录；用后清掉，避免下一组用例删除账号时留下悬空引用。
     *
     * <p>收尾落库在别的线程里进行，可能与本清理抢锁（实测出现过死锁）：这里不抢锁而是退让重试，
     * 让应用事务先完成，几轮之后仍失败才让用例失败。
     */
    @AfterEach
    void cleanupChatHistory() throws InterruptedException {
        for (int attempt = 0; ; attempt++) {
            try {
                transactions.executeWithoutResult(status -> {
                    jdbcTemplate.update("""
                            DELETE m FROM ai_conversation_message m
                             JOIN ai_conversation c ON c.id = m.conversation_id
                            WHERE c.user_id = ?
                            """, adminUserId);
                    jdbcTemplate.update("DELETE FROM ai_chat_run WHERE user_id = ?", adminUserId);
                    jdbcTemplate.update("DELETE FROM ai_conversation WHERE user_id = ?", adminUserId);
                });
                return;
            } catch (CannotAcquireLockException | DeadlockLoserDataAccessException conflict) {
                if (attempt >= 4) throw conflict;
                Thread.sleep(200);
            }
        }
    }

    protected URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    /** 用同一合成账号再开一个独立会话的客户端：用于验证换角色/换权限后的接口判定。 */
    protected HttpClient signedInClient() throws Exception {
        return httpClientFor(ADMIN_LOGIN, ADMIN_PASSWORD);
    }

    /** 用任意合成账号开一个独立会话的客户端（含 CSRF 与登录），用于验证会话归属与数据范围。 */
    protected HttpClient httpClientFor(String loginName, String password) throws Exception {
        CookieManager clientCookies = new CookieManager();
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(clientCookies)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        client.send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        HttpResponse<String> login = client.send(HttpRequest.newBuilder(uri("/api/auth/session"))
                        .header("Content-Type", "application/json")
                        .header("X-XSRF-TOKEN", csrfOf(client))
                        .POST(HttpRequest.BodyPublishers.ofString(loginBody(loginName, password)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).as("独立客户端登录 " + loginName).isEqualTo(200);
        return client;
    }

    /** 读取某个客户端自己的 CSRF 令牌。 */
    protected static String csrfOf(HttpClient client) {
        return ((CookieManager) client.cookieHandler().orElseThrow())
                .getCookieStore().getCookies().stream()
                .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
                .map(java.net.HttpCookie::getValue)
                .findFirst()
                .orElseThrow();
    }

    protected String csrf() {
        return cookies.getCookieStore().getCookies().stream()
                .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
                .map(java.net.HttpCookie::getValue)
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少 XSRF-TOKEN Cookie"));
    }

    protected HttpResponse<InputStream> startStream(String body) throws Exception {
        return startStream(http, body);
    }

    /** 用指定客户端发起对话：会话归属、数据范围用例需要换成非管理员账号。 */
    protected HttpResponse<InputStream> startStream(HttpClient client, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(uri("/api/agent/chat"))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .header("Accept", "text/event-stream, application/json")
                        .header("X-XSRF-TOKEN", csrfOf(client))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofInputStream());
    }

    protected HttpResponse<String> postJson(String path, String body, boolean withCsrf) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (withCsrf) {
            builder.header("X-XSRF-TOKEN", csrf());
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 指定 Accept 的 JSON 请求：用于验证 SSE 客户端（只声明 text/event-stream）的错误响应。 */
    protected HttpResponse<String> postJsonWithAccept(String path, String body, String accept)
            throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/json")
                        .header("Accept", accept)
                        .header("X-XSRF-TOKEN", csrf())
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> getJson(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path))
                .header("Accept", "application/json")
                .GET()
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    protected String chatBody(String idempotencyKey, String generationId, String message, Object answers)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotencyKey", idempotencyKey);
        body.put("clientConversationId", "conv-0001-demo");
        body.put("clientTimeZone", "Asia/Shanghai");
        body.put("messages", List.of(Map.of("role", "USER", "text", message)));
        if (generationId != null) {
            body.put("generationId", generationId);
        }
        if (answers != null) {
            body.put("answers", answers);
        }
        return objectMapper.writeValueAsString(body);
    }

    /** 读完整条流；SSE 帧按空行分隔，注释与 id 行单独处理。 */
    protected List<ChatEvent> readEvents(InputStream stream) throws Exception {
        CompletableFuture<List<ChatEvent>> task = CompletableFuture.supplyAsync(() -> {
            List<ChatEvent> events = new ArrayList<>();
            StringBuilder data = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        appendEvent(events, data);
                        continue;
                    }
                    if (line.startsWith(":")) {
                        continue;
                    }
                    if (line.startsWith("data:")) {
                        if (!data.isEmpty()) {
                            data.append('\n');
                        }
                        data.append(line.substring("data:".length()).trim());
                    }
                }
                appendEvent(events, data);
                return events;
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        });
        try {
            return task.get(15, TimeUnit.SECONDS);
        } catch (TimeoutException timeout) {
            task.cancel(true);
            stream.close();
            throw new AssertionError("SSE 流在 15 秒内没有结束");
        }
    }

    private void appendEvent(List<ChatEvent> events, StringBuilder data) {
        if (data.isEmpty()) {
            return;
        }
        events.add(objectMapper.readValue(data.toString(), ChatEvent.class));
        data.setLength(0);
    }

    protected void assertTerminalOnce(List<ChatEvent> events, ChatEvent.MessageStatus status) {
        assertThat(events).isNotEmpty();
        assertThat(events.getFirst()).isInstanceOf(ChatEvent.StreamStart.class);
        int terminalIndex = -1;
        for (int index = 0; index < events.size(); index++) {
            if (events.get(index) instanceof ChatEvent.MessageDone done) {
                assertThat(terminalIndex).as("终态只能出现一次").isEqualTo(-1);
                assertThat(done.status()).isEqualTo(status);
                terminalIndex = index;
            }
        }
        assertThat(terminalIndex).as("必须有终态事件").isGreaterThanOrEqualTo(0);
        assertThat(terminalIndex).as("终态之后不能再有数据事件").isEqualTo(events.size() - 1);
    }
}
