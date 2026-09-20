package com.merine.rebuild.system.role;

import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.common.PageResult;
import com.merine.rebuild.system.menu.MenuLookup;
import com.merine.rebuild.system.menu.dto.MenuNode;
import com.merine.rebuild.system.role.dto.RoleDetail;
import com.merine.rebuild.system.role.dto.RoleListItem;
import com.merine.rebuild.system.role.dto.RoleMember;
import com.merine.rebuild.system.role.dto.RoleRequests;
import com.merine.rebuild.system.role.dto.RoleSummary;
import com.merine.rebuild.system.security.PermissionCodes;
import com.merine.rebuild.system.security.PermissionGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * 角色管理接口。
 *
 * 每个方法都先过权限码门禁：菜单和按钮只改善体验，能不能调用由后端判定。
 * 启用与停用走各自的动作路径，不用「更新 status 字段」这种可以绕过状态规则的写法；
 * 成员清单只读——给谁授权仍在用户管理里做。
 */
@RestController
@RequestMapping("/api/system/roles")
@Tag(name = "角色管理", description = "角色查询、新增、编辑、启停、删除与成员查看")
public class RoleController {

    private final RoleLookup roles;
    private final RoleService admin;
    private final MenuLookup menus;
    private final PermissionGuard guard;

    public RoleController(RoleLookup roles, RoleService admin, MenuLookup menus,
                          PermissionGuard guard) {
        this.roles = roles;
        this.admin = admin;
        this.menus = menus;
        this.guard = guard;
    }

    /**
     * 角色选项：用户管理的筛选与表单选择，以及角色页自己回显用。
     * 用户管理者不需要额外拿到「角色管理」权限就能选角色，因此这里是「任一权限」。
     */
    @GetMapping("/options")
    @Operation(summary = "查询全部角色选项")
    public ApiResponse<List<RoleSummary>> options(Authentication authentication,
                                                  HttpServletRequest request) {
        guard.requireAny(authentication,
                List.of(PermissionCodes.USER_READ, PermissionCodes.ROLE_READ),
                "没有查看角色选项的权限");
        return ApiResponse.success(roles.listAll(), request);
    }

    @GetMapping
    @Operation(summary = "分页查询角色")
    public ApiResponse<PageResult<RoleListItem>> list(
            Authentication authentication,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.ROLE_READ, "没有查看角色管理的权限");
        return ApiResponse.success(admin.list(keyword, status, page, pageSize), request);
    }

    @GetMapping("/{code}")
    @Operation(summary = "查询角色详情（含权限集合与成员数）")
    public ApiResponse<RoleDetail> detail(@PathVariable String code, Authentication authentication,
                                          HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.ROLE_READ, "没有查看角色管理的权限");
        return ApiResponse.success(admin.get(code), request);
    }

    @GetMapping("/{code}/members")
    @Operation(summary = "分页查询角色成员（只读）")
    public ApiResponse<PageResult<RoleMember>> members(
            @PathVariable String code,
            Authentication authentication,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.ROLE_READ, "没有查看角色管理的权限");
        return ApiResponse.success(admin.members(code, page, pageSize), request);
    }

    /**
     * 权限勾选树：菜单模块的完整树（目录/页面/页签/按钮，含停用节点），
     * 角色编辑抽屉直接用它渲染勾选项，避免再维护一份扁平的权限清单。
     */
    @GetMapping("/permission-tree")
    @Operation(summary = "查询权限勾选树（菜单资源树）")
    public ApiResponse<List<MenuNode>> permissionTree(Authentication authentication,
                                                      HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.ROLE_READ, "没有查看角色管理的权限");
        return ApiResponse.success(menus.tree(), request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新建角色")
    public ApiResponse<RoleDetail> create(@Valid @RequestBody RoleRequests.CreateRole input,
                                          Authentication authentication,
                                          HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.ROLE_CREATE, "没有新建角色的权限");
        return ApiResponse.success(admin.create(input), request);
    }

    @PutMapping("/{code}")
    @Operation(summary = "编辑角色的名称、说明与功能权限")
    public ApiResponse<RoleDetail> update(@PathVariable String code,
                                          @Valid @RequestBody RoleRequests.UpdateRole input,
                                          Authentication authentication,
                                          HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.ROLE_UPDATE, "没有编辑角色的权限");
        return ApiResponse.success(admin.update(code, input), request);
    }

    @PostMapping("/enable")
    @Operation(summary = "启用角色（单个或批量）")
    public ApiResponse<List<RoleListItem>> enable(
            @Valid @RequestBody RoleRequests.ChangeRoleStatus input,
            Authentication authentication,
            HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.ROLE_TOGGLE_STATUS, "没有启停角色的权限");
        return ApiResponse.success(admin.changeStatus(input.roleCodes(), true), request);
    }

    @PostMapping("/disable")
    @Operation(summary = "停用角色（单个或批量）；持有者会话立即失效")
    public ApiResponse<List<RoleListItem>> disable(
            @Valid @RequestBody RoleRequests.ChangeRoleStatus input,
            Authentication authentication,
            HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.ROLE_TOGGLE_STATUS, "没有启停角色的权限");
        return ApiResponse.success(admin.changeStatus(input.roleCodes(), false), request);
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "删除无成员、非内置的角色；需提交打开页面时的版本号")
    public ApiResponse<Void> delete(@PathVariable String code,
                                    @RequestParam(defaultValue = "-1") int version,
                                    Authentication authentication,
                                    HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.ROLE_DELETE, "没有删除角色的权限");
        admin.delete(code, version);
        return ApiResponse.success(null, request);
    }
}
