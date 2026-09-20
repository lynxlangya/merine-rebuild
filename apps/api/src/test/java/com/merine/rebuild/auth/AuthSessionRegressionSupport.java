package com.merine.rebuild.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.sql.Connection;
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
 * 认证与会话回归测试的公共基类：真实 Spring 上下文 + 真实 MySQL 测试库 + MockMvc。
 *
 * 这里刻意不替换任何 Bean（不用 H2、不用 mock Mapper）：要证明的是查询、密码校验、
 * 会话写入与失效判定在 MySQL 上的真实结果，mock 掉数据访问就只剩自证。
 *
 * 数据隔离分两层：
 * 1. profile 只从环境变量取测试库连接，且每个测试方法先核对实际连接的库名；
 * 2. 每个测试方法自己清理并插入带 {@value #LOGIN_NAME_PREFIX} 命名空间的合成行。
 * 不用 {@code @Transactional} 包住测试：会话与身份要跨多个 HTTP 请求写入，
 * 外层测试事务回滚盖不住这些写入，也会让应用读到未提交的数据。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class AuthSessionRegressionSupport {

    /** 与 SecurityConfig 的 CookieCsrfTokenRepository.withHttpOnlyFalse() 保持一致。 */
    protected static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    protected static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    /** 合成数据命名空间：清理与插入都只涉及带这些前缀的行，不触碰库里的其他数据。 */
    protected static final String LOGIN_NAME_PREFIX = "regr.auth.";
    protected static final String UNIT_CODE_PREFIX = "REGR-AUTH-";
    protected static final String ROLE_CODE_PREFIX = "REGR-AUTH-";

    protected static final String LOGIN_NAME = LOGIN_NAME_PREFIX + "analyst";
    protected static final String RAW_PASSWORD = "regr-auth-secret-1";
    protected static final String DISPLAY_NAME = "回归测试账号";
    protected static final String UNIT_NAME = "回归测试单位";
    protected static final String ROLE_NAME = "回归测试角色";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /** 本次测试插入的合成行主键。JUnit 每个测试方法新建实例，字段不会互相污染。 */
    protected long unitId;
    protected long roleId;
    protected long userId;

    @BeforeEach
    void resetSyntheticData() throws Exception {
        assertIsolatedTestDatabase();
        deleteSyntheticRows();
        insertSyntheticAccount();
    }

    /**
     * 兜底防线：profile 名字叫 test 不等于连的就是测试库。
     * 直接读实际连接元数据核对库名，不采信配置里写的是什么。
     */
    private void assertIsolatedTestDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL())
                    .as("破坏性测试只允许连隔离的测试库")
                    .contains("merine_rebuild_test");
            assertThat(connection.getCatalog())
                    .as("实际连接的默认库必须是测试库")
                    .isEqualTo("merine_rebuild_test");
        }
    }

    /** 删除顺序固定为 sys_user_role → sys_user → sys_role → sys_unit，避免撞外键。 */
    private void deleteSyntheticRows() {
        jdbcTemplate.update("""
                DELETE ur FROM sys_user_role ur
                 JOIN sys_user u ON u.id = ur.user_id
                WHERE u.login_name LIKE ?
                """, LOGIN_NAME_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE ur FROM sys_user_role ur
                 JOIN sys_role r ON r.id = ur.role_id
                WHERE r.role_code LIKE ?
                """, ROLE_CODE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_user WHERE login_name LIKE ?", LOGIN_NAME_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code LIKE ?", ROLE_CODE_PREFIX + "%");
        jdbcTemplate.update("UPDATE sys_unit SET parent_id = NULL WHERE unit_code LIKE ?",
                UNIT_CODE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_unit WHERE unit_code LIKE ?", UNIT_CODE_PREFIX + "%");
    }

    /**
     * 插入本次测试的合成账号：启用状态的单位、角色、账号与一条授予关系。
     * 密码用应用自己的 {@link PasswordEncoder} 编码，保证库里就是 BCrypt 哈希。
     */
    private void insertSyntheticAccount() {
        jdbcTemplate.update("INSERT INTO sys_unit (unit_code, unit_name, status) VALUES (?, ?, 'ENABLED')",
                UNIT_CODE_PREFIX + "UNIT", UNIT_NAME);
        unitId = jdbcTemplate.queryForObject("SELECT id FROM sys_unit WHERE unit_code = ?", Long.class,
                UNIT_CODE_PREFIX + "UNIT");

        jdbcTemplate.update("INSERT INTO sys_role (role_code, role_name, status) VALUES (?, ?, 'ENABLED')",
                ROLE_CODE_PREFIX + "ROLE", ROLE_NAME);
        roleId = jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE role_code = ?", Long.class,
                ROLE_CODE_PREFIX + "ROLE");

        jdbcTemplate.update("""
                INSERT INTO sys_user (login_name, display_name, password_hash, unit_id)
                VALUES (?, ?, ?, ?)
                """, LOGIN_NAME, DISPLAY_NAME, passwordEncoder.encode(RAW_PASSWORD), unitId);
        userId = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE login_name = ?", Long.class,
                LOGIN_NAME);

        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
    }

    protected void updateAccountStatus(String status) {
        jdbcTemplate.update("UPDATE sys_user SET status = ? WHERE id = ?", status, userId);
    }

    protected void bumpAuthorizationVersion() {
        jdbcTemplate.update("UPDATE sys_user SET authorization_version = authorization_version + 1 WHERE id = ?",
                userId);
    }

    protected String passwordHashInDatabase() {
        return jdbcTemplate.queryForObject("SELECT password_hash FROM sys_user WHERE id = ?", String.class,
                userId);
    }

    /**
     * GET /api/auth/csrf 触发令牌生成并写入 Cookie；返回 Cookie 供后续写请求按
     * Cookie 原始值放进 X-XSRF-TOKEN 请求头（SpaCsrfTokenRequestHandler 的分派规则）。
     */
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

    /** 携带会话与 CSRF 令牌的登录请求；session 为 null 时不带会话。 */
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

    protected String loginBody(String loginName, String password) {
        return objectMapper.writeValueAsString(Map.of("loginName", loginName, "password", password));
    }

    protected static MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, Cookie csrf) {
        return request.cookie(csrf).header(CSRF_HEADER_NAME, csrf.getValue());
    }

    protected static String bodyOf(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString();
    }

    /** 按 JSONPath 读响应字段；字段值为 null 时返回 null。 */
    protected static <T> T jsonOf(String body, String path) {
        return JsonPath.read(body, path);
    }
}
