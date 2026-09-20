package com.merine.rebuild.system.menu;

import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.system.menu.dto.NavigationNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前账号的导航树。
 *
 * 只要登录就能调用：返回什么完全由会话里的权限码决定，而不是由调用方传参决定。
 * 前端据此渲染左侧导航与首页入口；隐藏的菜单不等于禁止访问，接口仍按权限码独立判定。
 */
@RestController
@RequestMapping("/api/me/menus")
@Tag(name = "导航", description = "当前账号可见的菜单树")
public class MenuNavigationController {

    private final MenuService service;

    public MenuNavigationController(MenuService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "查询当前账号可见的导航树")
    public ApiResponse<List<NavigationNode>> myMenus(Authentication authentication,
                                                     HttpServletRequest request) {
        // authority 里既有 ROLE_<角色编码>，也有功能权限码；导航只看后者。
        List<String> permissionCodes = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> !authority.startsWith("ROLE_"))
                .toList();
        return ApiResponse.success(service.navigation(permissionCodes), request);
    }
}
