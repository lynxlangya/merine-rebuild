package com.merine.rebuild.system.audit;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.merine.rebuild.system.security.SystemAdminRegressionSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 审计保留期（默认 30 天）清理的隔离回归。
 *
 * 守住三条不变量：只有早于窗口的记录被删（窗口内的一条都不动）、删除分区按批执行、
 * 清理本身留一条 `audit:purge` 流水。真删到东西才有记录，重复清理不产生噪音。
 */
class AuditRetentionRegressionTest extends SystemAdminRegressionSupport {

    private static final String PURGE = "/api/system/audit-logs/purge";

    @Autowired AuditRetentionService retention;

    @BeforeEach
    void clearAuditRows() {
        jdbcTemplate.update("DELETE FROM sys_audit_log");
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM sys_audit_log");
    }

    /** 直接插过期/未过期流水：留痕的写入路径已经有自己的用例，这里要的是可控的发生时刻。 */
    private long insertRow(String action, Instant occurredAt) {
        jdbcTemplate.update("""
                INSERT INTO sys_audit_log(occurred_at, actor_login, actor_name, actor_unit_name,
                                          module, action, result, target_type, target_id,
                                          target_label, summary, request_id, client_ip)
                VALUES(?,?,?,?,?,?,'SUCCEEDED','USER','1','对象名','回归流水','req-1','127.0.0.1')
                """, java.sql.Timestamp.from(occurredAt), ADMIN_LOGIN, ADMIN_DISPLAY,
                ADMIN_UNIT_NAME, "system", action);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM sys_audit_log", Long.class);
    }

    private long countRows(String action) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_audit_log WHERE action = ?", Long.class, action);
    }

    @Test
    void purgeDeletesOnlyRowsOlderThanTheRetentionWindowAndLeavesATrail() throws Exception {
        Instant now = Instant.now();
        long expired = insertRow("user:create", now.minus(31, ChronoUnit.DAYS));
        long insideBoundary = insertRow("user:update", now.minus(29, ChronoUnit.DAYS));
        long fresh = insertRow("user:delete", now.minus(1, ChronoUnit.HOURS));

        MvcResult purged = sendJson(post(PURGE), adminSession());
        assertThat(purged.getResponse().getStatus()).isEqualTo(200);
        assertThat(intOf(bodyOf(purged), "$.data.retentionDays")).isEqualTo(30);
        assertThat(intOf(bodyOf(purged), "$.data.deleted")).isEqualTo(1);

        // 窗口内的一条都不能少：这是保留期唯一的硬边界
        assertThat(jdbcTemplate.queryForList("SELECT id FROM sys_audit_log WHERE action IN ('user:update','user:delete')",
                Long.class)).containsExactlyInAnyOrder(insideBoundary, fresh);
        assertThat(countRows("user:create")).isZero();
        assertThat(jdbcTemplate.queryForList("SELECT id FROM sys_audit_log WHERE id = ?", Long.class, expired)).isEmpty();

        // 清理自己留痕：谁触发、删了多少、覆盖哪段时间
        var trail = jdbcTemplate.queryForList("""
                SELECT actor_login, target_label, summary FROM sys_audit_log WHERE action = 'audit:purge'
                """);
        assertThat(trail).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("actor_login", ADMIN_LOGIN);
            // 覆盖区间也要写出来：MIN/MAX 的映射类型换过一次，文案里绝不能出现「未知」
            assertThat((String) row.get("summary")).contains("30 天前").contains("手动触发").contains("删除 1 条")
                    .contains("覆盖 20").doesNotContain("未知");
        });

        // 再清一次：没有过期记录，不删除也不产生新的清理记录
        assertThat(intOf(bodyOf(sendJson(post(PURGE), adminSession())), "$.data.deleted")).isZero();
        assertThat(countRows("audit:purge")).isEqualTo(1);
    }

    @Test
    void schedulerTriggerIsRecordedWithoutOperator() {
        insertRow("user:create", Instant.now().minus(40, ChronoUnit.DAYS));
        var result = retention.purge(false);

        assertThat(result.deleted()).isEqualTo(1);
        var trail = jdbcTemplate.queryForList("""
                SELECT actor_login, actor_user_id, summary FROM sys_audit_log WHERE action = 'audit:purge'
                """);
        assertThat(trail).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("actor_login", "").containsEntry("actor_user_id", null);
            assertThat((String) row.get("summary")).contains("定时触发");
        });
    }

    @Test
    void retentionWindowMustBePositive() {
        assertThatThrownBy(() -> new AuditRetentionProperties(0, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少 1 天");
    }

    @Test
    void purgeRequiresAuditPermission() throws Exception {
        assertUnauthenticatedJson(sendJson(post(PURGE), null));
        jdbcTemplate.update("""
                INSERT INTO sys_role(role_code, role_name, status) VALUES('REGR-AUDIT-PURGE-NONE','无清理权限回归','ENABLED')
                ON DUPLICATE KEY UPDATE role_name = VALUES(role_name)
                """);
        long role = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE role_code='REGR-AUDIT-PURGE-NONE'", Long.class);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id=?", adminUserId);
        jdbcTemplate.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", adminUserId, role);
        MockHttpSession denied = signIn(ADMIN_LOGIN, ADMIN_PASSWORD);
        assertForbidden(sendJson(post(PURGE), denied));
        assertThat(countRows("audit:purge")).isZero();
    }
}
