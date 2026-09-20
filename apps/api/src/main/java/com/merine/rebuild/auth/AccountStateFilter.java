package com.merine.rebuild.auth;

import com.merine.rebuild.system.user.account.UserAccountLookup;
import com.merine.rebuild.system.user.account.UserAccountState;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 每次已认证请求核对账号当前状态，处理“会话还活着但账号已经不该继续用”的情况：
 * 账号或所属单位被停用、授权版本已变化（角色或数据范围改过）。
 *
 * 只作废会话，不直接写响应：清空上下文后，后续的授权规则会把请求判为未认证，
 * 由 {@link JsonAuthenticationEntryPoint} 统一返回 401，避免两处各写一套错误体。
 *
 * 代价是每个已认证请求多一次按主键的等值查询。本轮先接受这个成本，
 * 等真有性能证据再引入缓存。
 */
public class AccountStateFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AccountStateFilter.class);

    private final UserAccountLookup accounts;

    public AccountStateFilter(UserAccountLookup accounts) {
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedAccount account) {
            UserAccountState state = accounts.findStateById(account.userId());
            String reason = invalidationReason(state, account.authorizationVersion());
            if (reason != null) {
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();
                log.info("Session invalidated: userId={} reason={}", account.userId(), reason);
            }
        }
        filterChain.doFilter(request, response);
    }

    private static String invalidationReason(UserAccountState state, int sessionVersion) {
        if (state == null) {
            return "ACCOUNT_MISSING";
        }
        if (!state.enabled()) {
            return "ACCOUNT_OR_UNIT_DISABLED";
        }
        if (state.authorizationVersion() != sessionVersion) {
            return "AUTHORIZATION_CHANGED";
        }
        return null;
    }
}
