package com.merine.rebuild.agent.datasource;

import com.merine.rebuild.agent.datasource.dto.DataSourceCheckResult;
import com.merine.rebuild.agent.datasource.dto.DataSourceRequests.CheckDraft;
import com.merine.rebuild.agent.datasource.dto.DataSourceRequests.Save;
import com.merine.rebuild.agent.datasource.dto.DataSourceRequests.StatusChange;
import com.merine.rebuild.agent.datasource.dto.DataSourceView;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 数据源管理：配置连接、启停与测试连接/检查只读授权。
 *
 * <p>密码只写不读；测试连接是敏感管理操作（会带服务端网络访问能力），单独一个权限码；
 * 这里只管配置是否正确，不代表 Agent 已被允许使用数据源。
 */
@RestController
@RequestMapping("/api/agent/datasources")
@Tag(name = "数据源管理", description = "Agent 受控查询的数据源配置；密码只写不读，检查只读授权")
public class DataSourceController {
    private final DataSourceService service;
    private final PermissionGuard guard;

    public DataSourceController(DataSourceService service, PermissionGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    public ApiResponse<PageResult<DataSourceView>> list(
            @RequestParam(defaultValue = "") @Size(max = 80) String search,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(60) int pageSize,
            Authentication auth, HttpServletRequest request) {
        guard.require(auth, PermissionCodes.DATASOURCE_READ, "没有查看数据源的权限");
        return ApiResponse.success(service.list(search, page, pageSize), request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DataSourceView> create(@Valid @RequestBody Save input,
                                              Authentication auth, HttpServletRequest request) {
        guard.require(auth, PermissionCodes.DATASOURCE_CREATE, "没有新增数据源的权限");
        return ApiResponse.success(service.create(input), request);
    }

    @PutMapping("/{id}")
    public ApiResponse<DataSourceView> update(@PathVariable String id, @Valid @RequestBody Save input,
                                              Authentication auth, HttpServletRequest request) {
        guard.require(auth, PermissionCodes.DATASOURCE_UPDATE, "没有编辑数据源的权限");
        return ApiResponse.success(service.update(id, input), request);
    }

    /** 未保存配置的连接测试；已保存配置的检查见 {@link #checkSaved}。 */
    @PostMapping("/check")
    public ApiResponse<DataSourceCheckResult> check(@Valid @RequestBody CheckDraft input,
                                                    Authentication auth, HttpServletRequest request) {
        guard.require(auth, PermissionCodes.DATASOURCE_TEST, "没有测试数据源连接的权限");
        return ApiResponse.success(service.checkDraft(input), request);
    }

    @PostMapping("/{id}/check")
    public ApiResponse<DataSourceCheckResult> checkSaved(@PathVariable String id,
                                                         Authentication auth,
                                                         HttpServletRequest request) {
        guard.require(auth, PermissionCodes.DATASOURCE_TEST, "没有测试数据源连接的权限");
        return ApiResponse.success(service.checkSaved(id), request);
    }

    /** 启停：启用会在服务端重新检查，通过且已勾选视图才会真正生效。 */
    @PostMapping("/{id}/status")
    public ApiResponse<DataSourceView> updateStatus(@PathVariable String id,
                                                    @Valid @RequestBody StatusChange input,
                                                    Authentication auth,
                                                    HttpServletRequest request) {
        guard.require(auth, PermissionCodes.DATASOURCE_TOGGLE_STATUS, "没有启停数据源的权限");
        return ApiResponse.success(service.setStatus(id, input), request);
    }
}
