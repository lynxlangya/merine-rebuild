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
        List<String> roleNames) {

    public UserAccount {
        roleCodes = List.copyOf(roleCodes);
        roleNames = List.copyOf(roleNames);
    }

    public boolean enabled() {
        return "ENABLED".equals(accountStatus) && "ENABLED".equals(unitStatus);
    }
}
