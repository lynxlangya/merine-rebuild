package com.merine.rebuild.agent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.merine.rebuild.agent.chat.dto.ChatEvent;
import com.merine.rebuild.agent.chat.dto.ChatRunView;
import com.merine.rebuild.common.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockHttpSession;

/**
 * 会话落库与执行记录的隔离回归：用演示替身跑真实 HTTP 链路（不开外部调用）。
 *
 * 覆盖三层：表结构约束、服务层可见性（只认归属用户）、端到端一次执行后
 * 提问/回答/执行记录三处都落库，并且重复落库被唯一键挡住。
 */
class ChatHistoryRegressionTest extends ChatStreamSupport {

    @Autowired ChatHistoryService history;

    private long userId;
    private long otherUserId;

    @BeforeEach
    void resetFixture() {
        // 先按登录名清掉上一轮（含角色关系与会话），再建账号；只按 id 清会在 id 变化后留下悬空引用。
        cleanup();
        userId = account("regr.chat.history");
        otherUserId = account("regr.chat.history.other");
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("""
                DELETE m FROM ai_conversation_message m
                 JOIN ai_conversation c ON c.id = m.conversation_id
                 JOIN sys_user u ON u.id = c.user_id
                WHERE u.login_name LIKE 'regr.chat.history%'
                """);
        jdbcTemplate.update("""
                DELETE r FROM ai_chat_run r JOIN sys_user u ON u.id = r.user_id
                WHERE u.login_name LIKE 'regr.chat.history%'
                """);
        jdbcTemplate.update("""
                DELETE c FROM ai_conversation c JOIN sys_user u ON u.id = c.user_id
                WHERE u.login_name LIKE 'regr.chat.history%'
                """);
        jdbcTemplate.update("""
                DELETE ur FROM sys_user_role ur JOIN sys_user u ON u.id = ur.user_id
                WHERE u.login_name LIKE 'regr.chat.history%'
                """);
        jdbcTemplate.update("DELETE FROM sys_user WHERE login_name LIKE 'regr.chat.history%'");
    }

    /** 建一个只有 chat:use 权限的账号，避免依赖管理员 fixture。 */
    private long account(String loginName) {
        jdbcTemplate.update("""
                INSERT INTO sys_role (role_code, role_name, status) VALUES (?, ?, 'ENABLED')
                ON DUPLICATE KEY UPDATE role_name = VALUES(role_name)
                """, "REGR-CHAT-HISTORY", "助手历史回归角色");
        long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE role_code = 'REGR-CHAT-HISTORY'", Long.class);
        jdbcTemplate.update("""
                INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
                SELECT ?, id FROM sys_permission WHERE permission_code = 'agent:chat:use'
                """, roleId);
        jdbcTemplate.update("""
                INSERT INTO sys_user (login_name, display_name, password_hash, unit_id)
                VALUES (?, ?, ?, (SELECT id FROM sys_unit LIMIT 1))
                """, loginName, loginName, passwordEncoder.encode("regr-chat-secret-1"));
        long id = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE login_name = ?", Long.class, loginName);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", id, roleId);
        return id;
    }

    /** 落库发生在流收尾之后，客户端可能先读完事件；这里等到期望条数，最多 3 秒。 */
    private int awaitCount(int expected, String sql, Object... args) throws InterruptedException {
        Integer count = 0;
        for (int attempt = 0; attempt < 30; attempt++) {
            count = jdbcTemplate.queryForObject(sql, Integer.class, args);
            if (count != null && count == expected) return count;
            Thread.sleep(100);
        }
        return count == null ? -1 : count;
    }

    private Map<String, Object> body(String text, String idempotencyKey, String conversationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotencyKey", idempotencyKey);
        body.put("clientConversationId", "conv-0001-demo");
        if (conversationId != null) body.put("conversationId", conversationId);
        body.put("clientTimeZone", "Asia/Shanghai");
        body.put("messages", List.of(Map.of("role", "USER", "text", text)));
        return body;
    }

    @Test
    void schemaKeepsCommentsUniquesAndNoPhysicalReferences() {
        for (String table : List.of("ai_conversation", "ai_conversation_message", "ai_chat_run")) {
            Integer uncommented = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = ? AND column_comment = ''
                    """, Integer.class, table);
            assertThat(uncommented).as(table + " 每列都要有注释").isZero();
            Integer foreignKeys = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.referential_constraints
                     WHERE constraint_schema = DATABASE() AND table_name = ?
                    """, Integer.class, table);
            assertThat(foreignKeys).as(table + " 不允许物理外键").isZero();
        }
        assertThat(jdbcTemplate.queryForList("""
                SELECT index_name FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'ai_conversation_message'
                   AND non_unique = 0
                """, String.class)).contains("uk_ai_conversation_message_run");
        assertThat(jdbcTemplate.queryForList("""
                SELECT index_name FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = 'ai_chat_run' AND non_unique = 0
                """, String.class)).contains("uk_ai_chat_run_idempotency");
    }

    @Test
    void conversationIsOnlyVisibleToItsOwner() {
        String mine = history.ensureConversation(userId, null, "  第一问  ",
                new ChatHistoryService.TargetSnapshot("p-1", "连接", "model-x", null));
        assertThat(history.find(userId, mine).title()).isEqualTo("第一问");

        assertThatThrownBy(() -> history.find(otherUserId, mine))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo("CHAT_CONVERSATION_NOT_FOUND");
        assertThatThrownBy(() -> history.messages(otherUserId, mine))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> history.rename(otherUserId, mine, "改名", 0))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> history.delete(otherUserId, mine, 0))
                .isInstanceOf(ApiException.class);

        // 只有本人能看到它
        assertThat(history.list(userId, 1, 20).items()).extracting("id").contains(mine);
        assertThat(history.list(otherUserId, 1, 20).items()).isEmpty();
    }

    @Test
    void renameAndDeleteUseOptimisticVersion() {
        String id = history.ensureConversation(userId, null, "待改名", null);
        jdbcTemplate.update("UPDATE ai_conversation SET last_provider_id='',last_provider_name='',last_model_id='' WHERE id=?",
                id);
        assertThat(history.rename(userId, id, "新标题", 0).title()).isEqualTo("新标题");
        assertThatThrownBy(() -> history.rename(userId, id, "再次改名", 0))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo("CHAT_CONVERSATION_CONFLICT");

        history.delete(userId, id, 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_conversation WHERE id=?", Integer.class, id)).isZero();
    }

    @Test
    void oneTurnPersistsQuestionAnswerAndExecutionRecord() throws Exception {
        var session = signIn("regr.chat.history", "regr-chat-secret-1");
        var client = httpClientFor("regr.chat.history", "regr-chat-secret-1");
        String key = UUID.randomUUID().toString();
        var response = startStream(client,
                objectMapper.writeValueAsString(body("帮我看看昨天的情报", key, null)));
        List<ChatEvent> events = readEvents(response.body());
        assertTerminalOnce(events, ChatEvent.MessageStatus.SUCCEEDED);
        String conversationId = events.stream()
                .filter(ChatEvent.StreamStart.class::isInstance)
                .map(event -> ((ChatEvent.StreamStart) event).conversationId())
                .findFirst()
                .orElseThrow();
        assertThat(conversationId).isNotBlank();

        assertThat(awaitCount(2,
                "SELECT COUNT(*) FROM ai_conversation_message WHERE conversation_id = ?",
                conversationId)).isEqualTo(2);
        List<Map<String, Object>> messages = jdbcTemplate.queryForList("""
                SELECT role, text, status FROM ai_conversation_message
                 WHERE conversation_id = ? ORDER BY seq
                """, conversationId);
        assertThat(messages).hasSize(2);
        assertThat(messages.getFirst()).containsEntry("role", "USER")
                .containsEntry("text", "帮我看看昨天的情报");
        assertThat(messages.get(1)).containsEntry("role", "ASSISTANT")
                .containsEntry("status", "SUCCEEDED");
        assertThat((String) messages.get(1).get("text")).isNotBlank();

        List<Map<String, Object>> runs = jdbcTemplate.queryForList("""
                SELECT user_id, idempotency_key, mode, state, error_code, output_chars, prompt_tokens
                  FROM ai_chat_run WHERE conversation_id = ?
                """, conversationId);
        assertThat(runs).as("一次执行应留下恰好一条记录").hasSize(1);
        Map<String, Object> run = runs.getFirst();
        assertThat(run).containsEntry("mode", "DEMO").containsEntry("state", "SUCCEEDED")
                .containsEntry("error_code", "").containsEntry("idempotency_key", key)
                .containsEntry("user_id", userId);
        assertThat((Integer) run.get("output_chars")).isPositive();
        assertThat(run.get("prompt_tokens")).isNull();

        Map<String, Object> conversation = jdbcTemplate.queryForMap("""
                SELECT title, message_count FROM ai_conversation WHERE id = ?
                """, conversationId);
        assertThat(conversation).containsEntry("title", "帮我看看昨天的情报");
        assertThat((Integer) conversation.get("message_count")).isEqualTo(2);

        // 前端拿到会话后再提问：历史累加到同一会话
        var again = startStream(client, objectMapper.writeValueAsString(
                body("再补充一句", UUID.randomUUID().toString(), conversationId)));
        assertTerminalOnce(readEvents(again.body()), ChatEvent.MessageStatus.SUCCEEDED);
        assertThat(awaitCount(4,
                "SELECT COUNT(*) FROM ai_conversation_message WHERE conversation_id = ?",
                conversationId)).isEqualTo(4);
    }

    @Test
    void duplicateTurnIsRejectedByUniqueKeys() {
        String conversationId = history.ensureConversation(userId, null, "重复落库",
                new ChatHistoryService.TargetSnapshot("p-1", "连接", "model-x", null));
        ChatRun run = new ChatRun(userId, UUID.randomUUID().toString(), "digest",
                UUID.randomUUID().toString(), UUID.randomUUID().toString(), "conv-dup");
        run.attachConversation(conversationId);
        run.finish(ChatRunView.ChatRunState.SUCCEEDED);
        history.recordTurn(userId, conversationId, "DEMO",
                new ChatHistoryService.TargetSnapshot("p-1", "连接", "model-x", null),
                run, "一问", "一答", 2);
        assertThatThrownBy(() -> history.recordTurn(userId, conversationId, "DEMO",
                new ChatHistoryService.TargetSnapshot("p-1", "连接", "model-x", null),
                run, "一问", "一答", 2))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ai_conversation_message WHERE conversation_id = ?
                """, Integer.class, conversationId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_chat_run WHERE conversation_id = ?", Integer.class,
                conversationId)).isEqualTo(1);
    }

    @Test
    void runListAndStatsFollowFiltersAndRejectBadParameters() throws Exception {
        var session = signIn("regr.chat.history", "regr-chat-secret-1");
        var client = httpClientFor("regr.chat.history", "regr-chat-secret-1");
        String key = UUID.randomUUID().toString();
        var response = startStream(client,
                objectMapper.writeValueAsString(body("筛选口径验证", key, null)));
        assertTerminalOnce(readEvents(response.body()), ChatEvent.MessageStatus.SUCCEEDED);
        assertThat(awaitCount(1,
                "SELECT COUNT(*) FROM ai_chat_run WHERE user_id = ?", userId)).isEqualTo(1);

        // 明细与聚合都按状态筛选；非法状态与越界偏移都是 400 而不是 500
        assertThat(getJson("/api/agent/chat/runs?state=SUCCEEDED", session).getResponse().getStatus())
                .isEqualTo(200);
        assertThat(intOf(bodyOf(getJson("/api/agent/chat/runs?state=ABORTED", session)), "$.data.total"))
                .isZero();
        // 状态取值由控制器显式校验（INVALID_PARAMETER），参数绑定越界走 VALIDATION_ERROR
        assertError(getJson("/api/agent/chat/runs?state=BOGUS", session), 400, "INVALID_PARAMETER");
        assertError(getJson("/api/agent/chat/runs/stats?offsetMinutes=9999", session), 400,
                "VALIDATION_ERROR");
        assertError(getJson("/api/agent/chat/runs?pageSize=0", session), 400, "VALIDATION_ERROR");

        // 明细与聚合共用时间范围：塞一条 30 天前的记录，只有不带范围时才应出现
        jdbcTemplate.update("""
                INSERT INTO ai_chat_run(id,user_id,conversation_id,idempotency_key,message_id,generation_id,mode,
                                        provider_id,provider_name,model_id,reasoning_effort,state,error_code,error_message,
                                        started_at,finished_at,duration_ms,input_chars,output_chars)
                VALUES(?,?,(SELECT id FROM ai_conversation WHERE user_id=? LIMIT 1),?,'old-message','','DEMO',
                       '','','','','SUCCEEDED','','',DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 30 DAY),DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 30 DAY),
                       10,1,1)
                """, UUID.randomUUID().toString(), userId, userId, UUID.randomUUID().toString());
        assertThat(intOf(bodyOf(getJson("/api/agent/chat/runs?days=7", session)), "$.data.total"))
                .as("时间范围把更早的记录挡在外面").isEqualTo(1);
        assertThat(intOf(bodyOf(getJson("/api/agent/chat/runs", session)), "$.data.total"))
                .as("不带范围时明细不限时间").isEqualTo(2);
        assertError(getJson("/api/agent/chat/runs?days=0", session), 400, "VALIDATION_ERROR");

        var stats = getJson("/api/agent/chat/runs/stats?days=7&offsetMinutes=480", session);
        assertThat(stats.getResponse().getStatus()).isEqualTo(200);
        assertThat(intOf(bodyOf(stats), "$.data.totals.runs")).isEqualTo(1);
        assertThat(intOf(bodyOf(stats), "$.data.totals.succeeded")).isEqualTo(1);
        // 没有用量的是计数（不是布尔）：本次假上游没返回 usage，所以正好 1 条
        Integer withoutUsage = jsonOf(bodyOf(stats), "$.data.totals.runsWithoutUsage");
        assertThat(withoutUsage).isEqualTo(1);
        // 当天一定落在补零所需的最后一天（本地日期分桶），模型分布里也只有这一个模型
        List<String> dates = jsonOf(bodyOf(stats), "$.data.series[*].date");
        assertThat(dates).hasSize(1);
        assertThat(dates.getFirst()).isNotBlank();
        // 本轮走演示替身，执行记录里的模型是空串（没有真实连接），分布里仍然只有一格
        String modelId = jsonOf(bodyOf(stats), "$.data.models[0].modelId");
        assertThat(modelId).isEmpty();
        assertThat(intOf(bodyOf(stats), "$.data.models[0].runs")).isEqualTo(1);
    }

    @Test
    void conversationEndpointsRequireChatPermissionAndStayScoped() throws Exception {
        assertUnauthenticatedJson(getJson("/api/agent/conversations", null));
        MockHttpSession session = signIn("regr.chat.history", "regr-chat-secret-1");
        String conversationId = history.ensureConversation(userId, null, "接口会话", null);
        var listed = getJson("/api/agent/conversations", session);
        assertThat(listed.getResponse().getStatus()).isEqualTo(200);
        assertThat(bodyOf(listed)).contains(conversationId, "接口会话");

        Map<String, Object> rename = Map.of("title", "接口改名", "version", 0);
        var renamed = sendJson(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/agent/conversations/" + conversationId)
                .content(objectMapper.writeValueAsString(rename)), session);
        assertThat(renamed.getResponse().getStatus()).isEqualTo(200);
        assertThat(bodyOf(renamed)).contains("接口改名");

        var runs = getJson("/api/agent/chat/runs", session);
        assertThat(runs.getResponse().getStatus()).isEqualTo(200);

        // 删除需要当前版本；版本过期一律 409
        var stale = sendJson(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/agent/conversations/" + conversationId + "?version=0"), session);
        assertError(stale, 409, "CHAT_CONVERSATION_CONFLICT");
        var deleted = sendJson(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/agent/conversations/" + conversationId + "?version=1"), session);
        assertThat(deleted.getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_conversation WHERE id=?", Integer.class, conversationId))
                .isZero();
    }
}
