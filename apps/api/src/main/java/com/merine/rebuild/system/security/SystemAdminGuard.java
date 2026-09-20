package com.merine.rebuild.system.security;

import com.merine.rebuild.common.ApiException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * 系统管理接口的授权判定，当前供用户、角色和单位管理复用。
 *
 * 放这里而不是加注解：判定依据是登录时下发的 authority，规则只有一条，
 * 一处集中判定比在十几个方法上散落 SpEL 更容易读懂与改动。
 *
 * 这只是最小的一道门，不是权限模型：角色能做什么功能、能看哪些业务数据
 * 仍然没有定义，数据范围与功能权限留到后续阶段。
 */
@Component
public class SystemAdminGuard {

    /** 角色编码，供 SQL 使用（例如统计还剩几个可管理用户的账号）。 */
    private final Set<String> adminRoleCodes;
    /** 与 Authentication 里的 authority 比对用。 */
    private final Set<String> adminAuthorities;

    public SystemAdminGuard(
            @Value("${merine.security.system-admin-role-codes:SYSTEM_ADMIN}") String configuredCodes) {
        this.adminRoleCodes = Arrays.stream(configuredCodes.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (adminRoleCodes.isEmpty()) {
            throw new IllegalStateException("merine.security.system-admin-role-codes 不能为空");
        }
        this.adminAuthorities = adminRoleCodes.stream()
                .map(code -> "ROLE_" + code)
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<String> adminRoleCodes() {
        return adminRoleCodes;
    }

    public void require(Authentication authentication) {
        require(authentication, "没有系统管理权限");
    }

    /**
     * 同一道系统管理门禁可以被不同模块复用，但拒绝文案要写清当前操作的对象。
     */
    public void require(Authentication authentication, String deniedMessage) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "尚未登录或会话已过期");
        }
        boolean allowed = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(adminAuthorities::contains);
        if (!allowed) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", deniedMessage);
        }
    }
}
