package com.merine.rebuild.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * 同源 SPA 的 CSRF 令牌处理：生成用 XOR，校验按来源分派。
 *
 * 为什么不能只用 XorCsrfTokenRequestAttributeHandler：XOR 变体会把令牌编码后再交给
 * 页面，校验时也要求收到编码值。但本方案的令牌经 Cookie（原始值）交给页面，页面再把
 * 原始值放进请求头，用 XOR 校验必然失败。同时也不能只用原始处理器：那样就丢掉
 * BREACH 防护与延迟生成。
 *
 * 因此：请求头里带令牌（页面从 Cookie 读取）时按原始值校验；没有请求头时按 XOR 校验
 * （预留给以后可能出现的表单字段提交）。生成路径始终是 XOR，并保持延迟生成——
 * 不预先读取令牌，GET /api/auth/csrf 才有机会决定何时下发 Cookie。
 */
final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler xor = deferredXor();
    private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       Supplier<CsrfToken> csrfToken) {
        xor.handle(request, response, csrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        return (StringUtils.hasText(headerValue) ? plain : xor)
                .resolveCsrfTokenValue(request, csrfToken);
    }

    private static CsrfTokenRequestHandler deferredXor() {
        XorCsrfTokenRequestAttributeHandler handler = new XorCsrfTokenRequestAttributeHandler();
        // 置空属性名：未真正读取之前不生成令牌，Cookie 的写入时机由接口决定
        handler.setCsrfRequestAttributeName(null);
        return handler;
    }
}
