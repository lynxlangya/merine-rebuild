package com.merine.rebuild.system.security;

import com.merine.rebuild.common.ApiException;
import java.util.Collection;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * 功能权限门禁：调用方必须持有指定权限码。
 *
 * 判定只看登录时下发的 authority，不查库也不认角色编码——内置管理员角色在登录时
 * 已经被展开成全部权限码，因此这里不存在第二套规则。菜单和按钮只改善体验，
 * 每个受保护接口都在后端独立过这道门。
 */
@Component
public class PermissionGuard {

    public void require(Authentication authentication, String permissionCode, String deniedMessage) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "尚未登录或会话已过期");
        }
        if (!hasAuthority(authentication, permissionCode)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", deniedMessage);
        }
    }

    /**
     * 选项类查询可能同时服务两个用例（例如单位选项既给单位管理也给用户表单），
     * 任一权限码命中即放行；拒绝文案仍由调用方写清当前操作。
     */
    public void requireAny(Authentication authentication, Collection<String> permissionCodes,
                           String deniedMessage) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "尚未登录或会话已过期");
        }
        boolean allowed = permissionCodes.stream().anyMatch(code -> hasAuthority(authentication, code));
        if (!allowed) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", deniedMessage);
        }
    }

    private static boolean hasAuthority(Authentication authentication, String permissionCode) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(permissionCode::equals);
    }
}
