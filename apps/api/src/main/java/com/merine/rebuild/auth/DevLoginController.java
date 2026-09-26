package com.merine.rebuild.auth;

import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.system.user.account.DevLoginAccountOption;
import com.merine.rebuild.system.user.account.UserAccountLookup;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅在本地开发 profile 注册；交付环境没有免密登录路由。 */
@Profile("dev & !prod")
@ConditionalOnProperty(name = "merine.security.dev-login-enabled", havingValue = "true")
@RestController
@RequestMapping("/api/auth/dev")
public class DevLoginController {
    private final UserAccountLookup accounts;
    private final AuthService authService;

    public DevLoginController(UserAccountLookup accounts, AuthService authService) {
        this.accounts = accounts;
        this.authService = authService;
    }

    @GetMapping("/accounts")
    @Operation(summary = "列出本地开发环境可登录的账号")
    public ApiResponse<List<DevLoginAccountOption>> accounts(HttpServletRequest request) {
        return ApiResponse.success(accounts.listEnabledForDevLogin(), request);
    }

    @PostMapping("/session")
    @Operation(summary = "本地开发环境选择账号建立会话")
    public ApiResponse<AuthUserResponse> login(@Valid @RequestBody DevLoginRequest input,
                                               HttpServletRequest request, HttpServletResponse response) {
        AuthenticatedAccount account = authService.loginForDevelopment(
                input.loginName().strip(), input.rememberMeOrDefault(), request, response);
        return ApiResponse.success(AuthUserResponse.from(account), request);
    }
}
