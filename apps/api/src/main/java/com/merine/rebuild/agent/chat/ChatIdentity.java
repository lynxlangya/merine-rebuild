package com.merine.rebuild.agent.chat;

import com.merine.rebuild.auth.AuthenticatedAccount;
import com.merine.rebuild.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

/** 演示端点的身份入口：只认会话里的 {@link AuthenticatedAccount}，不接受请求参数声明身份。 */
final class ChatIdentity {

    private ChatIdentity() {}

    static AuthenticatedAccount require(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedAccount account)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "尚未登录或会话已过期");
        }
        return account;
    }
}
