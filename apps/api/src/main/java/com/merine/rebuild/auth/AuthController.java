package com.merine.rebuild.auth;

import com.merine.rebuild.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话是当前身份的资源：GET 取当前身份，POST 建立会话（登录），DELETE 结束会话（退出）。
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "认证", description = "登录、退出与当前身份；会话保存在服务端，Cookie 只带会话标识")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 触发 CSRF 令牌生成并写入 Cookie。令牌值只经 Cookie 交给同源页面，
     * 不放响应体，避免页面脚本之外的地方拿到它。
     */
    @GetMapping("/csrf")
    @Operation(summary = "获取 CSRF 令牌")
    public ApiResponse<Void> csrf(CsrfToken csrfToken, HttpServletRequest request) {
        csrfToken.getToken();
        return ApiResponse.success(null, request);
    }

    @PostMapping("/session")
    @Operation(summary = "登录并建立会话")
    public ApiResponse<AuthUserResponse> login(@Valid @RequestBody LoginRequest input,
                                               HttpServletRequest request,
                                               HttpServletResponse response) {
        AuthenticatedAccount account = authService.login(
                input.loginName().strip(), input.password(), input.rememberMeOrDefault(), request, response);
        return ApiResponse.success(AuthUserResponse.from(account), request);
    }

    @DeleteMapping("/session")
    @Operation(summary = "退出并作废当前会话")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        authService.logout(request);
        return ApiResponse.success(null, request);
    }

    @GetMapping("/session")
    @Operation(summary = "获取当前身份")
    public ApiResponse<AuthUserResponse> current(Authentication authentication,
                                                 HttpServletRequest request) {
        // 该端点要求已认证，走到这里 principal 必然是 AuthenticatedAccount
        return ApiResponse.success(
                AuthUserResponse.from((AuthenticatedAccount) authentication.getPrincipal()), request);
    }
}
