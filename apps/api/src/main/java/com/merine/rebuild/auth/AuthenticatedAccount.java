package com.merine.rebuild.auth;

import com.merine.rebuild.system.user.account.UserAccount;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 会话中保留的身份事实。
 *
 * 这里刻意不含密码哈希：凭据只在登录比对那一刻存在，认证完成后会话里只留身份。
 * 角色编码用于当前系统管理门禁，名称用于展示；功能权限与数据范围尚未实现。
 */
public record AuthenticatedAccount(
        long userId,
        String loginName,
        String displayName,
        String unitName,
        /** 稳定角色编码，用于授权判定；名称只用于展示。 */
        List<String> roleCodes,
        List<String> roleNames,
        int authorizationVersion) implements Serializable {

    @Serial
    private static final long serialVersionUID = 2L;

    public static AuthenticatedAccount from(UserAccount account) {
        return new AuthenticatedAccount(
                account.id(),
                account.loginName(),
                account.displayName(),
                account.unitName(),
                account.roleCodes(),
                account.roleNames(),
                account.authorizationVersion());
    }
}
