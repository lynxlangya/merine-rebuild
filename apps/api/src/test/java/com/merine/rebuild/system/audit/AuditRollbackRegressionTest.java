package com.merine.rebuild.system.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.merine.rebuild.system.security.SystemAdminRegressionSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 审计写失败必须让业务一起回滚——审计与业务写入在同一个事务里，失败时不允许出现「改了但没记住」。
 *
 * 为什么用替身制造失败：运行账号只持有 DML 权限（有意的约束），测试里无法改表结构注入 CHECK 约束来
 * 让审计写失败，因此替换「审计写入」这一个协作者让它抛异常。被断言的对象仍是隔离 MySQL 里的业务行，
 * 事务语义没有被替身掩盖：接口 500、用户状态不变；撤掉注入后同一次操作可以正常完成。
 */
class AuditRollbackRegressionTest extends SystemAdminRegressionSupport {

    @MockitoBean AuditTrail auditTrail;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM sys_audit_log");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE login_name LIKE 'regr.audit.%')");
        jdbcTemplate.update("DELETE FROM sys_user WHERE login_name LIKE 'regr.audit.%'");
    }

    @Test
    void failedAuditWriteRollsBackTheBusinessChange() throws Exception {
        var session = adminSession();
        var create = sendJson(MockMvcRequestBuilders.post("/api/system/users")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of(
                        "loginName", "regr.audit.rollback",
                        "displayName", "回滚验证用户",
                        "password", "regr-audit-pass-1",
                        "unitCode", ADMIN_UNIT_CODE,
                        "roleCodes", List.of(ADMIN_ROLE_CODE)))), session);
        assertThat(create.getResponse().getStatus()).as(bodyOf(create)).isEqualTo(201);
        String createdId = jsonOf(bodyOf(create), "$.data.id");

        Mockito.doThrow(new IllegalStateException("审计写入失败")).when(auditTrail)
                .recordCurrent(ArgumentMatchers.any());
        var disable = sendJson(MockMvcRequestBuilders.post("/api/system/users/disable")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("userIds", List.of(createdId)))),
                session);
        assertThat(disable.getResponse().getStatus()).isEqualTo(500);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM sys_user WHERE login_name = ?",
                String.class, "regr.audit.rollback")).isEqualTo("ENABLED");

        // 撤掉注入后同样的操作能正常完成，说明上面失败的是审计写入而不是别的前置条件
        Mockito.reset(auditTrail);
        var retry = sendJson(MockMvcRequestBuilders.post("/api/system/users/disable")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("userIds", List.of(createdId)))),
                session);
        assertThat(retry.getResponse().getStatus()).as(bodyOf(retry)).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM sys_user WHERE login_name = ?",
                String.class, "regr.audit.rollback")).isEqualTo("DISABLED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_audit_log "
                + "WHERE action = 'user:toggle-status'", Integer.class)).isZero();
    }
}
