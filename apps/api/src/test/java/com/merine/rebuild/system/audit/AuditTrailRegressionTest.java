package com.merine.rebuild.system.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.merine.rebuild.auth.AuthenticatedAccount;
import com.merine.rebuild.system.security.SystemAdminRegressionSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

/**
 * 审计写入封装与查询接口的隔离回归（S1：基础设施，不含各模块埋点）。
 *
 * 覆盖：写入后的行内容（操作者快照、对象、摘要、请求 id、IP）、登录失败的无身份写法、
 * 负面清单（敏感串绝不入表）、列表筛选与分页、非法参数与无权限 403。
 */
class AuditTrailRegressionTest extends SystemAdminRegressionSupport {

    private static final String PATH = "/api/system/audit-logs";

    @Autowired AuditTrail trail;

    /** 只清审计表：其它测试类的埋点行也会落库，断言「恰好一条」前先清干净。 */
    @BeforeEach
    void clearAuditRows() {
        jdbcTemplate.update("DELETE FROM sys_audit_log");
    }

    /**
     * 只在用例结束后清理：父类夹具在每个用例开始时重建管理员与角色，
     * 如果在 @BeforeEach 里清授权关系，会把刚建好的管理员角色删掉，会话就没有权限了。
     */
    @AfterEach
    void cleanupAuditRows() {
        jdbcTemplate.update("DELETE FROM sys_audit_log");
        // 权限用例会临时建角色；按登录名联表清掉，避免残留悬空引用与重复键
        jdbcTemplate.update("""
                DELETE ur FROM sys_user_role ur JOIN sys_user u ON u.id = ur.user_id
                 WHERE u.login_name = ?
                """, ADMIN_LOGIN);
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code = 'REGR-AUDIT-NONE'");
    }

    private AuthenticatedAccount actor() {
        return new AuthenticatedAccount(adminUserId, ADMIN_LOGIN, ADMIN_DISPLAY, ADMIN_UNIT_NAME,
                List.of(ADMIN_ROLE_CODE), List.of("回归系统管理员角色"),
                List.of("system:audit:read"), 0);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.7, 10.0.0.1");
        request.setAttribute("requestId", "req-1");
        return request;
    }

    @Test
    void writesActorSnapshotTargetSummaryAndRequestContext() {
        trail.record(actor(), AuditEvent.succeeded("system", "user:toggle-status", "USER", "42",
                "张三", "停用用户「张三」（demo.zhang）"), request());

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT actor_user_id, actor_login, actor_name, actor_unit_name, module, action, result,
                       target_type, target_id, target_label, summary, request_id, client_ip
                  FROM sys_audit_log WHERE action = 'user:toggle-status'
                """);
        assertThat(row).containsEntry("actor_user_id", adminUserId)
                .containsEntry("actor_login", ADMIN_LOGIN)
                .containsEntry("actor_name", ADMIN_DISPLAY)
                .containsEntry("actor_unit_name", ADMIN_UNIT_NAME)
                .containsEntry("module", "system")
                .containsEntry("action", "user:toggle-status")
                .containsEntry("result", "SUCCEEDED")
                .containsEntry("target_type", "USER")
                .containsEntry("target_id", "42")
                .containsEntry("target_label", "张三")
                .containsEntry("request_id", "req-1")
                .as("反代后取 X-Forwarded-For 的第一段").containsEntry("client_ip", "203.0.113.7");
        assertThat((String) row.get("summary")).contains("停用用户");
    }

    @Test
    void anonymousFailureKeepsAttemptedLoginAndLeavesActorEmpty() {
        trail.recordAnonymous("demo.zhang",
                AuditEvent.failed("auth", "auth:login", "USER", "", "demo.zhang",
                        "登录失败：账号或口令不正确"), request());

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT actor_user_id, actor_login, actor_name, result, summary
                  FROM sys_audit_log WHERE result = 'FAILED'
                """);
        assertThat(row.get("actor_user_id")).isNull();
        assertThat(row).containsEntry("actor_login", "demo.zhang").containsEntry("actor_name", "")
                .containsEntry("result", "FAILED");
        // 摘要就是传入的那句话（「口令不正确」是提示，不是凭据）；封装不会额外拼接任何内容
        assertThat((String) row.get("summary")).isEqualTo("登录失败：账号或口令不正确");
    }

    @Test
    void sensitiveValuesNeverReachTheAuditTable() {
        trail.record(actor(), AuditEvent.succeeded("agent", "provider:rotate-key", "PROVIDER", "p-1",
                "DeepSeek", "更换连接密钥（不记录密钥内容）"), request());
        List<String> hits = jdbcTemplate.queryForList("""
                SELECT summary FROM sys_audit_log
                 WHERE summary LIKE '%sk-%' OR summary LIKE '%$2a$%' OR summary LIKE '%BEGIN %'
                """, String.class);
        assertThat(hits).isEmpty();
    }

    @Test
    void listFiltersByModuleActionResultAndActor() throws Exception {
        trail.record(actor(),
                AuditEvent.succeeded("system", "user:create", "USER", "1", "甲", "新建用户「甲」"),
                request());
        trail.record(actor(),
                AuditEvent.succeeded("agent", "provider:update", "PROVIDER", "p-1", "DeepSeek",
                        "修改连接「DeepSeek」"), request());
        trail.recordAnonymous("demo.zhang",
                AuditEvent.failed("auth", "auth:login", "USER", "", "demo.zhang", "登录失败"), request());

        MockHttpSession session = adminSession();
        // 登录本身也会留一条（auth:login），所以这里只断言「不少于自己写的三条」
        assertThat(intOf(bodyOf(getJson(PATH, session)), "$.data.total")).isGreaterThanOrEqualTo(3);
        assertThat(intOf(bodyOf(getJson(PATH + "?module=system", session)), "$.data.total")).isEqualTo(1);
        assertThat(intOf(bodyOf(getJson(PATH + "?result=FAILED", session)), "$.data.total")).isEqualTo(1);
        assertThat(intOf(bodyOf(getJson(PATH + "?action=provider:update", session)), "$.data.total"))
                .isEqualTo(1);
        assertThat(intOf(bodyOf(getJson(PATH + "?actor=demo.zhang", session)), "$.data.total"))
                .isEqualTo(1);
        assertThat(intOf(bodyOf(getJson(PATH + "?targetType=PROVIDER&targetId=p-1", session)),
                "$.data.total")).isEqualTo(1);
        // 模块名来自代码侧注册表，响应里带中文名而不是原始码
        assertThat(bodyOf(getJson(PATH + "?module=agent", session))).contains("模型连接");
        // 时间范围缺省近 7 天；显式给一个过去的窗口应当查不到
        assertThat(intOf(bodyOf(getJson(PATH + "?from=2020-01-01T00:00:00Z&to=2020-01-02T00:00:00Z",
                session)), "$.data.total")).isZero();

        assertError(getJson(PATH + "?module=unknown", session), 400, "VALIDATION_ERROR");
        assertError(getJson(PATH + "?result=SUCCESS", session), 400, "VALIDATION_ERROR");
        assertError(getJson(PATH + "?pageSize=500", session), 400, "VALIDATION_ERROR");
        assertError(getJson(PATH + "?from=2020-01-01T00:00:00Z", session), 400, "VALIDATION_ERROR");
        assertError(getJson(PATH + "?actor=a&from=not-a-time", session), 400, "VALIDATION_ERROR");
    }

    @Test
    void modulesEndpointComesFromCodeRegistry() throws Exception {
        var result = getJson(PATH + "/modules", adminSession());
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(bodyOf(result)).contains("系统管理", "涉海要素", "模型连接", "登录与退出", "初始化")
                .doesNotContain("任务处置", "信息流转");
    }

    @Test
    void readRequiresAuditPermission() throws Exception {
        assertUnauthenticatedJson(getJson(PATH, null));
        jdbcTemplate.update("""
                INSERT INTO sys_role(role_code, role_name, status) VALUES('REGR-AUDIT-NONE','无审计权限回归','ENABLED')
                ON DUPLICATE KEY UPDATE role_name = VALUES(role_name)
                """);
        long role = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE role_code='REGR-AUDIT-NONE'", Long.class);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id=?", adminUserId);
        jdbcTemplate.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", adminUserId, role);
        var denied = signIn(ADMIN_LOGIN, ADMIN_PASSWORD);
        assertForbidden(getJson(PATH, denied));
        assertForbidden(getJson(PATH + "/modules", denied));
    }
}
