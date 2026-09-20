package com.merine.rebuild.system.user.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.merine.rebuild.support.MockMvcRegressionSupport;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 用户管理回归测试的公共基类：真实 Spring 上下文 + 真实 MySQL 测试库 + MockMvc。
 *
 * 通用协议、会话和数据库隔离校验复用 MockMvcRegressionSupport；
 * 本类只保留用户管理 fixture、请求体与业务断言。每个用例重建本命名空间的合成数据，
 * 不用测试外层事务掩盖应用的真实提交与会话失效。
 *
 * 管理员角色编码用 {@link #ADMIN_ROLE_CODE} 覆盖 {@code merine.security.system-admin-role-codes}：
 * 判定依据仍是「登录时下发的 {@code ROLE_<角色编码>} authority 是否落在配置的集合里」，
 * 但把集合收在本用例的命名空间内，既能造出「最后一个管理员」这种必须可控的边界，
 * 也不会因为库里存在演示账号（例如 SYSTEM_ADMIN）而改变用例的预期结果。
 *
 * 非 ASCII 输入曾经会触发 MySQL 的 collation 冲突（1267）而变成 500：login_name / unit_code /
 * role_code 是 ascii_bin，绑定参数却是 utf8mb4。修复方式是关键字 LIKE 把列转成 utf8mb4、
 * 等值比较把参数转成 ascii。{@link #CHINESE_LOGIN}、{@link #DISABLED_LOGIN} 相关用例是这条
 * 修复的回归证据：非 ASCII 关键字要能匹配姓名，非 ASCII 的单位/角色编码要落空而不是报错。
 */
@TestPropertySource(properties =
        "merine.security.system-admin-role-codes=" + UserAdminRegressionSupport.ADMIN_ROLE_CODE)
abstract class UserAdminRegressionSupport extends MockMvcRegressionSupport {

    protected static final String USERS_PATH = "/api/system/users";

    /** 合成数据命名空间：清理与插入都只涉及带这些前缀的行。 */
    protected static final String LOGIN_NAME_PREFIX = "regr.user.";
    protected static final String UNIT_CODE_PREFIX = "REGR-UNIT-";
    protected static final String ROLE_CODE_PREFIX = "REGR-ROLE-";

    protected static final String UNIT_ALPHA = UNIT_CODE_PREFIX + "ALPHA";
    protected static final String UNIT_BETA = UNIT_CODE_PREFIX + "BETA";
    protected static final String UNIT_ALPHA_NAME = "回归单位甲";
    protected static final String UNIT_BETA_NAME = "回归单位乙";

    /** 允许调用用户管理接口的角色编码（覆盖配置），因此是本用例唯一可控的管理员角色。 */
    protected static final String ADMIN_ROLE_CODE = ROLE_CODE_PREFIX + "ADMIN";
    protected static final String ANALYST_ROLE_CODE = ROLE_CODE_PREFIX + "ANALYST";
    protected static final String EDITOR_ROLE_CODE = ROLE_CODE_PREFIX + "EDITOR";
    protected static final String ADMIN_ROLE_NAME = "回归用户管理员";
    protected static final String ANALYST_ROLE_NAME = "回归业务分析员";
    protected static final String EDITOR_ROLE_NAME = "回归编辑";

    protected static final String ADMIN_LOGIN = LOGIN_NAME_PREFIX + "admin";
    protected static final String ANALYST_LOGIN = LOGIN_NAME_PREFIX + "analyst";
    protected static final String EDITOR_LOGIN = LOGIN_NAME_PREFIX + "editor";
    /** 登录名里含下划线，用来验证关键字里的 _ 不会被当成单字符通配符。 */
    protected static final String UNDERSCORE_LOGIN = LOGIN_NAME_PREFIX + "under_score";
    /** 与上一条同形但把下划线换成普通字符：若 _ 被当成通配符，它会一起被搜出来。 */
    protected static final String UNDERSCORE_DECOY_LOGIN = LOGIN_NAME_PREFIX + "underXscore";
    /** 登录名里含百分号，用来验证关键字里的 % 不会被当成任意长度通配符。 */
    protected static final String PERCENT_LOGIN = LOGIN_NAME_PREFIX + "pct%one";
    protected static final String PERCENT_DECOY_LOGIN = LOGIN_NAME_PREFIX + "pctXXone";
    protected static final String DISABLED_LOGIN = LOGIN_NAME_PREFIX + "disabled";
    /** 登录名与姓名都不含非 ASCII 汉字以外的干扰：用来验证「关键字匹配姓名」。 */
    protected static final String NAMED_LOGIN = LOGIN_NAME_PREFIX + "named";
    protected static final String NAMED_DISPLAY = "Sample 回归账号";
    protected static final String NAME_KEYWORD = "Sample";
    /**
     * 姓名含中文词、登录名是纯 ASCII：用中文词查询时只可能命中这一行。
     * 这是「非 ASCII 关键字 + ascii_bin 列」的触发条件，修复前该查询直接 500。
     */
    protected static final String CHINESE_LOGIN = LOGIN_NAME_PREFIX + "chinese";
    protected static final String CHINESE_DISPLAY = "中文姓名检索样例";
    protected static final String CHINESE_NAME_KEYWORD = "中文姓名";

    /** 停用的单位与角色：新建或编辑用户指向它们时必须被拒绝。 */
    protected static final String DISABLED_UNIT_CODE = UNIT_CODE_PREFIX + "OFF";
    protected static final String DISABLED_UNIT_NAME = "回归停用单位";
    protected static final String DISABLED_ROLE_CODE = ROLE_CODE_PREFIX + "OFF";
    protected static final String DISABLED_ROLE_NAME = "回归停用角色";

    protected static final List<String> PAGE_LOGINS = List.of(
            LOGIN_NAME_PREFIX + "page1", LOGIN_NAME_PREFIX + "page2", LOGIN_NAME_PREFIX + "page3");
    /** 只命中 {@link #PAGE_LOGINS} 三个账号的关键字（按账号匹配）。 */
    protected static final String PAGE_LOGIN_KEYWORD = LOGIN_NAME_PREFIX + "page";

    protected static final String ADMIN_DISPLAY = "回归管理员";
    protected static final String ANALYST_DISPLAY = "回归业务分析员";
    protected static final String EDITOR_DISPLAY = "回归编辑";

    /** 所有合成账号共用的明文密码；库里只存它的 BCrypt 哈希。 */
    protected static final String RAW_PASSWORD = "regr-user-secret-1";

    /** 本次测试要插入的合成账号。 */
    private record Fixture(String loginName, String displayName, String unitCode,
                           List<String> roleCodes, String status) {
    }

    private static final List<Fixture> FIXTURES = List.of(
            new Fixture(ADMIN_LOGIN, ADMIN_DISPLAY, UNIT_ALPHA,
                    List.of(ADMIN_ROLE_CODE), "ENABLED"),
            new Fixture(ANALYST_LOGIN, ANALYST_DISPLAY, UNIT_ALPHA,
                    List.of(ANALYST_ROLE_CODE), "ENABLED"),
            // 两个角色：验证 roleCodes 与 roleNames 按 role_code 排序且下标一一对应
            new Fixture(EDITOR_LOGIN, EDITOR_DISPLAY, UNIT_BETA,
                    List.of(EDITOR_ROLE_CODE, ANALYST_ROLE_CODE), "ENABLED"),
            new Fixture(UNDERSCORE_LOGIN, "回归下划线样例", UNIT_BETA,
                    List.of(ANALYST_ROLE_CODE), "ENABLED"),
            new Fixture(UNDERSCORE_DECOY_LOGIN, "回归干扰样例甲", UNIT_BETA,
                    List.of(ANALYST_ROLE_CODE), "ENABLED"),
            new Fixture(PERCENT_LOGIN, "回归百分号样例", UNIT_BETA,
                    List.of(ANALYST_ROLE_CODE), "ENABLED"),
            new Fixture(PERCENT_DECOY_LOGIN, "回归干扰样例乙", UNIT_BETA,
                    List.of(ANALYST_ROLE_CODE), "ENABLED"),
            new Fixture(DISABLED_LOGIN, "回归停用样例", UNIT_ALPHA,
                    List.of(ANALYST_ROLE_CODE), "DISABLED"),
            new Fixture(NAMED_LOGIN, NAMED_DISPLAY, UNIT_ALPHA,
                    List.of(ANALYST_ROLE_CODE), "ENABLED"),
            new Fixture(CHINESE_LOGIN, CHINESE_DISPLAY, UNIT_ALPHA,
                    List.of(ANALYST_ROLE_CODE), "ENABLED"),
            new Fixture(PAGE_LOGINS.get(0), "回归分页样例一", UNIT_ALPHA,
                    List.of(ANALYST_ROLE_CODE), "ENABLED"),
            new Fixture(PAGE_LOGINS.get(1), "回归分页样例二", UNIT_ALPHA,
                    List.of(ANALYST_ROLE_CODE), "ENABLED"),
            new Fixture(PAGE_LOGINS.get(2), "回归分页样例三", UNIT_ALPHA,
                    List.of(ANALYST_ROLE_CODE), "ENABLED"));

    /** 命名空间里的合成账号总数，列表口径的预期来源。 */
    protected static final int FIXTURE_COUNT = FIXTURES.size();

    /** 本次测试插入的合成账号 id，按登录名索引。JUnit 每个测试方法新建实例，不会互相污染。 */
    private final Map<String, Long> userIds = new LinkedHashMap<>();

    @BeforeEach
    void resetSyntheticData() throws Exception {
        deleteSyntheticRows();
        insertSyntheticRows();
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
        // 单位测试可能临时造出父子和三级关系；先解除自引用，再按命名空间删除。
        jdbcTemplate.update("UPDATE sys_unit SET parent_id = NULL WHERE unit_code LIKE ?",
                UNIT_CODE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_unit WHERE unit_code LIKE ?", UNIT_CODE_PREFIX + "%");
    }

    /**
     * 插入本次测试的合成单位、角色与账号，并记下各位账号的 id。
     * 密码用应用自己的 {@link PasswordEncoder} 编码一次后复用：库里存的是 BCrypt 哈希。
     */
    private void insertSyntheticRows() {
        insertUnit(UNIT_ALPHA, UNIT_ALPHA_NAME, "ENABLED");
        insertUnit(UNIT_BETA, UNIT_BETA_NAME, "ENABLED");
        insertUnit(DISABLED_UNIT_CODE, DISABLED_UNIT_NAME, "DISABLED");
        insertRole(ADMIN_ROLE_CODE, ADMIN_ROLE_NAME, "ENABLED");
        insertRole(ANALYST_ROLE_CODE, ANALYST_ROLE_NAME, "ENABLED");
        insertRole(EDITOR_ROLE_CODE, EDITOR_ROLE_NAME, "ENABLED");
        insertRole(DISABLED_ROLE_CODE, DISABLED_ROLE_NAME, "DISABLED");

        String hash = passwordEncoder.encode(RAW_PASSWORD);
        for (Fixture fixture : FIXTURES) {
            jdbcTemplate.update("""
                    INSERT INTO sys_user (login_name, display_name, password_hash, unit_id, status)
                    VALUES (?, ?, ?, (SELECT id FROM sys_unit WHERE unit_code = ?), ?)
                    """, fixture.loginName(), fixture.displayName(), hash, fixture.unitCode(),
                    fixture.status());
            long insertedId = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE login_name = ?",
                    Long.class, fixture.loginName());
            userIds.put(fixture.loginName(), insertedId);
            for (String roleCode : fixture.roleCodes()) {
                jdbcTemplate.update("""
                        INSERT INTO sys_user_role (user_id, role_id)
                        SELECT ?, id FROM sys_role WHERE role_code = ?
                        """, insertedId, roleCode);
            }
        }
    }

    private void insertUnit(String unitCode, String unitName, String status) {
        jdbcTemplate.update("INSERT INTO sys_unit (unit_code, unit_name, status) VALUES (?, ?, ?)",
                unitCode, unitName, status);
    }

    private void insertRole(String roleCode, String roleName, String status) {
        jdbcTemplate.update("INSERT INTO sys_role (role_code, role_name, status) VALUES (?, ?, ?)",
                roleCode, roleName, status);
    }

    protected long userId(String loginName) {
        Long id = userIds.get(loginName);
        assertThat(id).as("合成账号 %s 必须在本次测试的种子数据里", loginName).isNotNull();
        return id;
    }

    /** 通过接口新建出来的账号：id 只能回库里取，不能靠种子数据。 */
    protected long userIdInDatabase(String loginName) {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE login_name = ?",
                Long.class, loginName);
        assertThat(id).as("账号 %s 必须先被写进库里", loginName).isNotNull();
        return id;
    }

    // ---- 库中真实状态：断言业务结果不够，还要断言写没写对 ----

    protected String statusInDatabase(String loginName) {
        return jdbcTemplate.queryForObject("SELECT status FROM sys_user WHERE login_name = ?",
                String.class, loginName);
    }

    protected String displayNameInDatabase(String loginName) {
        return jdbcTemplate.queryForObject("SELECT display_name FROM sys_user WHERE login_name = ?",
                String.class, loginName);
    }

    protected String passwordHashInDatabase(String loginName) {
        return jdbcTemplate.queryForObject("SELECT password_hash FROM sys_user WHERE login_name = ?",
                String.class, loginName);
    }

    protected String loginNameInDatabase(long userId) {
        return jdbcTemplate.queryForObject("SELECT login_name FROM sys_user WHERE id = ?",
                String.class, userId);
    }

    protected String unitCodeInDatabase(long userId) {
        return jdbcTemplate.queryForObject("""
                SELECT un.unit_code
                  FROM sys_user u
                  JOIN sys_unit un ON un.id = u.unit_id
                 WHERE u.id = ?
                """, String.class, userId);
    }

    protected int authorizationVersionInDatabase(String loginName) {
        return jdbcTemplate.queryForObject("SELECT authorization_version FROM sys_user WHERE login_name = ?",
                Integer.class, loginName);
    }

    protected LocalDateTime lastLoginAtInDatabase(String loginName) {
        return jdbcTemplate.queryForObject("SELECT last_login_at FROM sys_user WHERE login_name = ?",
                LocalDateTime.class, loginName);
    }

    protected List<String> roleCodesInDatabase(long userId) {
        return jdbcTemplate.queryForList("""
                SELECT r.role_code
                  FROM sys_user_role ur
                  JOIN sys_role r ON r.id = ur.role_id
                 WHERE ur.user_id = ?
                 ORDER BY r.role_code
                """, String.class, userId);
    }

    protected long countUsersWithLoginName(String loginName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE login_name = ?",
                Long.class, loginName);
    }

    /**
     * 改单位状态。本轮没有单位启停接口（UnitController 只有查询），
     * 因此「单位被停用」这一既有状态只能在库里直接造出来。
     */
    protected void setUnitStatus(String unitCode, String status) {
        int updated = jdbcTemplate.update("UPDATE sys_unit SET status = ? WHERE unit_code = ?",
                status, unitCode);
        assertThat(updated).as("合成单位 %s 必须存在", unitCode).isEqualTo(1);
    }

    /** 本次命名空间里的账号总数，用来证明「被拒绝的请求没有写出任何一行」。 */
    protected long countSyntheticUsers() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE login_name LIKE ?",
                Long.class, LOGIN_NAME_PREFIX + "%");
    }

    protected boolean userExists(long userId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE id = ?",
                Long.class, userId) > 0;
    }

    /**
     * 当前启用状态的管理员账号数（按配置的角色编码）。
     * 只作为前置条件使用：停用最后一个管理员的用例必须先确认库里确实只有一个。
     */
    protected long countEnabledAdmins() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT u.id)
                  FROM sys_user u
                  JOIN sys_user_role ur ON ur.user_id = u.id
                  JOIN sys_role r ON r.id = ur.role_id
                 WHERE u.status = 'ENABLED'
                   AND r.role_code = ?
                """, Long.class, ADMIN_ROLE_CODE);
    }

    // ---- 请求构造与执行 ----

    /** 带查询参数的列表请求；session 为 null 时不带会话（未登录场景）。 */
    protected MvcResult listUsers(MockHttpSession session, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get(USERS_PATH);
        for (int i = 0; i + 1 < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        if (session != null) {
            request = request.session(session);
        }
        return mockMvc.perform(request).andReturn();
    }

    /** 管理员会话：这是所有管理接口用例的调用方。 */
    protected MockHttpSession adminSession() throws Exception {
        return signIn(ADMIN_LOGIN, RAW_PASSWORD);
    }

    protected String createBody(String loginName, String displayName, String unitCode,
                                List<String> roleCodes, String password) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("loginName", loginName);
        body.put("displayName", displayName);
        body.put("unitCode", unitCode);
        body.put("roleCodes", roleCodes);
        body.put("password", password);
        return objectMapper.writeValueAsString(body);
    }

    /** 编辑请求体；newPassword 为 null 表示不改密码（字段省略，与前端一致）。 */
    protected String updateBody(String displayName, String unitCode, List<String> roleCodes,
                                String newPassword) throws Exception {
        return updateBody(displayName, unitCode, roleCodes, newPassword, 0);
    }

    protected String updateBody(String displayName, String unitCode, List<String> roleCodes,
                                String newPassword, int version) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", version);
        body.put("displayName", displayName);
        body.put("unitCode", unitCode);
        body.put("roleCodes", roleCodes);
        if (newPassword != null) {
            body.put("newPassword", newPassword);
        }
        return objectMapper.writeValueAsString(body);
    }

    protected String statusBody(long... ids) throws Exception {
        List<String> rawIds = Arrays.stream(ids).mapToObj(Long::toString).toList();
        return objectMapper.writeValueAsString(Map.of("userIds", rawIds));
    }

    /** 列表响应里的 items，用来断言真实返回的行而不是只断言数量。 */
    protected static List<Map<String, Object>> itemsOf(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).as("列表查询应成功").isEqualTo(200);
        return jsonOf(bodyOf(result), "$.data.items");
    }

    protected static List<String> loginNamesOf(MvcResult result) throws Exception {
        return itemsOf(result).stream().map(item -> (String) item.get("loginName")).toList();
    }

    protected static Map<String, Object> itemOf(List<Map<String, Object>> items, String loginName) {
        return items.stream()
                .filter(item -> loginName.equals(item.get("loginName")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("列表中缺少账号：" + loginName));
    }
}
