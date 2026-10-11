package com.merine.rebuild.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.merine.rebuild.auth.AuthenticatedAccount;
import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.support.MockMvcRegressionSupport;
import com.merine.rebuild.system.security.PermissionCodes;
import com.merine.rebuild.task.TaskStatisticsLookup.Period;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;

/** 合成 fixture 在专用真实 MySQL 中回滚，防止错误范围、月界和授权被统计为零。 */
@Transactional
@Import(TaskStatisticsRegressionTest.FixedTime.class)
class TaskStatisticsRegressionTest extends MockMvcRegressionSupport {
    private static final Instant NOW = Instant.parse("2026-10-10T04:34:56.123456Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedTime {
        @Bean @Primary
        Clock fixedStatisticsClock() { return Clock.fixed(NOW.plusNanos(321), ZoneOffset.UTC); }
    }

    @Autowired TaskStatisticsLookup statistics;
    private long unitId;
    private long otherUnitId;
    private long userId;
    private Authentication authentication;

    @BeforeEach
    void fixture() {
        unitId = unit();
        otherUnitId = unit();
        String login = "stats-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("INSERT INTO sys_user(login_name,display_name,password_hash,unit_id) VALUES(?,?,?,?)",
                login, "合成统计账号", passwordEncoder.encode("synthetic-statistics-secret"), unitId);
        userId = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE login_name=?", Long.class, login);
        authentication = authentication(List.of(PermissionCodes.TASK_READ));
    }

    private long unit() {
        String code = "REGR-STATS-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("INSERT INTO sys_unit(unit_code,unit_name,status) VALUES(?,?,'ENABLED')", code, code);
        return jdbcTemplate.queryForObject("SELECT id FROM sys_unit WHERE unit_code=?", Long.class, code);
    }

    private Authentication authentication(List<String> permissions) {
        int version = jdbcTemplate.queryForObject("SELECT authorization_version FROM sys_user WHERE id=?", Integer.class, userId);
        var account = new AuthenticatedAccount(userId, "合成账号", "合成统计账号", "合成单位",
                List.of("SYSTEM_ADMIN"), List.of("合成角色"), permissions, version);
        return UsernamePasswordAuthenticationToken.authenticated(account, null,
                permissions.stream().map(SimpleGrantedAuthority::new).toList());
    }

    private long task(long issuer, String status, Instant completedAt) {
        String taskNo = "REGR-STATS-" + UUID.randomUUID().toString().substring(0, 8);
        Timestamp due = Timestamp.from(Instant.now().plusSeconds(86400));
        jdbcTemplate.update("""
                INSERT INTO task_order(task_no,issuer_unit_id,issuer_unit_name_snapshot,issuer_user_id,
                    title,instruction,expected_result,initial_due_at,current_due_at,status,completed_at)
                VALUES(?,?,?,?,'合成统计任务','合成要求','合成目标',?,?,?,?)
                """, taskNo, issuer, "合成单位", userId, due, due, status,
                completedAt == null ? null : Timestamp.from(completedAt));
        return jdbcTemplate.queryForObject("SELECT id FROM task_order WHERE task_no=?", Long.class, taskNo);
    }

    @Test
    void countsWholeIssuedTasksWithinCompletionPeriodAndDoesNotExpandAdminScope() {
        var range = TaskStatisticsLookup.timeRange(Period.THIS_MONTH, NOW);
        long whole = task(unitId, "COMPLETED", range.startAt());
        task(unitId, "COMPLETED", range.endAtExclusive().minusNanos(1000));
        task(unitId, "COMPLETED", range.startAt().minusNanos(1000));
        task(unitId, "COMPLETED", range.endAtExclusive());
        task(unitId, "COMPLETED", NOW.plusSeconds(1));
        task(unitId, "COMPLETED", Instant.parse("2026-10-20T00:00:00Z"));
        task(unitId, "AWAITING_CLOSE", null);
        task(unitId, "OPEN", null);
        long undertaken = task(otherUnitId, "COMPLETED", range.startAt());
        jdbcTemplate.update("""
                INSERT INTO task_branch(task_id,origin_from_unit_id,origin_to_unit_id,
                    instruction_snapshot,expected_result_snapshot,status,completed_at)
                VALUES(?,?,?,'合成要求','合成目标','COMPLETED',?)
                """, undertaken, otherUnitId, unitId, Timestamp.from(range.startAt()));
        long branch = jdbcTemplate.queryForObject("SELECT id FROM task_branch WHERE task_id=?", Long.class, undertaken);
        jdbcTemplate.update("""
                INSERT INTO task_assignment(branch_id,from_unit_id,from_unit_name_snapshot,to_unit_id,
                    to_unit_name_snapshot,source_action,due_at,status)
                VALUES(?,?,'其他合成单位',?,'本单位','DOWNWARD',?,'COMPLETED')
                """, branch, otherUnitId, unitId, Timestamp.from(range.startAt().plusSeconds(86400)));
        for (int index = 0; index < 3; index++) {
            jdbcTemplate.update("""
                    INSERT INTO task_branch(task_id,origin_from_unit_id,origin_to_unit_id,
                        instruction_snapshot,expected_result_snapshot,status,completed_at)
                    VALUES(?,?,?,'合成要求','合成目标','COMPLETED',?)
                    """, whole, unitId, otherUnitId, Timestamp.from(range.startAt()));
        }
        var result = statistics.completedCount(authentication, Period.THIS_MONTH);
        assertThat(result.count()).isEqualTo(2);
        assertThat(result.period()).isEqualTo(range);
        assertThat(result.queriedAt()).isEqualTo(NOW);
        assertThat(result.period().endAtExclusive()).isEqualTo(result.queriedAt());
        assertThat(result.queryId()).isNotBlank();
    }

    @Test
    void zeroIsASuccessAndPreviousMonthUsesCompletionTime() {
        var range = TaskStatisticsLookup.timeRange(Period.LAST_MONTH, NOW);
        task(unitId, "COMPLETED", range.startAt().plusSeconds(1));
        assertThat(statistics.completedCount(authentication, Period.THIS_MONTH).count()).isZero();
        assertThat(statistics.completedCount(authentication, Period.LAST_MONTH).count()).isEqualTo(1);
    }

    @Test
    void rollingMonthUsesBothExactTimeBoundariesInsteadOfCalendarMonths() {
        var start = Instant.parse("2026-09-10T04:34:56.123456Z");
        task(unitId, "COMPLETED", start);
        task(unitId, "COMPLETED", NOW.minusNanos(1000));
        task(unitId, "COMPLETED", start.minusNanos(1000));
        task(unitId, "COMPLETED", NOW);
        task(unitId, "COMPLETED", NOW.plusSeconds(1));
        task(otherUnitId, "COMPLETED", start);
        task(unitId, "AWAITING_CLOSE", null);
        var result = statistics.completedCount(authentication, Period.ROLLING_MONTH);
        assertThat(result.count()).isEqualTo(2);
        assertThat(result.period().startAt()).isEqualTo(start);
        assertThat(result.period().endAtExclusive()).isEqualTo(NOW);
        assertThat(result.queriedAt()).isEqualTo(NOW);
    }

    @Test
    void missingPermissionIsRejectedInsteadOfCountedAsZero() {
        assertThatThrownBy(() -> statistics.completedCount(authentication(List.of()), Period.THIS_MONTH))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo("FORBIDDEN"));
    }

    @Test
    void changedAuthorizationCannotBeUsedByAnAlreadyRunningChat() {
        jdbcTemplate.update("UPDATE sys_user SET authorization_version=authorization_version+1 WHERE id=?", userId);
        assertThatThrownBy(() -> statistics.completedCount(authentication, Period.THIS_MONTH))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo("UNAUTHENTICATED"));
    }

    @Test
    void disabledUnitCannotBeQueried() {
        jdbcTemplate.update("UPDATE sys_unit SET status='DISABLED' WHERE id=?", unitId);
        assertThatThrownBy(() -> statistics.completedCount(authentication, Period.THIS_MONTH))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.code()).isEqualTo("UNAUTHENTICATED"));
    }
}
