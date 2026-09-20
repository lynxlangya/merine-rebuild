package com.merine.rebuild.system.security;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 内置管理员角色编码（配置 merine.security.system-admin-role-codes，默认 SYSTEM_ADMIN）。
 *
 * 这些角色在登录时被展开为**全部权限码**，是「漏配权限也不会把自己锁死」的兜底；
 * 角色管理据此把它们标记为内置：不可删除、权限只读，停用时必须仍有其它可用管理账号。
 * 属性名沿用旧名，避免与历史配置、测试和文档产生两套写法。
 */
@Component
public class BuiltinAdminRoles {

    private final Set<String> codes;

    public BuiltinAdminRoles(
            @Value("${merine.security.system-admin-role-codes:SYSTEM_ADMIN}") String configuredCodes) {
        this.codes = Arrays.stream(configuredCodes.split(","))
                .map(String::trim)
                .filter(code -> !code.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        if (codes.isEmpty()) {
            throw new IllegalStateException("merine.security.system-admin-role-codes 不能为空");
        }
    }

    public Set<String> codes() {
        return codes;
    }

    public boolean isBuiltin(String roleCode) {
        return codes.contains(roleCode);
    }
}
