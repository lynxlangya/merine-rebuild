package com.merine.rebuild.system.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.merine.rebuild.support.MockMvcRegressionSupport;
import com.merine.rebuild.system.security.PermissionCodes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 角色管理回归测试的公共基类：真实 Spring 上下文 + 真实 MySQL 测试库 + MockMvc。
 *
 * fixture 造出五种角色，覆盖本用例真正要区分的边界：
 * <ul>
 *   <li>{@link #BUILTIN_ROLE_CODE}：配置里的内置管理员角色，登录时展开为全部权限码；</li>
 *   <li>{@link #MANAGER_ROLE_CODE}：只有角色管理读写权限，用来证明判权看权限码而不是角色编码；</li>
 *   <li>{@link #READER_ROLE_CODE}：只有用户管理查看权限，能读角色选项但进不了角色管理；</li>
 *   <li>{@link #PLAIN_ROLE_CODE}：没有任何权限码；</li>
 *   <li>{@link #TARGET_ROLE_CODE}：被编辑/停用/删除的目标角色，初始带两个只读权限。</li>
 * </ul>
 *
 * 「还剩几个可用管理员」不在测试里复刻 SQL：{@link #canManageSystem(String)} 用真实登录 +
 * 调用角色管理接口判断，断言的是用户可观察的结果。
 */
@TestPropertySource(properties =
        "merine.security.system-admin-role-codes=" + RoleAdminRegressionSupport.BUILTIN_ROLE_CODE)
abstract class RoleAdminRegressionSupport extends MockMvcRegressionSupport {

    protected static final String ROLES_PATH = "/api/system/roles";
    protected static final String OPTIONS_PATH = ROLES_PATH + "/options";
    /** 角色编辑用的权限勾选树：菜单资源树，页面/页签/按钮节点带权限码。 */
    protected static final String PERMISSION_TREE_PATH = "/api/system/roles/permission-tree";
    protected static final String USERS_PATH = "/api/system/users";

    protected static final String CODE_PREFIX = "REGR-ROLEM-";
    protected static final String LOGIN_PREFIX = "regr.rolem.";
    protected static final String UNIT_CODE = "REGR-ROLEM-UNIT";
    protected static final String UNIT_NAME = "回归角色管理单位";

    protected static final String BUILTIN_ROLE_CODE = CODE_PREFIX + "ADMIN";
    protected static final String BUILTIN_ROLE_NAME = "回归内置管理员";
    protected static final String MANAGER_ROLE_CODE = CODE_PREFIX + "MANAGER";
    protected static final String MANAGER_ROLE_NAME = "回归角色管理员";
    protected static final String READER_ROLE_CODE = CODE_PREFIX + "READER";
    protected static final String READER_ROLE_NAME = "回归只读用户管理员";
    protected static final String PLAIN_ROLE_CODE = CODE_PREFIX + "PLAIN";
    protected static final String PLAIN_ROLE_NAME = "回归普通角色";

    /** 被管理的目标角色：初始带两个只读权限，测试可以改它、停它、删它。 */
    protected static final String TARGET_ROLE_CODE = CODE_PREFIX + "TARGET";
    protected static final String TARGET_ROLE_NAME = "回归目标角色";
    protected static final String TARGET_ROLE_DESCRIPTION = "回归用：只读查看用户与单位";
    protected static final List<String> TARGET_PERMISSIONS =
            List.of(PermissionCodes.USER_READ, PermissionCodes.UNIT_READ);

    protected static final String ADMIN_LOGIN = LOGIN_PREFIX + "admin";
    protected static final String MANAGER_LOGIN = LOGIN_PREFIX + "manager";
    protected static final String READER_LOGIN = LOGIN_PREFIX + "reader";
    protected static final String HOLDER_LOGIN = LOGIN_PREFIX + "holder";
    protected static final String HOLDER_DISPLAY = "回归目标角色持有者";
    protected static final String SECOND_ADMIN_LOGIN = LOGIN_PREFIX + "admin2";
    protected static final String SECOND_ADMIN_DISPLAY = "回归第二个管理员";

    /** 合成账号共用的明文密码；库里只存它的 BCrypt 哈希。 */
    protected static final String RAW_PASSWORD = "regr-role-secret-1";

    @BeforeEach
    void resetRoleFixture() {
        deleteRoleFixture();
        insertRoleFixture();
    }

    private void deleteRoleFixture() {
        jdbcTemplate.update("""
                DELETE ur FROM sys_user_role ur
                 JOIN sys_user u ON u.id = ur.user_id
                WHERE u.login_name LIKE ?
                """, LOGIN_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE ur FROM sys_user_role ur
                 JOIN sys_role r ON r.id = ur.role_id
                WHERE r.role_code LIKE ?
                """, CODE_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE rp FROM sys_role_permission rp
                 JOIN sys_role r ON r.id = rp.role_id
                WHERE r.role_code LIKE ?
                """, CODE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_user WHERE login_name LIKE ?", LOGIN_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code LIKE ?", CODE_PREFIX + "%");
        jdbcTemplate.update("UPDATE sys_unit SET parent_id = NULL WHERE unit_code = ?", UNIT_CODE);
        jdbcTemplate.update("DELETE FROM sys_unit WHERE unit_code = ?", UNIT_CODE);
    }

    private void insertRoleFixture() {
        jdbcTemplate.update("""
                INSERT INTO sys_unit (unit_code, unit_name, status) VALUES (?, ?, 'ENABLED')
                """, UNIT_CODE, UNIT_NAME);

        // 内置角色不写 sys_role_permission：它在登录时展开为全部权限码，这也正是被测行为
        insertRole(BUILTIN_ROLE_CODE, BUILTIN_ROLE_NAME, List.of());
        insertRole(MANAGER_ROLE_CODE, MANAGER_ROLE_NAME,
                List.of(PermissionCodes.ROLE_READ, PermissionCodes.ROLE_CREATE,
                        PermissionCodes.ROLE_UPDATE, PermissionCodes.ROLE_TOGGLE_STATUS,
                        PermissionCodes.ROLE_DELETE));
        insertRole(READER_ROLE_CODE, READER_ROLE_NAME, List.of(PermissionCodes.USER_READ));
        insertRole(PLAIN_ROLE_CODE, PLAIN_ROLE_NAME, List.of());
        insertRole(TARGET_ROLE_CODE, TARGET_ROLE_NAME, TARGET_PERMISSIONS);
        jdbcTemplate.update("UPDATE sys_role SET description = ? WHERE role_code = ?",
                TARGET_ROLE_DESCRIPTION, TARGET_ROLE_CODE);

        insertUser(ADMIN_LOGIN, "回归角色管理员甲", List.of(BUILTIN_ROLE_CODE));
        insertUser(MANAGER_LOGIN, "回归角色管理员乙", List.of(MANAGER_ROLE_CODE));
        insertUser(READER_LOGIN, "回归只读用户管理员", List.of(READER_ROLE_CODE));
        insertUser(HOLDER_LOGIN, HOLDER_DISPLAY, List.of(TARGET_ROLE_CODE, PLAIN_ROLE_CODE));
    }

    private void insertRole(String roleCode, String roleName, List<String> permissionCodes) {
        jdbcTemplate.update("""
                INSERT INTO sys_role (role_code, role_name, status) VALUES (?, ?, 'ENABLED')
                """, roleCode, roleName);
        for (String permissionCode : permissionCodes) {
            jdbcTemplate.update("""
                    INSERT INTO sys_role_permission (role_id, permission_id)
                    SELECT r.id, p.id
                      FROM sys_role r, sys_permission p
                     WHERE r.role_code = ? AND p.permission_code = ?
                    """, roleCode, permissionCode);
        }
    }

    private void insertUser(String loginName, String displayName, List<String> roleCodes) {
        jdbcTemplate.update("""
                INSERT INTO sys_user (login_name, display_name, password_hash, unit_id)
                VALUES (?, ?, ?, (SELECT id FROM sys_unit WHERE unit_code = ?))
                """, loginName, displayName, passwordEncoder.encode(RAW_PASSWORD), UNIT_CODE);
        for (String roleCode : roleCodes) {
            jdbcTemplate.update("""
                    INSERT INTO sys_user_role (user_id, role_id)
                    SELECT u.id, r.id
                      FROM sys_user u, sys_role r
                     WHERE u.login_name = ? AND r.role_code = ?
                    """, loginName, roleCode);
        }
    }

    // ---- 会话与请求 ----

    protected MockHttpSession adminSession() throws Exception {
        return signIn(ADMIN_LOGIN, RAW_PASSWORD);
    }

    protected MockHttpSession managerSession() throws Exception {
        return signIn(MANAGER_LOGIN, RAW_PASSWORD);
    }

    protected MockHttpSession readerSession() throws Exception {
        return signIn(READER_LOGIN, RAW_PASSWORD);
    }

    protected MockHttpSession holderSession() throws Exception {
        return signIn(HOLDER_LOGIN, RAW_PASSWORD);
    }

    /**
     * 用真实登录 + 一个只会失败在业务校验上的写请求，判断这个账号现在还能不能管理系统。
     *
     * 授权通过时服务层对不存在的角色返回 404，没有权限时是 403；因此「还能不能管理」
     * 由可观察结果回答，而不是在测试里复刻一份覆盖 SQL。
     */
    protected boolean canManageRoles(String loginName) throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult login = login(loginName, RAW_PASSWORD, issueCsrfToken(null), session);
        if (login.getResponse().getStatus() != 200) {
            return false;
        }
        // 探针用「编辑角色」这个动作：它要求的正是管理底线里的 system:role:update，
        // 授权通过时会因为角色不存在返回 404，没有权限时是 403。
        MvcResult probe = sendJson(put(ROLES_PATH + "/" + CODE_PREFIX + "PROBE-NOT-EXIST")
                .content(updateRoleBody(0, "探针角色", null, List.of())), session);
        int status = probe.getResponse().getStatus();
        assertThat(status).as("授权探针只能返回 404（授权通过）或 403（无权限）").isIn(403, 404);
        return status == 404;
    }

    protected MvcResult listRoles(MockHttpSession session, String... params) throws Exception {
        return getWithParams(ROLES_PATH, session, params);
    }

    protected MvcResult roleMembers(String roleCode, MockHttpSession session, String... params)
            throws Exception {
        return getWithParams(ROLES_PATH + "/" + roleCode + "/members", session, params);
    }

    private MvcResult getWithParams(String path, MockHttpSession session, String... params)
            throws Exception {
        MockHttpServletRequestBuilder request = get(path);
        for (int i = 0; i + 1 < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        if (session != null) {
            request = request.session(session);
        }
        return mockMvc.perform(request).andReturn();
    }

    protected String createRoleBody(String code, String name, String description,
                                    List<String> permissionCodes) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("name", name);
        body.put("description", description);
        body.put("permissionCodes", permissionCodes);
        return objectMapper.writeValueAsString(body);
    }

    protected String updateRoleBody(int version, String name, String description,
                                    List<String> permissionCodes) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", version);
        body.put("name", name);
        body.put("description", description);
        body.put("permissionCodes", permissionCodes);
        return objectMapper.writeValueAsString(body);
    }

    protected String roleStatusBody(String... roleCodes) throws Exception {
        return objectMapper.writeValueAsString(Map.of("roleCodes", List.of(roleCodes)));
    }

    /** 用接口新建第二个内置管理员：并发与管理底线用例需要可控的第二个覆盖账号。 */
    protected long createSecondAdmin(MockHttpSession admin) throws Exception {
        MvcResult created = sendJson(
                post(USERS_PATH).content(secondAdminBody()),
                admin);
        assertThat(created.getResponse().getStatus()).as("先造出第二个内置管理员").isEqualTo(201);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE login_name = ?", Long.class, SECOND_ADMIN_LOGIN);
    }

    private String secondAdminBody() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("loginName", SECOND_ADMIN_LOGIN);
        body.put("displayName", SECOND_ADMIN_DISPLAY);
        body.put("unitCode", UNIT_CODE);
        body.put("roleCodes", List.of(BUILTIN_ROLE_CODE));
        body.put("password", RAW_PASSWORD);
        return objectMapper.writeValueAsString(body);
    }

    // ---- 库中真实状态 ----

    protected String roleStatusInDatabase(String roleCode) {
        return jdbcTemplate.queryForObject(
                """
                SELECT status FROM sys_role
                 WHERE CONVERT(role_code USING utf8mb4) COLLATE utf8mb4_0900_ai_ci = ?
                """, String.class, roleCode);
    }

    protected int roleVersionInDatabase(String roleCode) {
        return jdbcTemplate.queryForObject(
                """
                SELECT version FROM sys_role
                 WHERE CONVERT(role_code USING utf8mb4) COLLATE utf8mb4_0900_ai_ci = ?
                """, Integer.class, roleCode);
    }

    protected String roleNameInDatabase(String roleCode) {
        return jdbcTemplate.queryForObject(
                """
                SELECT role_name FROM sys_role
                 WHERE CONVERT(role_code USING utf8mb4) COLLATE utf8mb4_0900_ai_ci = ?
                """, String.class, roleCode);
    }

    protected String roleDescriptionInDatabase(String roleCode) {
        return jdbcTemplate.queryForObject(
                """
                SELECT description FROM sys_role
                 WHERE CONVERT(role_code USING utf8mb4) COLLATE utf8mb4_0900_ai_ci = ?
                """, String.class, roleCode);
    }

    protected List<String> rolePermissionCodesInDatabase(String roleCode) {
        return jdbcTemplate.queryForList("""
                SELECT p.permission_code
                  FROM sys_role_permission rp
                  JOIN sys_role r ON r.id = rp.role_id
                  JOIN sys_permission p ON p.id = rp.permission_id
                 WHERE CONVERT(r.role_code USING utf8mb4) COLLATE utf8mb4_0900_ai_ci = ?
                 ORDER BY p.permission_code
                """, String.class, roleCode);
    }

    protected long roleHolderCountInDatabase(String roleCode) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM sys_user_role ur
                  JOIN sys_role r ON r.id = ur.role_id
                 WHERE CONVERT(r.role_code USING utf8mb4) COLLATE utf8mb4_0900_ai_ci = ?
                """, Long.class, roleCode);
    }

    protected boolean roleExists(String roleCode) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM sys_role
                 WHERE CONVERT(role_code USING utf8mb4) COLLATE utf8mb4_0900_ai_ci = ?
                """, Long.class, roleCode) > 0;
    }

    protected int authorizationVersionInDatabase(String loginName) {
        return jdbcTemplate.queryForObject(
                "SELECT authorization_version FROM sys_user WHERE login_name = ?",
                Integer.class, loginName);
    }

    protected static List<Map<String, Object>> itemsOf(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).as("列表查询应成功").isEqualTo(200);
        return jsonOf(bodyOf(result), "$.data.items");
    }

    protected static Map<String, Object> itemOf(List<Map<String, Object>> items, String roleCode) {
        return items.stream()
                .filter(item -> roleCode.equals(item.get("code")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("列表中缺少角色：" + roleCode));
    }
}
