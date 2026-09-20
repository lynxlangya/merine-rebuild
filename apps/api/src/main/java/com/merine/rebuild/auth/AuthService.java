package com.merine.rebuild.auth;

import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.system.permission.PermissionLookup;
import com.merine.rebuild.system.security.BuiltinAdminRoles;
import com.merine.rebuild.system.user.account.UserAccount;
import com.merine.rebuild.system.user.account.UserAccountCommands;
import com.merine.rebuild.system.user.account.UserAccountLookup;
import com.merine.rebuild.system.user.account.PasswordLimits;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Service;

/**
 * 登录、退出与会话生命周期。
 *
 * 登录不使用表单登录过滤器：这里是明确的 JSON 接口，需要自己控制响应体、
 * 会话超时与错误码。因此会话固定（session fixation）防护也必须显式做——
 * Spring Security 只在 {@code AbstractAuthenticationProcessingFilter} 里自动轮换会话 ID。
 */
@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /** 默认空闲超时，与 server.servlet.session.timeout 保持一致。 */
    private static final int SESSION_TIMEOUT_SECONDS = 30 * 60;
    private static final int REMEMBER_ME_TIMEOUT_SECONDS = 7 * 24 * 60 * 60;

    private final UserAccountLookup accounts;
    private final UserAccountCommands accountCommands;
    private final BuiltinAdminRoles builtinAdminRoles;
    private final PermissionLookup permissions;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextRepository securityContextRepository;
    private final CsrfTokenRepository csrfTokenRepository;

    /**
     * 账号不存在时用来消耗一次同等的哈希比对，避免用响应时间判断账号是否存在。
     * 每次启动随机生成，不是任何真实凭据。
     */
    private final String placeholderHash;

    public AuthService(UserAccountLookup accounts, UserAccountCommands accountCommands,
                       BuiltinAdminRoles builtinAdminRoles, PermissionLookup permissions,
                       PasswordEncoder passwordEncoder,
                       SecurityContextRepository securityContextRepository,
                       CsrfTokenRepository csrfTokenRepository) {
        this.accounts = accounts;
        this.accountCommands = accountCommands;
        this.builtinAdminRoles = builtinAdminRoles;
        this.permissions = permissions;
        this.passwordEncoder = passwordEncoder;
        this.securityContextRepository = securityContextRepository;
        this.csrfTokenRepository = csrfTokenRepository;
        this.placeholderHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public AuthenticatedAccount login(String loginName, String password, boolean rememberMe,
                                      HttpServletRequest request, HttpServletResponse response) {
        PasswordLimits.requireSupportedLength(password, "password");
        UserAccount account = accounts.findByLoginName(loginName);
        if (account == null) {
            passwordEncoder.matches(password, placeholderHash);
            throw invalidCredentials();
        }
        // 先验密码再看状态：否则不知道密码的人也能通过错误码区分出“这个账号存在且被停用”
        if (!passwordEncoder.matches(password, account.passwordHash())) {
            throw invalidCredentials();
        }
        if (!account.enabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED",
                    "该账号已停用，请联系单位系统管理员");
        }

        AuthenticatedAccount principal = AuthenticatedAccount.from(effectiveAccount(account));

        // 登录时间先登记再建会话：登记失败就当作登录失败，不留下时间戳为空的“已登录”
        accountCommands.recordLogin(principal.userId(), Instant.now());

        // 会话固定防护：认证成功后轮换会话 ID，已有会话属性随会话保留
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setMaxInactiveInterval(rememberMe ? REMEMBER_ME_TIMEOUT_SECONDS : SESSION_TIMEOUT_SECONDS);

        // 同时重签 CSRF 令牌：Spring 的 CsrfAuthenticationStrategy 只挂在表单登录过滤器上，
        // 这里是手写登录，不重签的话登录前被植入的令牌在登录后依然有效。
        csrfTokenRepository.saveToken(csrfTokenRepository.generateToken(request), request, response);

        // 两类 authority 一起下发：角色编码（ROLE_ 前缀，供展示类判断与历史语义）与
        // 功能权限码（原样，判权只看它）。数据范围仍未实现，不属于本轮。
        List<SimpleGrantedAuthority> authorities = Stream.concat(
                        principal.roleCodes().stream().map(code -> "ROLE_" + code),
                        principal.permissionCodes().stream())
                .map(SimpleGrantedAuthority::new)
                .toList();
        Authentication authentication = UsernamePasswordAuthenticationToken
                .authenticated(principal, null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        log.info("Login succeeded for userId={} rememberMe={}", principal.userId(), rememberMe);
        return principal;
    }

    /**
     * 内置管理员角色在登录时展开为全部权限码：漏配某个权限时仍然进得去，不会把自己锁在外面。
     * 判定只看登录那一刻的角色集合；之后角色变化会让授权版本递增，旧会话随即失效。
     */
    private UserAccount effectiveAccount(UserAccount account) {
        boolean builtin = account.roleCodes().stream().anyMatch(builtinAdminRoles::isBuiltin);
        if (!builtin) {
            return account;
        }
        List<String> allCodes = permissions.listAllCodes();
        return new UserAccount(account.id(), account.loginName(), account.displayName(),
                account.passwordHash(), account.unitName(), account.unitStatus(),
                account.accountStatus(), account.authorizationVersion(),
                account.roleCodes(), account.roleNames(), allCodes);
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    /**
     * 账号不存在与密码错误返回完全相同的结果，不泄漏账号是否存在。
     * 失败详情只记登录名，不记密码。
     */
    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "账号或密码不正确");
    }
}
