package com.merine.rebuild.agent.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.merine.rebuild.agent.AgentSecretCipher;
import com.merine.rebuild.agent.datasource.dto.DataSourceCheckOutcome;
import com.merine.rebuild.agent.datasource.dto.DataSourceRequests.DbType;
import com.merine.rebuild.agent.datasource.dto.DataSourceRequests.Save;
import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.system.security.SystemAdminRegressionSupport;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 数据源管理回归：真实 Spring + 隔离 MySQL + 本项目的合成只读/写/角色账号。
 *
 * <p>账号与视图由 {@code ./scripts/dev.sh test-db} 在测试 MySQL 中准备：
 * 表级只读、库级只读、全局只读、角色只读、列级受限、含写权限六种；密码只从环境变量读。
 * 目标地址范围由测试属性收窄，越界地址不发起连接。
 */
@TestPropertySource(properties = {
        "merine.agent.provider-encryption-key=000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
        "merine.agent.datasource.allowed-networks=172.16.0.0/12,127.0.0.0/8,::1/128,10.255.255.0/24",
        "merine.agent.datasource.allowed-ports=3306",
        "merine.agent.datasource.connect-timeout-ms=1000",
        "merine.agent.datasource.socket-timeout-ms=2000",
        "merine.agent.datasource.max-concurrent-checks=2",
        "merine.agent.datasource.acquire-timeout-ms=2000"})
@DisplayName("数据源管理与检查")
class DataSourceAdminRegressionTest extends SystemAdminRegressionSupport {
    private static final String URL = "/api/agent/datasources";
    private static final String DRAFT_URL = URL + "/check";
    private static final String DATABASE = "merine_rebuild_test";
    private static final String HOST = "mysql";
    private static final String PRIMARY_VIEW = "regr_ds_view_alpha";
    private static final String SECONDARY_VIEW = "regr_ds_view_beta";
    private static final String READ_ROLE_CODE = "REGR-DS-VIEW";

    @Autowired
    private AgentSecretCipher cipher;
    @Autowired
    private DataSourceStore store;
    @Autowired
    private DataSourceViewQuery viewQuery;
    @Value("${merine.test.datasource-fixture-password:}")
    private String fixturePassword;

    @BeforeEach
    void requireFixtures() {
        assertThat(fixturePassword)
                .as("请先执行 ./scripts/dev.sh test-db 准备数据源检查合成账号")
                .isNotBlank();
    }

    @BeforeEach
    void cleanDatasources() {
        jdbcTemplate.update("DELETE FROM ai_data_source_view WHERE data_source_id IN "
                + "(SELECT id FROM ai_data_source WHERE name LIKE 'REGR-DS%')");
        jdbcTemplate.update("DELETE FROM ai_data_source WHERE name LIKE 'REGR-DS%'");
    }

    @AfterEach
    void cleanupFixtures() {
        jdbcTemplate.update("DELETE FROM ai_data_source_view WHERE data_source_id IN "
                + "(SELECT id FROM ai_data_source WHERE name LIKE 'REGR-DS%')");
        jdbcTemplate.update("DELETE FROM ai_data_source WHERE name LIKE 'REGR-DS%'");
        jdbcTemplate.update("DELETE ur FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id "
                + "WHERE r.role_code = ?", READ_ROLE_CODE);
        jdbcTemplate.update("DELETE rp FROM sys_role_permission rp JOIN sys_role r ON r.id = rp.role_id "
                + "WHERE r.role_code = ?", READ_ROLE_CODE);
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code = ?", READ_ROLE_CODE);
    }

    private Map<String, Object> input(String name, String username) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", name);
        data.put("remark", "隔离回归配置");
        data.put("dbType", "MYSQL");
        data.put("host", HOST);
        data.put("port", 3306);
        data.put("databaseName", DATABASE);
        data.put("username", username);
        data.put("password", fixturePassword);
        data.put("views", List.of());
        return data;
    }

    private Map<String, Object> draft(String username, List<String> views) {
        Map<String, Object> data = new HashMap<>();
        data.put("dbType", "MYSQL");
        data.put("host", HOST);
        data.put("port", 3306);
        data.put("databaseName", DATABASE);
        data.put("username", username);
        data.put("password", fixturePassword);
        data.put("views", views);
        return data;
    }

    private MvcResult create(MockHttpSession session, Map<String, Object> data) throws Exception {
        return sendJson(post(URL).content(objectMapper.writeValueAsString(data)), session);
    }

    private MvcResult update(MockHttpSession session, String id, Map<String, Object> data)
            throws Exception {
        return sendJson(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(URL + "/" + id)
                        .content(objectMapper.writeValueAsString(data)),
                session);
    }

    private MvcResult draftCheck(MockHttpSession session, Map<String, Object> data) throws Exception {
        return sendJson(post(DRAFT_URL).content(objectMapper.writeValueAsString(data)), session);
    }

    private MvcResult checkSaved(MockHttpSession session, String id) throws Exception {
        return sendJson(post(URL + "/" + id + "/check"), session);
    }

    private MvcResult status(MockHttpSession session, String id, String status, int version)
            throws Exception {
        return sendJson(post(URL + "/" + id + "/status")
                .content(objectMapper.writeValueAsString(Map.of("status", status, "version", version))),
                session);
    }

    private String idOf(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return jsonOf(bodyOf(result), "$.data.id");
    }

    private String storedCipher(String id) {
        return jdbcTemplate.queryForObject(
                "SELECT password_cipher FROM ai_data_source WHERE id = ?", String.class, id);
    }

    private String storedCheckStatus(String id) {
        return jdbcTemplate.queryForObject(
                "SELECT last_check_status FROM ai_data_source WHERE id = ?", String.class, id);
    }

    private String outcomeOf(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return jsonOf(bodyOf(result), "$.data.outcome");
    }

    @Test
    @DisplayName("新增、编辑与密码保持/替换都不回显凭据")
    void credentialsAreStoredEncryptedAndNeverReturned() throws Exception {
        MockHttpSession session = adminSession();
        Map<String, Object> data = input("REGR-DS-SECRET", "regr_ds_ro_table");
        MvcResult created = create(session, data);
        String id = idOf(created);
        String body = bodyOf(created);
        assertThat(body).doesNotContain(fixturePassword, "password_cipher", "\"password\"");
        assertThat((String) jsonOf(body, "$.data.status")).isEqualTo("DISABLED");
        assertThat((Object) jsonOf(body, "$.data.lastCheckAt")).isNull();

        String before = storedCipher(id);
        assertThat(before).startsWith("v1.").doesNotContain(fixturePassword);
        assertThat(cipher.decrypt(DataSourceStore.secretAad(id), before)).isEqualTo(fixturePassword);

        // 留空保留原密文；只改备注不触发检查失效。
        data.put("version", 0);
        data.put("password", "");
        data.put("remark", "仅改备注");
        assertThat(update(session, id, data).getResponse().getStatus()).isEqualTo(200);
        assertThat(storedCipher(id)).isEqualTo(before);

        String replacement = "regr-synthetic-replacement-1";
        data.put("version", 1);
        data.put("password", replacement);
        assertThat(update(session, id, data).getResponse().getStatus()).isEqualTo(200);
        assertThat(storedCipher(id)).isNotEqualTo(before);
        assertThat(cipher.decrypt(DataSourceStore.secretAad(id), storedCipher(id)))
                .isEqualTo(replacement);

        MvcResult listed = getJson(URL + "?search=REGR-DS-SECRET", session);
        assertThat(bodyOf(listed)).doesNotContain(fixturePassword, replacement, "password_cipher");
        List<String> summaries = jdbcTemplate.queryForList(
                "SELECT summary FROM sys_audit_log WHERE target_id = ?", String.class, id);
        assertThat(String.join(" ", summaries)).doesNotContain(fixturePassword, replacement);
    }

    @Test
    @DisplayName("非法地址、端口、库名与重复名称在写入口被拒绝")
    void validationRejectsUnsafeInput() throws Exception {
        MockHttpSession session = adminSession();
        Map<String, Object> data = input("REGR-DS-INVALID", "regr_ds_ro_table");
        data.put("port", 3307);
        assertValidationError(create(session, data), "port");
        data.put("port", 3306);

        data.put("host", "http://mysql");
        assertValidationError(create(session, data), "host");
        data.put("host", "mysql;DROP DATABASE x");
        assertValidationError(create(session, data), "host");
        data.put("host", HOST);

        data.put("databaseName", "merine-rebuild-test");
        assertValidationError(create(session, data), "databaseName");
        data.put("databaseName", DATABASE);

        data.put("username", "");
        assertValidationError(create(session, data), "username");

        data.put("username", "regr_ds_ro_table");
        data.put("password", "");
        assertValidationError(create(session, data), "password");
        data.put("password", fixturePassword);

        data.put("views", List.of("regr_ds_view_alpha; DROP TABLE sys_unit"));
        assertValidationError(create(session, data), "views");
        data.put("views", List.of(PRIMARY_VIEW, PRIMARY_VIEW));
        assertValidationError(create(session, data), "views");
        data.put("views", List.of());

        String id = idOf(create(session, data));
        assertError(create(session, data), 409, "DATASOURCE_NAME_EXISTS");
        data.put("version", 0);
        assertThat(update(session, id, data).getResponse().getStatus()).isEqualTo(200);
        assertError(update(session, id, data), 409, "DATASOURCE_CONFLICT");
        assertError(sendJson(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        URL + "/00000000-0000-0000-0000-000000000000")
                        .content(objectMapper.writeValueAsString(data)), session), 404, "DATASOURCE_NOT_FOUND");
        assertThat(getJson(URL + "?pageSize=1000", session).getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("启用必须重新检查并已勾选视图；改连接参数后旧结果失效且旧版本不能启用")
    void enableRequiresAFreshCheckAndSelectedViews() throws Exception {
        MockHttpSession session = adminSession();
        Map<String, Object> data = input("REGR-DS-GATE", "regr_ds_ro_table");
        String id = idOf(create(session, data));

        assertError(status(session, id, "ENABLED", 0), 409, "DATASOURCE_NO_VIEWS");

        data.put("version", 0);
        data.put("password", "");
        data.put("views", List.of(PRIMARY_VIEW));
        MvcResult updated = update(session, id, data);
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        int version = intOf(bodyOf(updated), "$.data.version");

        assertThat(outcomeOf(checkSaved(session, id))).isEqualTo("PASSED");
        assertThat(storedCheckStatus(id)).isEqualTo("PASSED");

        MvcResult enabled = status(session, id, "ENABLED", version);
        assertThat(enabled.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) jsonOf(bodyOf(enabled), "$.data.status")).isEqualTo("ENABLED");
        assertThat((String) jsonOf(bodyOf(enabled), "$.data.lastCheckStatus")).isEqualTo("PASSED");
        int enabledVersion = intOf(bodyOf(enabled), "$.data.version");

        // 连接参数变化：旧检查清除、强制停用，使用变化前版本启用必须冲突。
        data.put("version", enabledVersion);
        data.put("databaseName", "merine_rebuild");
        MvcResult changed = update(session, id, data);
        assertThat((String) jsonOf(bodyOf(changed), "$.data.status")).isEqualTo("DISABLED");
        assertThat((Object) jsonOf(bodyOf(changed), "$.data.lastCheckAt")).isNull();
        assertError(status(session, id, "ENABLED", enabledVersion), 409, "DATASOURCE_CONFLICT");

        // 恢复目标库：重新检查后可以启用，再停用。
        int changedVersion = intOf(bodyOf(changed), "$.data.version");
        data.put("version", changedVersion);
        data.put("databaseName", DATABASE);
        MvcResult restored = update(session, id, data);
        int restoredVersion = intOf(bodyOf(restored), "$.data.version");
        assertThat(outcomeOf(checkSaved(session, id))).isEqualTo("PASSED");
        MvcResult reenabled = status(session, id, "ENABLED", restoredVersion);
        assertThat(reenabled.getResponse().getStatus()).isEqualTo(200);
        int reenabledVersion = intOf(bodyOf(reenabled), "$.data.version");
        MvcResult disabled = status(session, id, "DISABLED", reenabledVersion);
        assertThat((String) jsonOf(bodyOf(disabled), "$.data.status")).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("真实 MySQL 授权元数据分支：只读通过、写权限/角色/列级/越界分别拒绝")
    void authorizationOutcomesComeFromRealMysqlMetadata() throws Exception {
        MockHttpSession session = adminSession();

        Map<String, Object> tableRead = draft("regr_ds_ro_table", List.of(PRIMARY_VIEW));
        MvcResult passed = draftCheck(session, tableRead);
        assertThat(outcomeOf(passed)).isEqualTo("PASSED");
        List<String> detected = jsonOf(bodyOf(passed), "$.data.detectedViews");
        assertThat(detected).containsExactly(PRIMARY_VIEW);

        assertThat(outcomeOf(draftCheck(session, draft("regr_ds_ro_table", List.of(SECONDARY_VIEW)))))
                .isEqualTo("VIEW_DENIED");

        Map<String, Object> schemaRead = draft("regr_ds_ro_schema", List.of(PRIMARY_VIEW, SECONDARY_VIEW));
        MvcResult schemaPassed = draftCheck(session, schemaRead);
        assertThat(outcomeOf(schemaPassed)).isEqualTo("PASSED");
        assertThat((List<String>) jsonOf(bodyOf(schemaPassed), "$.data.detectedViews"))
                .contains(PRIMARY_VIEW, SECONDARY_VIEW);

        assertThat(outcomeOf(draftCheck(session, draft("regr_ds_ro_global", List.of(SECONDARY_VIEW)))))
                .as("全局 SELECT 是只读；元数据里其它账号的权限不能算到本账号")
                .isEqualTo("PASSED");

        assertThat(outcomeOf(draftCheck(session, draft("regr_ds_ro_role", List.of(SECONDARY_VIEW)))))
                .as("角色授权读不到，只能判无法确认")
                .isEqualTo("AUTHORIZATION_UNCONFIRMED");

        assertThat(outcomeOf(draftCheck(session, draft("regr_ds_ro_column", List.of(PRIMARY_VIEW)))))
                .as("列级 SELECT 不能当整视图可读")
                .isEqualTo("VIEW_DENIED");

        MvcResult writer = draftCheck(session, draft("regr_ds_writer", List.of(PRIMARY_VIEW)));
        assertThat(outcomeOf(writer)).isEqualTo("WRITE_PRIVILEGE");
        assertThat((String) jsonOf(bodyOf(writer), "$.data.message")).contains("INSERT");

        Map<String, Object> wrong = draft("regr_ds_ro_table", List.of());
        wrong.put("password", "regr-wrong-password-value");
        MvcResult failed = draftCheck(session, wrong);
        assertThat(outcomeOf(failed)).isEqualTo("CONNECTION_FAILED");
        assertThat(bodyOf(failed)).doesNotContain("regr-wrong-password-value");

        Map<String, Object> outside = draft("regr_ds_ro_table", List.of());
        outside.put("host", "192.0.2.1");
        MvcResult denied = draftCheck(session, outside);
        assertThat(outcomeOf(denied)).isEqualTo("CONNECTION_FAILED");
        assertThat((String) jsonOf(bodyOf(denied), "$.data.message")).contains("未授权");

        Map<String, Object> unreachable = draft("regr_ds_ro_table", List.of());
        unreachable.put("host", "10.255.255.1");
        MvcResult timeout = draftCheck(session, unreachable);
        assertThat(outcomeOf(timeout)).as("允许网段内不可达目标必须超时返回，不重试")
                .isEqualTo("CONNECTION_FAILED");
    }

    @Test
    @DisplayName("连续检查正确释放并发名额，旧版本的检查结论不能落库")
    void checksReleaseResourcesAndStaleResultsAreRejected() throws Exception {
        MockHttpSession session = adminSession();
        Map<String, Object> data = input("REGR-DS-STALE", "regr_ds_ro_table");
        String id = idOf(create(session, data));
        for (int round = 0; round < 5; round += 1) {
            assertThat(outcomeOf(checkSaved(session, id))).isEqualTo("PASSED");
        }

        int version = jdbcTemplate.queryForObject(
                "SELECT version FROM ai_data_source WHERE id = ?", Integer.class, id);
        Save metadataUpdate = new Save("REGR-DS-STALE", "检查期间被改备注", DbType.MYSQL, HOST,
                3306, DATABASE, "regr_ds_ro_table", null, List.of(PRIMARY_VIEW), version);
        store.update(id, metadataUpdate);
        jdbcTemplate.update("UPDATE ai_data_source SET last_check_at = NULL, last_check_status = NULL, "
                + "last_check_message = '', checked_version = NULL WHERE id = ?", id);

        assertThatThrownBy(() -> store.recordCheck(id, version, DataSourceCheckOutcome.PASSED,
                "检查期间配置已变化", Instant.now()))
                .isInstanceOf(ApiException.class);
        assertThat(storedCheckStatus(id)).isNull();
        assertThatThrownBy(() -> store.enableChecked(id, version, "旧检查结论", Instant.now()))
                .isInstanceOf(ApiException.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM ai_data_source WHERE id = ?", String.class, id)).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("未登录、缺 CSRF 与只读角色都按权限边界拒绝")
    void permissionsMenusAndCsrfBoundaries() throws Exception {
        assertUnauthenticatedJson(getJson(URL, null));
        MockHttpSession admin = adminSession();
        Map<String, Object> data = input("REGR-DS-AUTH", "regr_ds_ro_table");
        assertThat(mockMvc.perform(post(URL).session(admin).contentType("application/json")
                .content(objectMapper.writeValueAsString(data))).andReturn()
                .getResponse().getStatus()).as("缺少 CSRF 的写请求必须拒绝").isEqualTo(403);

        jdbcTemplate.update("INSERT INTO sys_role(role_code, role_name, status) VALUES(?, ?, 'ENABLED')",
                READ_ROLE_CODE, "数据源只读回归");
        long role = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE role_code = ?", Long.class, READ_ROLE_CODE);
        jdbcTemplate.update("INSERT INTO sys_role_permission(role_id, permission_id) "
                + "SELECT ?, id FROM sys_permission WHERE permission_code = 'agent:datasource:read'", role);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ?", adminUserId);
        jdbcTemplate.update("INSERT INTO sys_user_role(user_id, role_id) VALUES(?, ?)", adminUserId, role);
        MockHttpSession viewer = signIn(ADMIN_LOGIN, ADMIN_PASSWORD);

        assertThat(getJson(URL, viewer).getResponse().getStatus()).isEqualTo(200);
        assertThat(bodyOf(getJson("/api/me/menus", viewer))).contains("agent.datasources");
        assertForbidden(create(viewer, data));
        assertForbidden(draftCheck(viewer, draft("regr_ds_ro_table", List.of())));
        assertForbidden(checkSaved(viewer, "00000000-0000-0000-0000-000000000000"));
        assertForbidden(status(viewer, "00000000-0000-0000-0000-000000000000", "DISABLED", 0));
    }

    @Test
    @DisplayName("并发编辑只有一个版本生效")
    void concurrentEditsHaveExactlyOneWinner() throws Exception {
        MockHttpSession first = adminSession();
        MockHttpSession second = adminSession();
        Map<String, Object> data = input("REGR-DS-RACE", "regr_ds_ro_table");
        String id = idOf(create(first, data));
        Map<String, Object> update = input("REGR-DS-RACE", "regr_ds_ro_table");
        update.put("version", 0);
        update.put("password", "");
        CountDownLatch gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> {
                gate.await();
                return update(first, id, update).getResponse().getStatus();
            });
            var b = executor.submit(() -> {
                gate.await();
                return update(second, id, update).getResponse().getStatus();
            });
            gate.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
    }

    @Test
    @DisplayName("受控查询只允许启用数据源的视图白名单，歧义或多命中一律拒绝")
    void viewQueryEnforcesEnabledWhitelistAndFailsClosedOnAmbiguity() {
        assertThat(viewQuery.enabledSourceFor(PRIMARY_VIEW)).isEmpty();
        String first = insertEnabledSource("REGR-DS-QUERY-1", PRIMARY_VIEW);
        var source = viewQuery.enabledSourceFor(PRIMARY_VIEW).orElseThrow();
        assertThat(source.name()).isEqualTo("REGR-DS-QUERY-1");
        List<Map<String, Object>> rows = viewQuery.select(source.id(), PRIMARY_VIEW,
                "SELECT COUNT(*) AS count FROM regr_ds_view_alpha", List.of());
        assertThat(((Number) rows.getFirst().get("count")).longValue()).isPositive();

        assertThatThrownBy(() -> viewQuery.select(source.id(), SECONDARY_VIEW,
                "SELECT COUNT(*) AS count FROM regr_ds_view_beta", List.of()))
                .as("白名单外的视图必须拒绝")
                .isInstanceOf(ApiException.class);

        jdbcTemplate.update(
                "UPDATE ai_data_source SET status='DISABLED', version=version+1 WHERE id=?", first);
        assertThat(viewQuery.enabledSourceFor(PRIMARY_VIEW)).isEmpty();
        assertThatThrownBy(() -> viewQuery.select(first, PRIMARY_VIEW,
                "SELECT COUNT(*) AS count FROM regr_ds_view_alpha", List.of()))
                .isInstanceOf(ApiException.class);

        jdbcTemplate.update(
                "UPDATE ai_data_source SET status='ENABLED', version=version+1 WHERE id=?", first);
        insertEnabledSource("REGR-DS-QUERY-2", PRIMARY_VIEW);
        assertThat(viewQuery.enabledSourceFor(PRIMARY_VIEW))
                .as("同一视图被多个启用来源命中时必须 fail closed")
                .isEmpty();
    }

    /** 直接插入启用配置，避免每条歧义用例都跑一次真实网络检查；启停与检查本身由其它用例覆盖。 */
    private String insertEnabledSource(String name, String view) {
        String id = java.util.UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO ai_data_source(id,name,remark,db_type,host,port,database_name,username,password_cipher,status)
                VALUES(?,?,'视图查询回归','MYSQL','mysql',3306,'merine_rebuild_test','regr_ds_ro_table',?,'ENABLED')
                """, id, name, cipher.encrypt(DataSourceStore.secretAad(id), fixturePassword));
        jdbcTemplate.update(
                "INSERT INTO ai_data_source_view(data_source_id, view_name) VALUES(?,?)", id, view);
        return id;
    }

    @Test
    @DisplayName("表结构与注释按数据库规则落地，且没有物理外键")
    void schemaUsesCommentsWithoutPhysicalReferences() {
        for (String table : List.of("ai_data_source", "ai_data_source_view")) {
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = ? AND column_comment = ''
                    """, Integer.class, table))
                    .as("%s 的字段注释", table).isZero();
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.tables
                     WHERE table_schema = DATABASE() AND table_name = ? AND table_comment = ''
                    """, Integer.class, table))
                    .as("%s 的表注释", table).isZero();
            assertThat(jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.referential_constraints
                     WHERE constraint_schema = DATABASE() AND table_name = ?
                    """, Integer.class, table))
                    .as("%s 的物理外键", table).isZero();
        }
    }
}
