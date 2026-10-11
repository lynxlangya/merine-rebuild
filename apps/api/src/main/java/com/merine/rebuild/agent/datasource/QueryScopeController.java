package com.merine.rebuild.agent.datasource;

import com.merine.rebuild.agent.datasource.dto.DataSourceCheckResult;
import com.merine.rebuild.agent.datasource.dto.DataSourceView;
import com.merine.rebuild.agent.datasource.dto.QueryScopeDetail;
import com.merine.rebuild.agent.datasource.dto.QueryScopeRequests.ReplaceViews;
import com.merine.rebuild.agent.datasource.dto.QueryScopeRequests.SaveMetadata;
import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.common.PageResult;
import com.merine.rebuild.system.security.PermissionCodes;
import com.merine.rebuild.system.security.PermissionGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 查询范围管理：允许查询的视图白名单，以及视图/字段说明覆盖值。
 *
 * <p>查看列表只读本项目配置库；探测候选视图与字段、保存新增字段说明会连接目标库，
 * 因此除 {@code agent:scope:update} 外还需要 {@code agent:datasource:test}；
 * 纯移除视图或只编辑已有说明不需要网络权限。
 */
@RestController
@RequestMapping("/api/agent/query-scopes")
@Tag(name = "查询范围管理", description = "维护允许查询的视图范围与视图/字段说明；列表不连接目标库")
public class QueryScopeController {
    private final QueryScopeService service;
    private final PermissionGuard guard;

    public QueryScopeController(QueryScopeService service, PermissionGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    public ApiResponse<PageResult<DataSourceView>> list(
            @RequestParam(defaultValue = "") @Size(max = 80) String search,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(60) int pageSize,
            Authentication auth, HttpServletRequest request) {
        guard.require(auth, PermissionCodes.SCOPE_READ, "没有查看查询范围的权限");
        return ApiResponse.success(service.list(search, page, pageSize), request);
    }

    /** 详情只返回已保存的范围与说明；视图/字段是否存在由探测接口判断。 */
    @GetMapping("/{id}")
    public ApiResponse<QueryScopeDetail> detail(@PathVariable String id,
                                                Authentication auth, HttpServletRequest request) {
        guard.require(auth, PermissionCodes.SCOPE_READ, "没有查看查询范围的权限");
        return ApiResponse.success(service.find(id), request);
    }

    /** 刷新可读视图；带 view 时同时读取该视图字段名/类型/可空性/列注释。 */
    @PostMapping("/{id}/inspect")
    public ApiResponse<DataSourceCheckResult> inspect(
            @PathVariable String id,
            @RequestParam(required = false) @Size(max = 64) String view,
            Authentication auth, HttpServletRequest request) {
        guard.require(auth, PermissionCodes.SCOPE_READ, "没有查看查询范围的权限");
        guard.require(auth, PermissionCodes.DATASOURCE_TEST, "没有测试数据源连接的权限");
        return ApiResponse.success(service.inspect(id, view), request);
    }

    /** 整体替换允许查询的视图；新增候选会先复检可读性，保存后清除检查结果并停用数据源。 */
    @PutMapping("/{id}/views")
    public ApiResponse<DataSourceView> replaceViews(@PathVariable String id,
                                                    @Valid @RequestBody ReplaceViews input,
                                                    Authentication auth, HttpServletRequest request) {
        guard.require(auth, PermissionCodes.SCOPE_UPDATE, "没有维护查询范围的权限");
        return ApiResponse.success(service.replaceViews(id, input, guard.has(auth, PermissionCodes.DATASOURCE_TEST)),
                request);
    }

    /** 指定视图的说明覆盖段整体替换；新增字段说明前会实时核对目标库字段。 */
    @PutMapping("/{id}/metadata")
    public ApiResponse<QueryScopeDetail> saveMetadata(@PathVariable String id,
                                                      @Valid @RequestBody SaveMetadata input,
                                                      Authentication auth, HttpServletRequest request) {
        guard.require(auth, PermissionCodes.SCOPE_UPDATE, "没有维护查询范围的权限");
        return ApiResponse.success(service.saveMetadata(id, input, guard.has(auth, PermissionCodes.DATASOURCE_TEST)),
                request);
    }
}
