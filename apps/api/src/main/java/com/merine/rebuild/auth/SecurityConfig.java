package com.merine.rebuild.auth;

import com.merine.rebuild.system.user.account.UserAccountLookup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * 一条覆盖全应用的过滤器链。除登录所需端点、健康探针与（按环境决定的）接口文档外，
 * 其余请求一律要求认证；不放开整个 /api/** 或 /actuator/**。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // BCrypt：自带盐的单向编码器；库里只存哈希，接口与日志都不输出
        return new BCryptPasswordEncoder();
    }

    /**
     * 令牌仓库同时给两条路径用：过滤器链读取/校验，以及登录成功后重签令牌。
     * 必须是同一个 Bean——两处各建一个实例会让重签的令牌与校验用的不是同一份配置。
     */
    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SecurityContextRepository securityContextRepository,
            CsrfTokenRepository csrfTokenRepository,
            UserAccountLookup accounts,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler,
            Environment environment,
            @Value("${merine.security.dev-login-enabled:false}") boolean devLoginEnabled,
            @Value("${merine.security.public-api-docs:false}") boolean publicApiDocs) throws Exception {

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                // 不缓存未认证请求，也不做登录重定向：前端自己决定回跳目标
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // 退出用 DELETE /api/auth/session，由 AuthController 控制响应体
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(registry -> {
                    registry.requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll();
                    registry.requestMatchers(HttpMethod.POST, "/api/auth/session").permitAll();
                    if (devLoginEnabled && environment.acceptsProfiles(Profiles.of("dev & !prod"))) {
                        registry.requestMatchers(HttpMethod.GET, "/api/auth/dev/accounts").permitAll();
                        registry.requestMatchers(HttpMethod.POST, "/api/auth/dev/session").permitAll();
                    }
                    // 探针只反映进程与数据库可达性，show-details 为 never，不带库内详情
                    registry.requestMatchers(readinessProbes()).permitAll();
                    if (publicApiDocs) {
                        registry.requestMatchers(HttpMethod.GET,
                                "/api/docs", "/api/openapi", "/api/openapi.yaml",
                                "/api/openapi/swagger-config", "/api/swagger-ui/**").permitAll();
                    }
                    registry.anyRequest().authenticated();
                })
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterAfter(new AccountStateFilter(accounts), SecurityContextHolderFilter.class);

        return http.build();
    }

    private static RequestMatcher readinessProbes() {
        return request -> {
            String path = request.getRequestURI();
            return "/actuator/health/liveness".equals(path) || "/actuator/health/readiness".equals(path);
        };
    }

    private static CsrfTokenRequestHandler csrfRequestHandler() {
        return new SpaCsrfTokenRequestHandler();
    }
}
