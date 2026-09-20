package com.merine.rebuild.auth;

import com.merine.rebuild.system.user.UserAccount;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 会话中保留的身份事实。
 *
 * 这里刻意不含密码哈希：凭据只在登录比对那一刻存在，认证完成后会话里只留身份。
 * 本轮没有功能权限模型（角色分配与数据范围留到后续阶段），因此不携带 authorities，
 * 安全规则只有“已认证”。等权限模型落地时，这里再加最小必要的权限标识与数据范围。
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
                account.roles(),
                account.authorizationVersion());
    }
}
