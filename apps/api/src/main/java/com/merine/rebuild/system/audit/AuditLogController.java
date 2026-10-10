package com.merine.rebuild.system.audit;

import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.common.PageResult;
import com.merine.rebuild.system.audit.dto.AuditLogView;
import com.merine.rebuild.system.audit.dto.AuditPurgeResult;
import com.merine.rebuild.system.audit.dto.AuditModuleOption;
import com.merine.rebuild.system.security.PermissionCodes;
import com.merine.rebuild.system.security.PermissionGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作审计：查询流水 + 触发保留期清理。
 *
 * 流水不可改：没有单行修改或删除接口。唯一的删除是保留期清理（`audit:purge`），
 * 按时间窗硬删过期行，清理本身也留一条流水。判权只看权限码（`system:audit:read`），
 * 可见范围是全量（决策记录见设计稿 §1）。
 */
@RestController
@RequestMapping("/api/system/audit-logs")
@Tag(name = "系统管理 · 审计日志", description = "只读流水：操作者、动作、对象、结果；不含业务内容")
public class AuditLogController {

    private final AuditLogService service;
    private final AuditRetentionService retention;
    private final PermissionGuard guard;

    AuditLogController(AuditLogService service, AuditRetentionService retention,
                       PermissionGuard guard) {
        this.service = service;
        this.retention = retention;
        this.guard = guard;
    }

    @GetMapping
    @Operation(summary = "查询审计流水（服务端筛选 + 分页，时间缺省最近 7 天）")
    public ApiResponse<PageResult<AuditLogView>> list(
            @RequestParam(defaultValue = "") @Size(max = 40) String from,
            @RequestParam(defaultValue = "") @Size(max = 40) String to,
            @RequestParam(defaultValue = "") @Size(max = 80) String actor,
            @RequestParam(defaultValue = "") @Size(max = 32) String module,
            @RequestParam(defaultValue = "") @Size(max = 64) String action,
            @RequestParam(defaultValue = "") @Size(max = 16) String result,
            @RequestParam(defaultValue = "") @Size(max = 32) String targetType,
            @RequestParam(defaultValue = "") @Size(max = 64) String targetId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize,
            Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.AUDIT_READ, "没有查看审计日志的权限");
        return ApiResponse.success(service.list(from, to, actor, module, action, result, targetType,
                targetId, page, pageSize), request);
    }

    @GetMapping("/modules")
    @Operation(summary = "审计模块筛选项（代码侧注册表）")
    public ApiResponse<List<AuditModuleOption>> modules(Authentication authentication,
                                                        HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.AUDIT_READ, "没有查看审计日志的权限");
        return ApiResponse.success(service.modules(), request);
    }

    @PostMapping("/purge")
    @Operation(summary = "立即清理超过保留期的流水（默认 30 天，删除不可恢复）")
    public ApiResponse<AuditPurgeResult> purge(Authentication authentication,
                                               HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.AUDIT_READ, "没有清理审计日志的权限");
        return ApiResponse.success(retention.purge(true), request);
    }
}
