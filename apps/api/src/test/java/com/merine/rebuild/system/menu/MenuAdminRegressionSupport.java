package com.merine.rebuild.system.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.merine.rebuild.support.MockMvcRegressionSupport;
import com.merine.rebuild.system.security.PermissionCodes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 菜单与按钮级权限回归的公共基类：真实 Spring + 真实 MySQL 测试库 + MockMvc。
 *
 * fixture 造出三种账号，覆盖「能看不能写」「只能新建」这两类边界：
 * <ul>
 *   <li>内置管理员（配置里的角色，登录时展开为全部权限码）；</li>
 *   <li>{@link #READER_LOGIN}：只有 system:user:read，能读用户、能拿导航，不能改任何东西；</li>
 *   <li>{@link #WRITER_LOGIN}：另有 system:user:create，可以新建用户但不能编辑、不能重置密码。</li>
 * </ul>
 * 测试自建的菜单节点统一用 {@value #CODE_PREFIX} 权限码前缀与「回归-」名称前缀，
 * 每个用例前按命名空间清理，绝不碰迁移写入的引导菜单。
 */
@TestPropertySource(properties =
        "merine.security.system-admin-role-codes=" + MenuAdminRegressionSupport.BUILTIN_ROLE_CODE)
abstract class MenuAdminRegressionSupport extends MockMvcRegressionSupport {

    protected static final String MENUS_PATH = "/api/system/menus";
    protected static final String MY_MENUS_PATH = "/api/me/menus";
    protected static final String USERS_PATH = "/api/system/users";
    protected static final String ROLES_PATH = "/api/system/roles";

    protected static final String CODE_PREFIX = "regr:menu:";
    protected static final String NAME_PREFIX = "回归-";
    protected static final String LOGIN_PREFIX = "regr.menu.";
    protected static final String UNIT_CODE = "REGR-MENU-UNIT";
    protected static final String UNIT_NAME = "回归菜单单位";

    protected static final String BUILTIN_ROLE_CODE = "REGR-MENU-ADMIN";
    protected static final String READER_ROLE_CODE = "REGR-MENU-READER";
    protected static final String WRITER_ROLE_CODE = "REGR-MENU-WRITER";

    protected static final String ADMIN_LOGIN = LOGIN_PREFIX + "admin";
    protected static final String READER_LOGIN = LOGIN_PREFIX + "reader";
    protected static final String WRITER_LOGIN = LOGIN_PREFIX + "writer";
    protected static final String RAW_PASSWORD = "regr-menu-secret-1";

    /** 用例自建目录：所有测试节点都挂在它下面，便于清理。 */
    protected static final String TEST_DIRECTORY_NAME = NAME_PREFIX + "验收目录";

    @BeforeEach
    void resetMenuFixture() {
        deleteTestMenus();
        deleteFixtureRows();
        insertFixtureRows();
    }

    /** 用例结束后也要清掉自建节点：权限字典一致性回归断言的是「迁移写入的那份清单」。 */
    @AfterEach
    void cleanTestMenus() {
        deleteTestMenus();
    }

    /** 反复删除「已经没有子节点」的测试节点，直到整棵测试子树清空（父节点引用要求从叶子删起）。 */
    private void deleteTestMenus() {
        jdbcTemplate.update("""
                DELETE rp FROM sys_role_permission rp
                 JOIN sys_permission p ON p.id = rp.permission_id
                WHERE p.permission_code LIKE ?
                """, CODE_PREFIX + "%");
        int deleted;
        do {
            deleted = jdbcTemplate.update("""
                    DELETE m FROM sys_menu m
                     WHERE (m.menu_name LIKE ? OR m.route_key LIKE ?)
                       AND NOT EXISTS (SELECT 1
                                         FROM (SELECT parent_id FROM sys_menu) AS child
                                        WHERE child.parent_id = m.id)
                    """, NAME_PREFIX + "%", "regr.%");
        } while (deleted > 0);
        jdbcTemplate.update("DELETE FROM sys_permission WHERE permission_code LIKE ?",
                CODE_PREFIX + "%");
        // 用例可能把引导页面停用过头：恢复到启用，保证用例之间互不干扰
        jdbcTemplate.update("UPDATE sys_menu SET status = 'ENABLED' WHERE status = 'DISABLED'");
        // 用例可能挪动引导页面：按名称恢复到各自目录下（目录本身是迁移写入的固定名称）
        jdbcTemplate.update("""
                UPDATE sys_menu page
                  JOIN (SELECT id FROM sys_menu
                         WHERE menu_name = '系统管理' AND menu_type = 'DIRECTORY') AS sys_dir
                    SET page.parent_id = sys_dir.id
                 WHERE page.route_key IN ('system.users', 'system.roles', 'system.menus',
                                          'system.units')
                """);
        jdbcTemplate.update("""
                UPDATE sys_menu page
                  JOIN (SELECT id FROM sys_menu
                         WHERE menu_name = '开发工具' AND menu_type = 'DIRECTORY') AS dev_dir
                    SET page.parent_id = dev_dir.id
                 WHERE page.route_key = 'dev.diagnostics'
                """);
    }

    private void deleteFixtureRows() {
        jdbcTemplate.update("""
                DELETE ur FROM sys_user_role ur
                 JOIN sys_user u ON u.id = ur.user_id
                WHERE u.login_name LIKE ?
                """, LOGIN_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE rp FROM sys_role_permission rp
                 JOIN sys_role r ON r.id = rp.role_id
                WHERE r.role_code LIKE 'REGR-MENU-%'
                """);
        jdbcTemplate.update("DELETE FROM sys_user WHERE login_name LIKE ?", LOGIN_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code LIKE 'REGR-MENU-%'");
        jdbcTemplate.update("UPDATE sys_unit SET parent_id = NULL WHERE unit_code = ?", UNIT_CODE);
        jdbcTemplate.update("DELETE FROM sys_unit WHERE unit_code = ?", UNIT_CODE);
    }

    private void insertFixtureRows() {
        jdbcTemplate.update("""
                INSERT INTO sys_unit (unit_code, unit_name, status) VALUES (?, ?, 'ENABLED')
                """, UNIT_CODE, UNIT_NAME);
        insertRole(BUILTIN_ROLE_CODE, "回归菜单管理员", List.of());
        insertRole(READER_ROLE_CODE, "回归只读用户管理员", List.of(PermissionCodes.USER_READ));
        insertRole(WRITER_ROLE_CODE, "回归用户新建员",
                List.of(PermissionCodes.USER_READ, PermissionCodes.USER_CREATE));
        insertUser(ADMIN_LOGIN, "回归菜单管理员", BUILTIN_ROLE_CODE);
        insertUser(READER_LOGIN, "回归只读账号", READER_ROLE_CODE);
        insertUser(WRITER_LOGIN, "回归新建账号", WRITER_ROLE_CODE);
    }

    private void insertRole(String roleCode, String roleName, List<String> permissionCodes) {
        jdbcTemplate.update("""
                INSERT INTO sys_role (role_code, role_name, status) VALUES (?, ?, 'ENABLED')
                """, roleCode, roleName);
        for (String permissionCode : permissionCodes) {
            jdbcTemplate.update("""
                    INSERT INTO sys_role_permission (role_id, permission_id)
                    SELECT r.id, p.id FROM sys_role r, sys_permission p
                     WHERE r.role_code = ? AND p.permission_code = ?
                    """, roleCode, permissionCode);
        }
    }

    private void insertUser(String loginName, String displayName, String roleCode) {
        jdbcTemplate.update("""
                INSERT INTO sys_user (login_name, display_name, password_hash, unit_id)
                VALUES (?, ?, ?, (SELECT id FROM sys_unit WHERE unit_code = ?))
                """, loginName, displayName, passwordEncoder.encode(RAW_PASSWORD), UNIT_CODE);
        jdbcTemplate.update("""
                INSERT INTO sys_user_role (user_id, role_id)
                SELECT u.id, r.id FROM sys_user u, sys_role r
                 WHERE u.login_name = ? AND r.role_code = ?
                """, loginName, roleCode);
    }

    // ---- 会话与请求 ----

    protected MockHttpSession adminSession() throws Exception {
        return signIn(ADMIN_LOGIN, RAW_PASSWORD);
    }

    protected MockHttpSession readerSession() throws Exception {
        return signIn(READER_LOGIN, RAW_PASSWORD);
    }

    protected MockHttpSession writerSession() throws Exception {
        return signIn(WRITER_LOGIN, RAW_PASSWORD);
    }

    protected MvcResult getMenus(MockHttpSession session) throws Exception {
        return getWithSession(get(MENUS_PATH), session);
    }

    protected MvcResult getMyMenus(MockHttpSession session) throws Exception {
        return getWithSession(get(MY_MENUS_PATH), session);
    }

    protected MvcResult getWithSession(MockHttpServletRequestBuilder request, MockHttpSession session)
            throws Exception {
        if (session != null) {
            request = request.session(session);
        }
        return mockMvc.perform(request).andReturn();
    }

    protected String createMenuBody(String parentId, String type, String name, String routeKey,
                                    String permissionCode, Integer sortOrder) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("parentId", parentId);
        body.put("type", type);
        body.put("name", name);
        body.put("routeKey", routeKey);
        body.put("permissionCode", permissionCode);
        body.put("description", "回归用节点");
        body.put("sortOrder", sortOrder);
        return objectMapper.writeValueAsString(body);
    }

    protected String updateMenuBody(int version, String parentId, String name, String routeKey,
                                    int sortOrder, String status) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", version);
        body.put("parentId", parentId);
        body.put("name", name);
        body.put("routeKey", routeKey);
        body.put("description", "回归用节点");
        body.put("sortOrder", sortOrder);
        body.put("status", status);
        return objectMapper.writeValueAsString(body);
    }

    protected MvcResult deleteMenu(String id, int version, MockHttpSession session) throws Exception {
        return sendJson(delete(MENUS_PATH + "/" + id + "?version=" + version), session);
    }

    protected MvcResult updateMenu(String id, int version, String parentId, String name,
                                   String routeKey, int sortOrder, String status,
                                   MockHttpSession session) throws Exception {
        return sendJson(put(MENUS_PATH + "/" + id).content(
                updateMenuBody(version, parentId, name, routeKey, sortOrder, status)), session);
    }

    protected MvcResult createMenu(String parentId, String type, String name, String routeKey,
                                   String permissionCode, Integer sortOrder, MockHttpSession session)
            throws Exception {
        return sendJson(post(MENUS_PATH).content(
                createMenuBody(parentId, type, name, routeKey, permissionCode, sortOrder)), session);
    }

    // ---- 库中真实状态与树查询 ----

    /** 在返回的菜单树里按名称找到节点（返回扁平后的 Map，children 仍在）。 */
    @SuppressWarnings("unchecked")
    protected static Map<String, Object> findNode(MvcResult tree, String name) throws Exception {
        assertThat(tree.getResponse().getStatus()).as("菜单树查询应成功").isEqualTo(200);
        List<Map<String, Object>> nodes = jsonOf(bodyOf(tree), "$.data");
        return flatten(nodes).stream()
                .filter(node -> name.equals(node.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("菜单树里缺少节点：" + name));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> flatten(List<Map<String, Object>> nodes) {
        List<Map<String, Object>> all = new java.util.ArrayList<>();
        for (Map<String, Object> node : nodes) {
            all.add(node);
            all.addAll(flatten((List<Map<String, Object>>) node.getOrDefault("children", List.of())));
        }
        return all;
    }

    protected static int versionOf(Map<String, Object> node) {
        return ((Number) node.get("version")).intValue();
    }

    protected static String idOf(Map<String, Object> node) {
        return (String) node.get("id");
    }

    protected boolean permissionExists(String code) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_permission WHERE permission_code = ?", Long.class, code) > 0;
    }

    protected boolean menuNamed(String name) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE menu_name = ?", Long.class, name) > 0;
    }

    protected long grantsOf(String permissionCode) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM sys_role_permission rp
                  JOIN sys_permission p ON p.id = rp.permission_id
                 WHERE p.permission_code = ?
                """, Long.class, permissionCode);
    }

    protected int authorizationVersionInDatabase(String loginName) {
        return jdbcTemplate.queryForObject(
                "SELECT authorization_version FROM sys_user WHERE login_name = ?",
                Integer.class, loginName);
    }
}
