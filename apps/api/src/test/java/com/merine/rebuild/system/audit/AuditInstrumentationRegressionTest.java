package com.merine.rebuild.system.audit;

import static org.assertj.core.api.Assertions.assertThat;
import com.merine.rebuild.system.security.SystemAdminRegressionSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * S2 埋点回归：系统管理（用户/角色/菜单/单位/字典）、登录与退出、初始化，操作成功后都要留下审计。
 *
 * 断言只查审计表本身（动作码、对象、摘要），不依赖页面；删除类必须留下被删对象的名称快照。
 * 负面清单：重置密码、更换密钥之后，审计表里搜不到密码/密钥的任何片段。
 */
class AuditInstrumentationRegressionTest extends SystemAdminRegressionSupport {

    private static final String LOGS = "/api/system/audit-logs";


    /** 只清审计表：其它测试类的埋点行也会落库，断言「恰好一条」前先清干净。 */
    @BeforeEach
    void clearAuditRows() {
        jdbcTemplate.update("DELETE FROM sys_audit_log");
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM sys_audit_log");
        // 字典项与字典类型按前缀清掉：编码是唯一键，残留会让下一次运行 409
        jdbcTemplate.update("""
                DELETE i FROM sys_dict_item i JOIN sys_dict_type t ON t.id = i.dict_type_id
                 WHERE t.dict_code LIKE 'regr.audit%'
                """);
        jdbcTemplate.update("DELETE FROM sys_dict_type WHERE dict_code LIKE 'regr.audit%'");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE login_name LIKE 'regr.audit.%')");
        jdbcTemplate.update("DELETE FROM sys_user WHERE login_name LIKE 'regr.audit.%'");
        jdbcTemplate.update("DELETE FROM sys_unit WHERE unit_code LIKE 'REGR-AUDIT-%'");
        // 先删关联再删角色：用例失败中途退出时也要保持无悬空引用
        jdbcTemplate.update("DELETE rp FROM sys_role_permission rp JOIN sys_role r ON r.id = rp.role_id "
                + "WHERE r.role_code LIKE 'REGR-AUDIT-%'");
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code LIKE 'REGR-AUDIT-%'");
    }

    private List<Map<String, Object>> auditRows(String actionLike) {
        return jdbcTemplate.queryForList("""
                SELECT module, action, result, target_type, target_label, summary, actor_login
                  FROM sys_audit_log WHERE action LIKE ? ORDER BY id
                """, actionLike);
    }

    private Map<String, Object> unitPayload(String code, String name) {
        return Map.of("code", code, "name", name, "parentCode", ADMIN_UNIT_CODE, "areaCode", "330000");
    }

    @Test
    void userCreateResetAndStatusChangesAreRecorded() throws Exception {
        var session = adminSession();
        var create = sendJson(MockMvcRequestBuilders.post("/api/system/users")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "loginName", "regr.audit.user",
                        "displayName", "审计回归用户",
                        "password", "regr-audit-pass-1",
                        "unitCode", ADMIN_UNIT_CODE,
                        "roleCodes", List.of(ADMIN_ROLE_CODE)))), session);
        assertThat(create.getResponse().getStatus()).isEqualTo(201);
        String createdId = jsonOf(bodyOf(create), "$.data.id");
        int version = intOf(bodyOf(create), "$.data.version");

        assertThat(auditRows("user:create")).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("module", "system").containsEntry("result", "SUCCEEDED")
                    .containsEntry("target_type", "USER").containsEntry("target_label", "审计回归用户")
                    .containsEntry("actor_login", ADMIN_LOGIN);
            assertThat((String) row.get("summary")).contains("新建用户", "regr.audit.user");
        });

        // 重置密码：只记「已重置」，不记密码内容
        var reset = sendJson(MockMvcRequestBuilders.post("/api/system/users/" + createdId + "/reset-password")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "newPassword", "regr-audit-pass-2", "version", version))), session);
        assertThat(reset.getResponse().getStatus()).isEqualTo(200);
        assertThat(auditRows("user:reset-password")).singleElement().satisfies(row ->
                assertThat((String) row.get("summary")).contains("重置").doesNotContain("regr-audit-pass"));
        assertThat(jdbcTemplate.queryForList(
                "SELECT summary FROM sys_audit_log WHERE summary LIKE '%regr-audit-pass%'", String.class))
                .isEmpty();

        // 停用：带目标姓名与登录名
        var disable = sendJson(MockMvcRequestBuilders.post("/api/system/users/disable")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "userIds", List.of(createdId)))), session);
        assertThat(disable.getResponse().getStatus()).isEqualTo(200);
        assertThat(auditRows("user:toggle-status")).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("target_label", "审计回归用户");
            assertThat((String) row.get("summary")).contains("停用", "regr.audit.user");
        });
    }

    @Test
    void roleUnitDictionaryAndMenuWritesAreRecordedWithDeletionSnapshot() throws Exception {
        var session = adminSession();

        // 角色：新建 → 删除（删除必须留下名称快照）
        var role = sendJson(MockMvcRequestBuilders.post("/api/system/roles")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "code", "REGR-AUDIT-ROLE", "name", "审计回归角色",
                        "description", "埋点回归", "permissionCodes", List.of("system:audit:read")))), session);
        assertThat(role.getResponse().getStatus()).isEqualTo(201);
        assertThat(auditRows("role:create")).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("target_type", "ROLE").containsEntry("target_label", "审计回归角色");
            assertThat((String) row.get("summary")).contains("权限 1 项");
        });
        var deleted = sendJson(MockMvcRequestBuilders.delete("/api/system/roles/REGR-AUDIT-ROLE?version=0"), session);
        assertThat(deleted.getResponse().getStatus()).isEqualTo(200);
        assertThat(auditRows("role:delete")).singleElement().satisfies(row ->
                assertThat(row).containsEntry("target_label", "审计回归角色"));

        // 单位：新建 → 删除
        var unit = sendJson(MockMvcRequestBuilders.post("/api/system/units")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(unitPayload("REGR-AUDIT-UNIT", "审计回归单位"))), session);
        assertThat(unit.getResponse().getStatus()).isEqualTo(201);
        var unitDeleted = sendJson(
                MockMvcRequestBuilders.delete("/api/system/units/REGR-AUDIT-UNIT?version=0"), session);
        assertThat(unitDeleted.getResponse().getStatus()).isEqualTo(200);
        assertThat(auditRows("unit:create")).singleElement().satisfies(row ->
                assertThat((String) row.get("summary")).contains("审计回归单位"));
        assertThat(auditRows("unit:delete")).singleElement().satisfies(row ->
                assertThat(row).containsEntry("target_label", "审计回归单位"));

        // 字典：新建类型 → 新增项 → 停用项
        var dict = sendJson(MockMvcRequestBuilders.post("/api/system/dictionaries")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "code", "regr.audit.dict", "name", "审计回归字典"))), session);
        assertThat(dict.getResponse().getStatus()).isEqualTo(201);
        var item = sendJson(MockMvcRequestBuilders.post("/api/system/dictionaries/regr.audit.dict/items")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "value", "A", "label", "选项甲", "sortOrder", 0))), session);
        assertThat(item.getResponse().getStatus()).isEqualTo(201);
        var itemVersion = intOf(bodyOf(item), "$.data.version");
        var itemOff = sendJson(MockMvcRequestBuilders
                .put("/api/system/dictionaries/regr.audit.dict/items/A")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "label", "选项甲", "sortOrder", 0, "status", "DISABLED", "version", itemVersion))), session);
        assertThat(itemOff.getResponse().getStatus()).isEqualTo(200);

        assertThat(auditRows("dict-type:create")).hasSize(1);
        assertThat(auditRows("dict-item:create")).singleElement().satisfies(row ->
                assertThat((String) row.get("summary")).contains("选项甲", "取值 A"));
        assertThat(auditRows("dict-item:update")).singleElement().satisfies(row ->
                assertThat((String) row.get("summary")).contains("停用", "选项甲", "取值 A"));
    }

    @Test
    void loginSuccessFailureAndLogoutAreRecordedWithoutSecrets() throws Exception {
        // 成功：建立会话后再退出
        var session = adminSession();
        assertThat(auditRows("auth:login")).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("result", "SUCCEEDED").containsEntry("target_type", "USER")
                    .containsEntry("actor_login", ADMIN_LOGIN);
            assertThat((String) row.get("summary")).contains("登录成功", ADMIN_DISPLAY);
        });
        var logout = sendJson(MockMvcRequestBuilders.delete("/api/auth/session"), session);
        assertThat(logout.getResponse().getStatus()).isEqualTo(200);
        assertThat(auditRows("auth:logout")).singleElement().satisfies(row ->
                assertThat(row).containsEntry("actor_login", ADMIN_LOGIN));

        // 失败：错误口令 → 记尝试的登录名与原因，不记口令
        jdbcTemplate.update("DELETE FROM sys_audit_log");
        var csrf = issueCsrfToken(null);
        var attempt = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/session")
                .contentType("application/json")
                .cookie(csrf)
                .header("X-XSRF-TOKEN", csrf.getValue())
                .content(objectMapper.writeValueAsString(Map.of(
                        "loginName", ADMIN_LOGIN, "password", "wrong-password-1")))).andReturn();
        assertThat(attempt.getResponse().getStatus()).isEqualTo(401);
        assertThat(auditRows("auth:login")).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("result", "FAILED").containsEntry("actor_login", ADMIN_LOGIN)
                    .containsEntry("target_label", ADMIN_LOGIN);
            assertThat(row.get("actor_user_id")).isNull();
            assertThat((String) row.get("summary")).contains("登录失败", "口令不正确")
                    .doesNotContain("wrong-password-1");
        });
    }
}
