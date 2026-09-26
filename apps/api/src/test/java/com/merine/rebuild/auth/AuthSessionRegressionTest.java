package com.merine.rebuild.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 认证与会话的关键行为回归。
 *
 * 每条用例都走完整的过滤器链与真实 MySQL：断言的是业务结果（HTTP 状态、错误码、身份字段）
 * 与数据库状态（会话是否被作废、授权版本是否已改），不是 mock 的调用次数。
 */
@DisplayName("认证与会话回归")
class AuthSessionRegressionTest extends AuthSessionRegressionSupport {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void passwordlessLoginIsAbsentWithoutDevProfile() throws Exception {
        assertThat(applicationContext.getBeansOfType(DevLoginController.class)).isEmpty();
        assertUnauthenticatedJson(mockMvc.perform(get("/api/auth/dev/accounts")).andReturn());
    }

    @Test
    void apiDocsAndTheirAssetsRemainProtectedByDefault() throws Exception {
        for (String path : List.of("/api/docs", "/api/openapi", "/api/openapi/swagger-config",
                "/api/swagger-ui/index.html", "/api/swagger-ui/swagger-ui.css")) {
            assertThat(mockMvc.perform(get(path)).andReturn().getResponse().getStatus())
                    .as("默认保护文档路径 %s", path).isEqualTo(401);
        }
    }

    @Test
    @DisplayName("正确凭证登录返回 200 与真实身份字段，同一会话随后可访问受保护接口")
    void validCredentialsReturnIdentityAndOpenSessionForProtectedEndpoint() throws Exception {
        Cookie csrf = issueCsrfToken(null);
        MockHttpSession session = new MockHttpSession();

        MvcResult login = login(LOGIN_NAME, RAW_PASSWORD, csrf, session);

        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        String body = bodyOf(login);
        assertThat((String) jsonOf(body, "$.code")).isEqualTo("OK");

        assertThat((String) jsonOf(body, "$.data.loginName")).isEqualTo(LOGIN_NAME);
        assertThat((String) jsonOf(body, "$.data.displayName")).isEqualTo(DISPLAY_NAME);
        assertThat((String) jsonOf(body, "$.data.unitName")).isEqualTo(UNIT_NAME);
        List<String> roleNames = jsonOf(body, "$.data.roleNames");
        assertThat(roleNames).containsExactly(ROLE_NAME);
        assertThat((String) jsonOf(body, "$.data.id")).isEqualTo(Long.toString(userId));
        Integer authorizationVersion = jsonOf(body, "$.data.authorizationVersion");
        assertThat(authorizationVersion).isZero();

        MvcResult protectedCall = mockMvc.perform(get("/api/bootstrap").session(session)).andReturn();
        assertThat(protectedCall.getResponse().getStatus()).as("登录后的会话必须能访问受保护接口").isEqualTo(200);
        assertThat((String) jsonOf(bodyOf(protectedCall), "$.data.database")).isEqualTo("merine_rebuild_test");
    }

    @Test
    @DisplayName("错误密码登录返回 401 INVALID_CREDENTIALS")
    void wrongPasswordIsRejectedWithInvalidCredentials() throws Exception {
        Cookie csrf = issueCsrfToken(null);

        MvcResult result = login(LOGIN_NAME, RAW_PASSWORD + "-wrong", csrf, null);

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        String body = bodyOf(result);
        assertThat((String) jsonOf(body, "$.code")).isEqualTo("INVALID_CREDENTIALS");
        Object data = jsonOf(body, "$.data");
        assertThat(data).isNull();
        assertThat((String) jsonOf(body, "$.requestId")).isNotBlank();
    }

    @Test
    @DisplayName("不存在的账号返回 401，响应体与错误密码完全一致（除 requestId），不泄漏账号是否存在")
    void unknownAccountResponseIsIdenticalToWrongPasswordResponse() throws Exception {
        Cookie csrf = issueCsrfToken(null);

        MvcResult wrongPassword = login(LOGIN_NAME, RAW_PASSWORD + "-wrong", csrf, null);
        MvcResult unknownAccount = login(LOGIN_NAME_PREFIX + "no-such-account", RAW_PASSWORD, csrf, null);

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownAccount.getResponse().getStatus()).isEqualTo(401);

        String wrongBody = bodyOf(wrongPassword);
        String unknownBody = bodyOf(unknownAccount);
        String wrongRequestId = jsonOf(wrongBody, "$.requestId");
        String unknownRequestId = jsonOf(unknownBody, "$.requestId");
        assertThat(wrongRequestId).isNotBlank();
        assertThat(unknownRequestId).isNotBlank().isNotEqualTo(wrongRequestId);

        assertThat(unknownBody.replace(unknownRequestId, "<requestId>"))
                .as("账号存在与否不能从响应体里读出来")
                .isEqualTo(wrongBody.replace(wrongRequestId, "<requestId>"));
        assertThat(unknownBody).contains("\"code\":\"INVALID_CREDENTIALS\"");
    }

    @Test
    @DisplayName("空账号与空密码返回 400 VALIDATION_ERROR，fieldErrors 落在 loginName 与 password 上")
    void blankLoginNameAndPasswordReturnValidationErrorForBothFields() throws Exception {
        Cookie csrf = issueCsrfToken(null);

        MvcResult result = mockMvc.perform(withCsrf(post("/api/auth/session")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody("", "")), csrf)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        String body = bodyOf(result);
        assertThat((String) jsonOf(body, "$.code")).isEqualTo("VALIDATION_ERROR");
        List<String> fields = jsonOf(body, "$.fieldErrors[*].field");
        assertThat(fields).containsExactlyInAnyOrder("loginName", "password");
    }

    @Test
    @DisplayName("未登录访问受保护接口返回 401 UNAUTHENTICATED 的 JSON（不是 HTML）且带请求编号")
    void unauthenticatedProtectedRequestReturnsJsonUnauthenticatedWithRequestId() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/bootstrap")).andReturn();
        MockHttpServletResponse response = result.getResponse();

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType())
                .as("未登录响应必须是 JSON，不能是登录页 HTML")
                .startsWith(MediaType.APPLICATION_JSON_VALUE);

        String body = bodyOf(result);
        assertThat(body.strip())
                .as("响应体必须是 JSON 对象")
                .startsWith("{")
                .doesNotContain("<html")
                .doesNotContain("<!DOCTYPE");

        assertThat((String) jsonOf(body, "$.code")).isEqualTo("UNAUTHENTICATED");
        Object data = jsonOf(body, "$.data");
        assertThat(data).isNull();

        String requestId = jsonOf(body, "$.requestId");
        assertThat(requestId).isNotBlank();
        assertThat(response.getHeader("X-Request-Id"))
                .as("响应头与响应体的请求编号必须一致")
                .isEqualTo(requestId);
    }

    @Test
    @DisplayName("已登录但缺少 CSRF 令牌的写请求被拒为 403，同一会话补上令牌后可写入")
    void authenticatedWriteWithoutCsrfTokenIsForbidden() throws Exception {
        Cookie csrf = issueCsrfToken(null);
        MockHttpSession session = new MockHttpSession();
        assertThat(login(LOGIN_NAME, RAW_PASSWORD, csrf, session).getResponse().getStatus()).isEqualTo(200);

        MvcResult withoutToken = mockMvc.perform(post("/api/bootstrap/probes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(noteBody("regr-auth-without-csrf"))
                .session(session)).andReturn();

        assertThat(withoutToken.getResponse().getStatus()).isEqualTo(403);
        String body = bodyOf(withoutToken);
        assertThat((String) jsonOf(body, "$.code")).isEqualTo("FORBIDDEN");
        assertThat(session.isInvalid()).as("CSRF 拒绝不退出登录，会话应继续有效").isFalse();

        // 同一会话带上令牌再写一次：证明上面的 403 确实来自缺令牌，而不是会话或接口本身不可用
        MvcResult withToken = mockMvc.perform(withCsrf(post("/api/bootstrap/probes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(noteBody("regr-auth-with-csrf")), csrf)
                .session(session)).andReturn();
        assertThat(withToken.getResponse().getStatus()).isEqualTo(201);
        assertThat((String) jsonOf(bodyOf(withToken), "$.data.note")).isEqualTo("regr-auth-with-csrf");
    }

    @Test
    @DisplayName("账号停用后已有会话下一次请求即 401，重新登录返回 403 ACCOUNT_DISABLED")
    void disabledAccountInvalidatesSessionAndRefusesNewLogin() throws Exception {
        Cookie csrf = issueCsrfToken(null);
        MockHttpSession session = new MockHttpSession();
        assertThat(login(LOGIN_NAME, RAW_PASSWORD, csrf, session).getResponse().getStatus()).isEqualTo(200);
        assertThat(mockMvc.perform(get("/api/bootstrap").session(session)).andReturn().getResponse().getStatus())
                .as("停用之前会话可用")
                .isEqualTo(200);

        updateAccountStatus("DISABLED");

        MvcResult afterDisable = mockMvc.perform(get("/api/auth/session").session(session)).andReturn();
        assertThat(afterDisable.getResponse().getStatus()).isEqualTo(401);
        assertThat((String) jsonOf(bodyOf(afterDisable), "$.code")).isEqualTo("UNAUTHENTICATED");
        assertThat(session.isInvalid())
                .as("失效判定必须作废服务端会话，而不是只让本次请求失败")
                .isTrue();

        // 密码正确但账号已停用：重新登录必须被明确拒绝，并且是新会话上的新一次登录
        Cookie freshCsrf = issueCsrfToken(null);
        MvcResult relogin = login(LOGIN_NAME, RAW_PASSWORD, freshCsrf, new MockHttpSession());
        assertThat(relogin.getResponse().getStatus()).isEqualTo(403);
        assertThat((String) jsonOf(bodyOf(relogin), "$.code")).isEqualTo("ACCOUNT_DISABLED");
    }

    @Test
    @DisplayName("授权版本递增后已有会话下一次请求即 401，重新登录拿到新版本")
    void authorizationVersionBumpInvalidatesExistingSession() throws Exception {
        Cookie csrf = issueCsrfToken(null);
        MockHttpSession session = new MockHttpSession();
        assertThat(login(LOGIN_NAME, RAW_PASSWORD, csrf, session).getResponse().getStatus()).isEqualTo(200);

        bumpAuthorizationVersion();

        MvcResult afterBump = mockMvc.perform(get("/api/auth/session").session(session)).andReturn();
        assertThat(afterBump.getResponse().getStatus()).isEqualTo(401);
        assertThat((String) jsonOf(bodyOf(afterBump), "$.code")).isEqualTo("UNAUTHENTICATED");
        assertThat(session.isInvalid()).as("授权版本变化必须作废已有会话").isTrue();

        Cookie freshCsrf = issueCsrfToken(null);
        MvcResult relogin = login(LOGIN_NAME, RAW_PASSWORD, freshCsrf, new MockHttpSession());
        assertThat(relogin.getResponse().getStatus()).isEqualTo(200);
        Integer authorizationVersion = jsonOf(bodyOf(relogin), "$.data.authorizationVersion");
        assertThat(authorizationVersion).isEqualTo(1);
    }

    @Test
    @DisplayName("登录轮换 CSRF 令牌：登录前的令牌对写请求失效（403），登录响应里下发的新令牌可用")
    void loginRotatesCsrfTokenSoThePreLoginTokenStopsWorking() throws Exception {
        Cookie beforeLogin = issueCsrfToken(null);
        MockHttpSession session = new MockHttpSession();

        // 登录请求自己带的就是登录前的令牌：它能通过，后面的 403 才能归因到"登录"这一步
        MvcResult login = login(LOGIN_NAME, RAW_PASSWORD, beforeLogin, session);
        assertThat(login.getResponse().getStatus()).isEqualTo(200);

        Cookie rotated = login.getResponse().getCookie(CSRF_COOKIE_NAME);
        assertThat(rotated).as("登录响应必须重新下发 %s Cookie", CSRF_COOKIE_NAME).isNotNull();
        assertThat(rotated.getValue()).isNotBlank().isNotEqualTo(beforeLogin.getValue());

        // 浏览器上的 Cookie 已经换成新的，而登录前被页面（或攻击者）拿到的旧令牌没有跟着变；
        // 本方案的 CSRF 是双提交校验（令牌不落服务端），因此失效发生在"旧令牌配不上新 Cookie"这一步。
        MvcResult staleToken = writeProbe("regr-auth-stale-csrf", rotated.getValue(),
                beforeLogin.getValue(), session);
        assertThat(staleToken.getResponse().getStatus()).isEqualTo(403);
        assertThat((String) jsonOf(bodyOf(staleToken), "$.code")).isEqualTo("FORBIDDEN");
        assertThat(session.isInvalid()).as("CSRF 拒绝不退出登录，会话应继续有效").isFalse();

        // 用登录后读到的新令牌重发同一请求：成功，说明上面的 403 来自令牌本身
        MvcResult freshToken = writeProbe("regr-auth-rotated-csrf", rotated.getValue(),
                rotated.getValue(), session);
        assertThat(freshToken.getResponse().getStatus()).isEqualTo(201);
        assertThat((String) jsonOf(bodyOf(freshToken), "$.data.note")).isEqualTo("regr-auth-rotated-csrf");
    }

    /** 按给定的令牌值发一次写请求，Cookie 与请求头分开控制。 */
    private MvcResult writeProbe(String note, String csrfCookieValue, String csrfHeaderValue,
                                 MockHttpSession session) throws Exception {
        return mockMvc.perform(post("/api/bootstrap/probes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(noteBody(note))
                .cookie(new Cookie(CSRF_COOKIE_NAME, csrfCookieValue))
                .header(CSRF_HEADER_NAME, csrfHeaderValue)
                .session(session)).andReturn();
    }

    @Test
    @DisplayName("退出后原会话访问受保护接口返回 401")
    void logoutInvalidatesSessionForProtectedEndpoint() throws Exception {
        Cookie csrf = issueCsrfToken(null);
        MockHttpSession session = new MockHttpSession();
        assertThat(login(LOGIN_NAME, RAW_PASSWORD, csrf, session).getResponse().getStatus()).isEqualTo(200);
        String sessionIdBeforeLogout = session.getId();

        MvcResult logout = mockMvc.perform(withCsrf(delete("/api/auth/session"), csrf).session(session))
                .andReturn();
        assertThat(logout.getResponse().getStatus()).isEqualTo(200);
        assertThat(session.isInvalid()).as("退出必须作废服务端会话").isTrue();

        // 客户端可能仍拿着旧会话标识再发请求（真实容器里这个会话已经不存在了）
        MockHttpSession replayOfOldSession = new MockHttpSession(null, sessionIdBeforeLogout);
        MvcResult afterLogout = mockMvc.perform(get("/api/bootstrap").session(replayOfOldSession)).andReturn();
        assertThat(afterLogout.getResponse().getStatus()).isEqualTo(401);
        assertThat((String) jsonOf(bodyOf(afterLogout), "$.code")).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    @DisplayName("登录成功会轮换会话 ID（会话固定防护），轮换后的会话仍是已认证会话")
    void loginRotatesSessionId() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Cookie csrf = issueCsrfToken(session);
        String sessionIdBeforeLogin = session.getId();
        assertThat(sessionIdBeforeLogin).isNotBlank();

        // 对照组：同一条会话上先发一个不改变认证状态的请求，ID 必须保持原样，
        // 这样后面的变化才能归因到登录本身，而不是 MockMvc 或请求次数
        mockMvc.perform(get("/api/auth/session").session(session)).andReturn();
        assertThat(session.getId()).isEqualTo(sessionIdBeforeLogin);

        MvcResult login = login(LOGIN_NAME, RAW_PASSWORD, csrf, session);
        assertThat(login.getResponse().getStatus()).isEqualTo(200);

        String sessionIdAfterLogin = session.getId();
        assertThat(sessionIdAfterLogin)
                .as("登录必须轮换会话 ID，否则攻击者预置的会话标识可直接复用")
                .isNotEqualTo(sessionIdBeforeLogin);

        MvcResult protectedCall = mockMvc.perform(get("/api/bootstrap").session(session)).andReturn();
        assertThat(protectedCall.getResponse().getStatus())
                .as("轮换后的会话必须仍是服务端认可的已认证会话")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("登录响应不含任何密码字段，也不含数据库中的密码哈希")
    void loginResponseDoesNotLeakPasswordOrHash() throws Exception {
        String passwordHash = passwordHashInDatabase();
        assertThat(passwordHash).as("库里只能存 BCrypt 哈希").startsWith("$2");

        Cookie csrf = issueCsrfToken(null);
        MvcResult login = login(LOGIN_NAME, RAW_PASSWORD, csrf, new MockHttpSession());
        assertThat(login.getResponse().getStatus()).isEqualTo(200);

        String body = bodyOf(login);
        assertThat(body)
                .as("响应体不得出现任何密码字段")
                .doesNotContain("password")
                .doesNotContain("Password");
        assertThat(body)
                .as("响应体不得回显密码或其哈希")
                .doesNotContain(passwordHash)
                .doesNotContain(RAW_PASSWORD);
    }

    private String noteBody(String note) {
        return objectMapper.writeValueAsString(Map.of("note", note));
    }
}
