package com.merine.rebuild.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

/**
 * 真实 Spring + 隔离 MySQL + MockMvc 的通用测试基座。
 *
 * 这里只放跨模块复用的协议、会话、CSRF 和 JSON 断言；
 * 业务 fixture 放在各自模块的 RegressionSupport 中，不把用户数据带进无关测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class MockMvcRegressionSupport {
    protected static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    protected static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    protected final void verifyIsolatedDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL())
                    .as("破坏性测试只允许连隔离的测试库")
                    .contains("merine_rebuild_test");
            assertThat(connection.getCatalog())
                    .as("实际连接的默认库必须是测试库")
                    .isEqualTo("merine_rebuild_test");
        }
    }

    /** GET /api/auth/csrf 触发令牌生成并写入 Cookie。 */
    protected Cookie issueCsrfToken(MockHttpSession session) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/auth/csrf");
        if (session != null) {
            request = request.session(session);
        }
        MvcResult result = mockMvc.perform(request).andReturn();
        assertThat(result.getResponse().getStatus()).as("获取 CSRF 令牌").isEqualTo(200);
        Cookie cookie = result.getResponse().getCookie(CSRF_COOKIE_NAME);
        assertThat(cookie).as("GET /api/auth/csrf 必须下发 %s Cookie", CSRF_COOKIE_NAME).isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        return cookie;
    }

    protected MvcResult getJson(String path, MockHttpSession session) throws Exception {
        MockHttpServletRequestBuilder request = get(path);
        if (session != null) {
            request = request.session(session);
        }
        return mockMvc.perform(request).andReturn();
    }

    protected MvcResult sendJson(MockHttpServletRequestBuilder request, MockHttpSession session)
            throws Exception {
        Cookie csrf = issueCsrfToken(null);
        MockHttpServletRequestBuilder withToken =
                withCsrf(request.contentType(MediaType.APPLICATION_JSON), csrf);
        if (session != null) {
            withToken = withToken.session(session);
        }
        return mockMvc.perform(withToken).andReturn();
    }

    protected MvcResult login(String loginName, String password, Cookie csrf, MockHttpSession session)
            throws Exception {
        MockHttpServletRequestBuilder request = withCsrf(post("/api/auth/session")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(loginName, password)), csrf);
        if (session != null) {
            request = request.session(session);
        }
        return mockMvc.perform(request).andReturn();
    }

    protected MockHttpSession signIn(String loginName, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult result = login(loginName, password, issueCsrfToken(null), session);
        assertThat(result.getResponse().getStatus()).as("合成账号 %s 必须能登录", loginName).isEqualTo(200);
        return session;
    }

    protected MvcResult currentSession(MockHttpSession session) throws Exception {
        return mockMvc.perform(get("/api/auth/session").session(session)).andReturn();
    }

    protected String loginBody(String loginName, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("loginName", loginName, "password", password));
    }

    protected static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request,
                                                            Cookie csrf) {
        return request.cookie(csrf).header(CSRF_HEADER_NAME, csrf.getValue());
    }

    protected static String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    protected static <T> T jsonOf(String body, String path) {
        return JsonPath.read(body, path);
    }

    protected static int intOf(String body, String path) {
        Number value = JsonPath.read(body, path);
        return value.intValue();
    }

    protected static void assertUnauthenticatedJson(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(result.getResponse().getContentType())
                .as("未登录响应必须是 JSON，不能是登录页 HTML")
                .startsWith(MediaType.APPLICATION_JSON_VALUE);
        String body = bodyOf(result);
        assertThat(body.strip())
                .as("响应体必须是 JSON 对象")
                .startsWith("{")
                .doesNotContain("<html")
                .doesNotContain("<!DOCTYPE");
        assertThat((String) jsonOf(body, "$.code")).isEqualTo("UNAUTHENTICATED");
        assertThat((Object) jsonOf(body, "$.data")).isNull();
        String requestId = jsonOf(body, "$.requestId");
        assertThat(requestId).isNotBlank();
        assertThat(result.getResponse().getHeader("X-Request-Id"))
                .as("响应头与响应体的请求编号必须一致")
                .isEqualTo(requestId);
    }

    protected static void assertForbidden(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
        assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        String body = bodyOf(result);
        assertThat((String) jsonOf(body, "$.code")).isEqualTo("FORBIDDEN");
        assertThat((String) jsonOf(body, "$.requestId")).isNotBlank();
    }

    protected static void assertError(MvcResult result, int status, String code) throws Exception {
        assertThat(result.getResponse().getStatus())
                .as("预期 %d %s，实际响应体：%s", status, code, bodyOf(result))
                .isEqualTo(status);
        String body = bodyOf(result);
        assertThat((String) jsonOf(body, "$.code")).isEqualTo(code);
        assertThat((String) jsonOf(body, "$.requestId")).isNotBlank();
        assertThat((Object) jsonOf(body, "$.data")).isNull();
    }

    protected static void assertValidationError(MvcResult result, String... expectedFields)
            throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        String body = bodyOf(result);
        assertThat((String) jsonOf(body, "$.code")).isEqualTo("VALIDATION_ERROR");
        List<String> fields = jsonOf(body, "$.fieldErrors[*].field");
        assertThat(fields).containsExactlyInAnyOrder(expectedFields);
    }
}
