package com.merine.rebuild.auth;

import com.merine.rebuild.system.user.account.UserAccount;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 会话中保留的身份事实。
 *
 * 这里刻意不含密码哈希：凭据只在登录比对那一刻存在，认证完成后会话里只留身份。
 * 角色编码与名称用于展示和「我自己是否持有这个角色」的判断；
 * 判权只看 permissionCodes（登录时已把内置管理员角色展开为全部权限码）。
 * 数据范围仍未实现，不属于本结构。
 */
public record AuthenticatedAccount(
        long userId,
        String loginName,
        String displayName,
        String unitName,
        /** 稳定角色编码，用于授权判定；名称只用于展示。 */
        List<String> roleCodes,
        List<String> roleNames,
        /** 登录时确定的有效功能权限码；会话期间不变，授权版本变化即整段失效。 */
        List<String> permissionCodes,
        int authorizationVersion) implements Serializable {

    @Serial
    private static final long serialVersionUID = 3L;

    public static AuthenticatedAccount from(UserAccount account) {
        return new AuthenticatedAccount(
                account.id(),
                account.loginName(),
                account.displayName(),
                account.unitName(),
                account.roleCodes(),
                account.roleNames(),
                account.permissionCodes(),
                account.authorizationVersion());
    }
}
