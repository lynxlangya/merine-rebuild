package com.merine.rebuild.system.audit;

import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.common.PageResult;
import com.merine.rebuild.system.audit.dto.AuditLogView;
import com.merine.rebuild.system.audit.dto.AuditModuleOption;
import com.merine.rebuild.system.audit.persistence.AuditLogMapper;
import com.merine.rebuild.system.audit.persistence.AuditLogRow;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审计流水查询：只读、服务端筛选 + 分页。
 *
 * 时间范围缺省为最近 7 天（审计表只会增长，默认全量查询没有意义）；`to` 缺省为现在。
 * 结果与模块取值都做白名单校验，非法值直接 400，避免拼接出意外查询。
 */
@Service
public class AuditLogService {

    private static final Set<String> RESULTS = Set.of("SUCCEEDED", "FAILED");
    private static final int MAX_PAGE_SIZE = 100;
    /** 时间范围上限：一次最多查 90 天，避免误操作把整表拉出来。 */
    private static final Duration MAX_RANGE = Duration.ofDays(90);

    private final AuditLogMapper mapper;

    AuditLogService(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResult<AuditLogView> list(String from, String to, String actor, String module,
                                         String action, String result, String targetType,
                                         String targetId, int page, int pageSize) {
        if (pageSize > MAX_PAGE_SIZE) {
            throw invalid("pageSize", "每页条数不能超过 " + MAX_PAGE_SIZE);
        }
        if (result != null && !result.isBlank() && !RESULTS.contains(result)) {
            throw invalid("result", "结果取值不在允许范围内");
        }
        if (module != null && !module.isBlank() && !AuditModules.isRegistered(module)) {
            throw invalid("module", "模块取值不在允许范围内");
        }
        Instant resolvedTo = parse(to, Instant.now());
        Instant resolvedFrom = parse(from, resolvedTo.minus(Duration.ofDays(7)));
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw invalid("from", "起始时间不能晚于结束时间");
        }
        if (Duration.between(resolvedFrom, resolvedTo).compareTo(MAX_RANGE) > 0) {
            throw invalid("from", "时间范围不能超过 " + MAX_RANGE.toDays() + " 天");
        }
        List<AuditLogView> items = mapper
                .list(resolvedFrom, resolvedTo, blankToNull(actor), blankToNull(module),
                        blankToNull(action), blankToNull(result), blankToNull(targetType),
                        blankToNull(targetId), pageSize, (long) (page - 1) * pageSize)
                .stream()
                .map(AuditLogService::toView)
                .toList();
        long total = mapper.count(resolvedFrom, resolvedTo, blankToNull(actor), blankToNull(module),
                blankToNull(action), blankToNull(result), blankToNull(targetType),
                blankToNull(targetId));
        return new PageResult<>(items, total, page, pageSize);
    }

    /** 模块筛选项：来自代码侧注册表，不走进字典（它是结构性清单，不是业务枚举）。 */
    @Transactional(readOnly = true)
    public List<AuditModuleOption> modules() {
        return AuditModules.all().stream()
                .map(module -> new AuditModuleOption(module.code(), module.name()))
                .toList();
    }

    private static AuditLogView toView(AuditLogRow row) {
        return new AuditLogView(Long.toString(row.id()), row.occurredAt(), row.actorLogin(),
                row.actorName(), row.actorUnitName(), row.module(), AuditModules.nameOf(row.module()),
                row.action(), row.result(), row.targetType(), row.targetId(), row.targetLabel(),
                row.summary(), row.requestId(), row.clientIp());
    }

    private static Instant parse(String value, Instant fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return Instant.parse(value.trim());
        } catch (RuntimeException error) {
            throw invalid("from", "时间需为 ISO-8601 的 UTC 时刻，例如 2026-10-10T00:00:00Z");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message,
                List.of(new com.merine.rebuild.common.ApiResponse.FieldError(field, message)));
    }
}
