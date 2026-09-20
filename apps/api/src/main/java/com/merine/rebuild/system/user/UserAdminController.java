package com.merine.rebuild.system.user;

import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.common.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理接口。
 *
 * 每个方法都先过 {@link UserAdminGuard}：菜单和按钮只是体验，能不能调用由后端判定。
 * 启用与停用走各自的动作路径，不用「更新 status 字段」这种可以绕过状态规则的写法。
 */
@RestController
@RequestMapping("/api/system/users")
@Tag(name = "用户管理", description = "账号查询、新建、编辑与启用停用")
public class UserAdminController {
    private final UserAdminService service;
    private final UserAdminGuard guard;

    public UserAdminController(UserAdminService service, UserAdminGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    @Operation(summary = "分页查询用户")
    public ApiResponse<PageResult<UserSummary>> list(
            Authentication authentication,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String unitCode,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        guard.require(authentication);
        return ApiResponse.success(
                service.list(keyword, unitCode, roleCode, status, page, pageSize), request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询用户详情")
    public ApiResponse<UserSummary> detail(@PathVariable long id, Authentication authentication,
                                           HttpServletRequest request) {
        guard.require(authentication);
        return ApiResponse.success(service.get(id), request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新建用户")
    public ApiResponse<UserSummary> create(@Valid @RequestBody UserRequests.CreateUser input,
                                           Authentication authentication,
                                           HttpServletRequest request) {
        guard.require(authentication);
        return ApiResponse.success(service.create(input), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑用户的基本信息、角色，或重置密码")
    public ApiResponse<UserSummary> update(@PathVariable long id,
                                           @Valid @RequestBody UserRequests.UpdateUser input,
                                           Authentication authentication,
                                           HttpServletRequest request) {
        guard.require(authentication);
        return ApiResponse.success(service.update(id, input), request);
    }

    @PostMapping("/enable")
    @Operation(summary = "启用账号（单个或批量）")
    public ApiResponse<List<UserSummary>> enable(@Valid @RequestBody UserRequests.ChangeStatus input,
                                                 Authentication authentication,
                                                 HttpServletRequest request) {
        guard.require(authentication);
        return ApiResponse.success(service.changeStatus(input.userIds(), true), request);
    }

    @PostMapping("/disable")
    @Operation(summary = "停用账号（单个或批量）；停用后该账号的已有会话立即失效")
    public ApiResponse<List<UserSummary>> disable(@Valid @RequestBody UserRequests.ChangeStatus input,
                                                  Authentication authentication,
                                                  HttpServletRequest request) {
        guard.require(authentication);
        return ApiResponse.success(service.changeStatus(input.userIds(), false), request);
    }
}
