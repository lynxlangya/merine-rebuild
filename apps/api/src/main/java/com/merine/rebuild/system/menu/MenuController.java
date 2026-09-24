package com.merine.rebuild.system.menu;

import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.system.menu.dto.IconOption;
import com.merine.rebuild.system.menu.dto.MenuDeleteImpact;
import com.merine.rebuild.system.menu.dto.MenuNode;
import com.merine.rebuild.system.menu.dto.MenuRequests;
import com.merine.rebuild.system.menu.dto.RestoreMenusResult;
import com.merine.rebuild.system.menu.dto.RouteKeyOption;
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
 * 菜单管理接口：目录、页面、页签、按钮的树，以及每个节点对应的权限码。
 *
 * 页面节点只能绑前端已注册的 route key；权限码创建后不可修改。
 * 菜单停用只影响导航展示与可分配性，不改变已授权功能——隐藏菜单从不作为授权手段。
 */
@RestController
@RequestMapping("/api/system/menus")
@Tag(name = "菜单管理", description = "菜单资源树与按钮权限维护")
public class MenuController {

    private final MenuService service;
    private final PermissionGuard guard;

    public MenuController(MenuService service, PermissionGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    @Operation(summary = "查询完整菜单树（含停用节点）")
    public ApiResponse<List<MenuNode>> tree(Authentication authentication,
                                            HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MENU_READ, "没有查看菜单的权限");
        return ApiResponse.success(service.tree(), request);
    }

    @GetMapping("/route-keys")
    @Operation(summary = "查询前端已注册的路由 key（页面节点只能从中选择）")
    public ApiResponse<List<RouteKeyOption>> routeKeys(Authentication authentication,
                                                       HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MENU_READ, "没有查看菜单的权限");
        return ApiResponse.success(service.routeKeys(), request);
    }

    @GetMapping("/icons")
    @Operation(summary = "查询前端已注册的导航图标（目录与页面节点只能从中选择）")
    public ApiResponse<List<IconOption>> icons(Authentication authentication,
                                               HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MENU_READ, "没有查看菜单的权限");
        return ApiResponse.success(service.icons(), request);
    }

    @GetMapping("/{id}/delete-impact")
    @Operation(summary = "预览删除影响：子树节点数与将失去授权的角色数")
    public ApiResponse<MenuDeleteImpact> deleteImpact(@PathVariable String id,
                                                      Authentication authentication,
                                                      HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MENU_READ, "没有查看菜单的权限");
        return ApiResponse.success(service.deleteImpact(id), request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增菜单节点（目录/页面/页签/按钮）")
    public ApiResponse<MenuNode> create(@Valid @RequestBody MenuRequests.CreateMenu input,
                                        Authentication authentication,
                                        HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MENU_CREATE, "没有新增菜单的权限");
        return ApiResponse.success(service.create(input), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑节点名称、上级、排序、状态与说明")
    public ApiResponse<MenuNode> update(@PathVariable String id,
                                        @Valid @RequestBody MenuRequests.UpdateMenu input,
                                        Authentication authentication,
                                        HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MENU_UPDATE, "没有编辑菜单的权限");
        return ApiResponse.success(service.update(id, input), request);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除节点及其子树，并解除相关角色的授权")
    public ApiResponse<MenuDeleteImpact> delete(@PathVariable String id,
                                                @RequestParam(defaultValue = "-1") int version,
                                                Authentication authentication,
                                                HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MENU_DELETE, "没有删除菜单的权限");
        return ApiResponse.success(service.delete(id, version), request);
    }

    @PostMapping("/restore")
    @Operation(summary = "恢复默认菜单：只补缺失的引导节点与权限码")
    public ApiResponse<RestoreMenusResult> restore(Authentication authentication,
                                                   HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MENU_RESTORE, "没有恢复默认菜单的权限");
        return ApiResponse.success(service.restore(), request);
    }
}
