package com.merine.rebuild.system.user.account;

import java.util.List;

/**
 * 登录认证所需的账号事实，由 system 模块对外提供。
 *
 * 这是模块间的公开契约：auth 只能通过它读取账号，不得直接访问本模块的 Mapper 或表。
 * 其中的 passwordHash 仅用于服务端一次性比对，不进入会话、不进入任何响应或日志。
 */
public record UserAccount(
        long id,
        String loginName,
        String displayName,
        String passwordHash,
        String unitName,
        String unitStatus,
        String accountStatus,
        int authorizationVersion,
        List<String> roleCodes,
        List<String> roleNames,
        /**
         * 启用角色带来的功能权限码并集；内置管理员角色的展开发生在登录（AuthService），
         * 因此这里对内置角色的账号可能为空——判定只认登录时下发的 authority。
         */
        List<String> permissionCodes) {

    public UserAccount {
        roleCodes = List.copyOf(roleCodes);
        roleNames = List.copyOf(roleNames);
        permissionCodes = List.copyOf(permissionCodes);
    }

    public boolean enabled() {
        return "ENABLED".equals(accountStatus) && "ENABLED".equals(unitStatus);
    }
}
