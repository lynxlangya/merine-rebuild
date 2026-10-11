package com.merine.rebuild.task;

import com.merine.rebuild.auth.AuthenticatedAccount;
import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.system.security.PermissionCodes;
import com.merine.rebuild.system.security.PermissionGuard;
import com.merine.rebuild.system.user.account.UserAccountLookup;
import com.merine.rebuild.system.user.account.UserAccountState;
import com.merine.rebuild.task.persistence.TaskMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 对 Agent 公开的只读统计：身份由调用方会话提供，范围与计算由任务模块执行。 */
@Service
public class TaskStatisticsLookup {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final TaskMapper mapper;
    private final PermissionGuard guard;
    private final UserAccountLookup accounts;
    private final Clock clock;

    TaskStatisticsLookup(TaskMapper mapper, PermissionGuard guard, UserAccountLookup accounts, Clock clock) {
        this.mapper = mapper;
        this.guard = guard;
        this.accounts = accounts;
        this.clock = clock;
    }

    public enum Period { THIS_MONTH, LAST_MONTH, ROLLING_MONTH }

    public record TimeRange(Instant startAt, Instant endAtExclusive) {}

    /** queriedAt 是查询时间，不伪称数据最近更新时间；不暴露持久化投射或任务正文。 */
    public record CompletedCount(String queryId, long count, String unitCode, String unitName,
                                 TimeRange period, Instant queriedAt) {}

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED, timeout = 5)
    public CompletedCount completedCount(Authentication authentication, Period period) {
        guard.require(authentication, PermissionCodes.TASK_READ, "没有查看任务的权限");
        if (!(authentication.getPrincipal() instanceof AuthenticatedAccount account)) {
            throw expired();
        }
        requireCurrent(account);
        if (period == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATISTICS_PERIOD", "请选择本月、上月或最近一个月");
        }
        var unit = mapper.actorUnit(account.userId());
        if (unit == null || !"ENABLED".equals(unit.status())) throw expired();
        // 查询工具内部必读服务器时间，按 MySQL DATETIME(6) 精度冻结截止点；模型与客户端不能提交时间。
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        TimeRange range = timeRange(period, now);
        long count = mapper.completedIssuedCount(unit.id(), range.startAt(), range.endAtExclusive());
        // 异步模型调用期间发生停用/换角色/换单位，不能继续使用启动时的授权快照。
        requireCurrent(account);
        return new CompletedCount(UUID.randomUUID().toString(), count, unit.code(), unit.name(), range, now);
    }

    static TimeRange timeRange(Period period, Instant now) {
        var localNow = now.atZone(ZONE);
        var monthStart = localNow.toLocalDate().withDayOfMonth(1).atStartOfDay(ZONE);
        return switch (period) {
            case THIS_MONTH -> new TimeRange(monthStart.toInstant(), now);
            case LAST_MONTH -> new TimeRange(monthStart.minusMonths(1).toInstant(), monthStart.toInstant());
            // 日历月倒推保留时分秒；上月没有同一天时，由日期库落在上月最后一天。
            case ROLLING_MONTH -> new TimeRange(localNow.minusMonths(1).toInstant(), now);
        };
    }

    private void requireCurrent(AuthenticatedAccount account) {
        UserAccountState state = accounts.findStateById(account.userId());
        if (state == null || !state.enabled() || state.authorizationVersion() != account.authorizationVersion()) {
            throw expired();
        }
    }

    private static ApiException expired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "登录身份已失效，请重新登录后查询");
    }
}
