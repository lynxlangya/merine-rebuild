package com.merine.rebuild.system.dictionary;

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
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 字典回归测试的公共基类：真实 Spring + 真实 MySQL 测试库 + MockMvc。
 *
 * fixture 造出三种账号，覆盖本用例真正要区分的边界：
 * <ul>
 *   <li>内置管理员（配置里的角色，登录时展开为全部权限码）；</li>
 *   <li>{@link #READER_LOGIN}：只有 system:dict:read，能看管理页但不能改；</li>
 *   <li>{@link #PLAIN_LOGIN}：没有任何字典权限，仍能读使用侧字典接口（登录即可）。</li>
 * </ul>
 * 测试自建的字典统一用 {@value #CODE_PREFIX} 前缀，每个用例前按命名空间清理；
 * 三本系统枚举（状态、菜单类型、单位层级）是普通字典数据，用例可以随便改，
 * 因此每个用例前把它们恢复成迁移写入的基线，避免用例之间互相污染。
 */
@TestPropertySource(properties =
        "merine.security.system-admin-role-codes=" + DictionaryRegressionSupport.BUILTIN_ROLE_CODE)
abstract class DictionaryRegressionSupport extends MockMvcRegressionSupport {

    protected static final String READ_PATH = "/api/dictionaries";
    protected static final String ADMIN_PATH = "/api/system/dictionaries";
    protected static final String USERS_PATH = "/api/system/users";

    protected static final String CODE_PREFIX = "regr.";
    protected static final String LOGIN_PREFIX = "regr.dict.";
    protected static final String UNIT_CODE = "REGR-DICT-UNIT";
    protected static final String UNIT_NAME = "回归字典单位";

    protected static final String BUILTIN_ROLE_CODE = "REGR-DICT-ADMIN";
    protected static final String READER_ROLE_CODE = "REGR-DICT-READER";
    protected static final String PLAIN_ROLE_CODE = "REGR-DICT-PLAIN";

    protected static final String ADMIN_LOGIN = LOGIN_PREFIX + "admin";
    protected static final String READER_LOGIN = LOGIN_PREFIX + "reader";
    protected static final String PLAIN_LOGIN = LOGIN_PREFIX + "plain";
    protected static final String RAW_PASSWORD = "regr-dict-secret-1";

    protected static final String DEMO_CODE = CODE_PREFIX + "demo";

    /** 三本系统枚举：与 V14 引导数据同名，用例读写它们之前先由 fixture 恢复基线。 */
    protected static final String STATUS_CODE = "common.status";
    protected static final String MENU_TYPE_CODE = "system.menu.type";
    protected static final String UNIT_LEVEL_CODE = "system.unit.level";

    /** 用于证明「编码前缀不再有保留概念」的探针字典；故意不放在 {@value #CODE_PREFIX} 命名空间里。 */
    protected static final String NAMESPACE_PROBE_CODE = "system.custom";

    @BeforeEach
    void resetDictionaryFixture() {
        deleteFixtureRows();
        restoreSystemDictionaries();
        insertFixtureRows();
    }

    private void deleteFixtureRows() {
        jdbcTemplate.update("""
                DELETE i FROM sys_dict_item i
                 JOIN sys_dict_type t ON t.id = i.dict_type_id
                WHERE t.dict_code LIKE ?
                """, CODE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_dict_type WHERE dict_code LIKE ?", CODE_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE ur FROM sys_user_role ur
                 JOIN sys_user u ON u.id = ur.user_id
                WHERE u.login_name LIKE ?
                """, LOGIN_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE rp FROM sys_role_permission rp
                 JOIN sys_role r ON r.id = rp.role_id
                WHERE r.role_code LIKE 'REGR-DICT-%'
                """);
        jdbcTemplate.update("DELETE FROM sys_user WHERE login_name LIKE ?", LOGIN_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code LIKE 'REGR-DICT-%'");
        jdbcTemplate.update("UPDATE sys_unit SET parent_id = NULL WHERE unit_code = ?", UNIT_CODE);
        jdbcTemplate.update("DELETE FROM sys_unit WHERE unit_code = ?", UNIT_CODE);
        jdbcTemplate.update("""
                DELETE i FROM sys_dict_item i
                 JOIN sys_dict_type t ON t.id = i.dict_type_id
                WHERE t.dict_code = ?
                """, NAMESPACE_PROBE_CODE);
        jdbcTemplate.update("DELETE FROM sys_dict_type WHERE dict_code = ?", NAMESPACE_PROBE_CODE);
    }

    /** 把三本系统枚举恢复成 V14 的引导数据：类型改名/停用、字典项增删都在这里被抹平。 */
    private void restoreSystemDictionaries() {
        Object[][] types = {
            {STATUS_CODE, "通用状态", "用户、角色、单位与菜单共用的启停状态"},
            {MENU_TYPE_CODE, "菜单类型", "菜单资源树的节点类型"},
            {UNIT_LEVEL_CODE, "单位层级", "总队、支队、大队三级"},
        };
        Object[][] items = {
            {STATUS_CODE, "ENABLED", "启用", "正常可用", 10},
            {STATUS_CODE, "DISABLED", "停用", "不可登录或不可分配，历史数据保留", 20},
            {MENU_TYPE_CODE, "DIRECTORY", "目录", "只做导航分组，不携带权限码", 10},
            {MENU_TYPE_CODE, "PAGE", "页面", "绑定前端已注册的 route key", 20},
            {MENU_TYPE_CODE, "TAB", "页签", "页面内部的页签权限", 30},
            {MENU_TYPE_CODE, "BUTTON", "按钮", "页面或页签内的操作权限", 40},
            {UNIT_LEVEL_CODE, "1", "总队", "一级单位，全局唯一", 10},
            {UNIT_LEVEL_CODE, "2", "支队", "二级单位，挂在总队下", 20},
            {UNIT_LEVEL_CODE, "3", "大队", "三级单位，挂在支队下", 30},
        };

        jdbcTemplate.update("""
                DELETE i FROM sys_dict_item i
                 JOIN sys_dict_type t ON t.id = i.dict_type_id
                WHERE t.dict_code IN (?, ?, ?)
                """, STATUS_CODE, MENU_TYPE_CODE, UNIT_LEVEL_CODE);
        for (Object[] type : types) {
            jdbcTemplate.update("""
                    INSERT INTO sys_dict_type (dict_code, dict_name, description)
                    SELECT ?, ?, ?
                     WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_code = ?)
                    """, type[0], type[1], type[2], type[0]);
            jdbcTemplate.update("""
                    UPDATE sys_dict_type
                       SET dict_name = ?, description = ?, status = 'ENABLED',
                           version = 0, updated_at = CURRENT_TIMESTAMP(6)
                     WHERE dict_code = ?
                    """, type[1], type[2], type[0]);
        }
        for (Object[] item : items) {
            jdbcTemplate.update("""
                    INSERT INTO sys_dict_item (dict_type_id, item_value, item_label, description,
                                               sort_order)
                    SELECT t.id, ?, ?, ?, ?
                      FROM sys_dict_type t
                     WHERE t.dict_code = ?
                       AND NOT EXISTS (SELECT 1 FROM sys_dict_item i
                                        WHERE i.dict_type_id = t.id AND i.item_value = ?)
                    """, item[1], item[2], item[3], item[4], item[0], item[1]);
        }
    }

    private void insertFixtureRows() {
        jdbcTemplate.update("""
                INSERT INTO sys_unit (unit_code, unit_name, status) VALUES (?, ?, 'ENABLED')
                """, UNIT_CODE, UNIT_NAME);
        insertRole(BUILTIN_ROLE_CODE, "回归字典管理员", List.of());
        insertRole(READER_ROLE_CODE, "回归字典查看员", List.of(PermissionCodes.DICT_READ));
        insertRole(PLAIN_ROLE_CODE, "回归普通账号", List.of());
        insertUser(ADMIN_LOGIN, "回归字典管理员", BUILTIN_ROLE_CODE);
        insertUser(READER_LOGIN, "回归字典查看员", READER_ROLE_CODE);
        insertUser(PLAIN_LOGIN, "回归普通账号", PLAIN_ROLE_CODE);
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

    protected MockHttpSession plainSession() throws Exception {
        return signIn(PLAIN_LOGIN, RAW_PASSWORD);
    }

    protected MvcResult read(MockHttpSession session, String... codes) throws Exception {
        String path = codes.length == 0 ? READ_PATH : READ_PATH + "?codes=" + String.join(",", codes);
        return getWithSession(get(path), session);
    }

    protected MvcResult adminList(MockHttpSession session) throws Exception {
        return getWithSession(get(ADMIN_PATH), session);
    }

    protected MvcResult adminDetail(String code, MockHttpSession session) throws Exception {
        return getWithSession(get(ADMIN_PATH + "/" + code), session);
    }

    protected MvcResult getWithSession(MockHttpServletRequestBuilder request, MockHttpSession session)
            throws Exception {
        if (session != null) {
            request = request.session(session);
        }
        return mockMvc.perform(request).andReturn();
    }

    protected MvcResult createDictionary(MockHttpSession session, String code, String name)
            throws Exception {
        return sendJson(post(ADMIN_PATH).content(objectMapper.writeValueAsString(
                Map.of("code", code, "name", name, "description", "回归用字典"))), session);
    }

    protected MvcResult updateDictionary(MockHttpSession session, String code, int version,
                                         String name, String status) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", version);
        body.put("name", name);
        body.put("description", "回归用字典");
        body.put("status", status);
        return sendJson(put(ADMIN_PATH + "/" + code).content(
                objectMapper.writeValueAsString(body)), session);
    }

    protected MvcResult createItem(MockHttpSession session, String code, String value, String label,
                                   Integer sortOrder) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("value", value);
        body.put("label", label);
        body.put("description", "回归用字典项");
        body.put("sortOrder", sortOrder);
        return sendJson(post(ADMIN_PATH + "/" + code + "/items").content(
                objectMapper.writeValueAsString(body)), session);
    }

    protected MvcResult updateItem(MockHttpSession session, String code, String value, int version,
                                   String label, int sortOrder, String status) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", version);
        body.put("label", label);
        body.put("description", "回归用字典项");
        body.put("sortOrder", sortOrder);
        body.put("status", status);
        return sendJson(put(ADMIN_PATH + "/" + code + "/items/" + value).content(
                objectMapper.writeValueAsString(body)), session);
    }

    protected MvcResult deleteItem(MockHttpSession session, String code, String value)
            throws Exception {
        // 删除字典项已经下线，这里保留入口只用于断言"没有这条路"；写请求要带 CSRF 令牌
        return sendJson(delete(ADMIN_PATH + "/" + code + "/items/" + value), session);
    }

    // ---- 响应解析与库中真实状态 ----

    /** 读接口返回的是字典数组，按 code 取出其中一本。 */
    @SuppressWarnings("unchecked")
    protected static Map<String, Object> dictionaryOf(MvcResult result, String code) throws Exception {
        assertThat(result.getResponse().getStatus()).as("字典查询应成功").isEqualTo(200);
        List<Map<String, Object>> dictionaries = jsonOf(bodyOf(result), "$.data");
        return dictionaries.stream()
                .filter(dictionary -> code.equals(dictionary.get("code")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("响应里缺少字典：" + code));
    }

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> itemsOf(Map<String, Object> dictionary) {
        return (List<Map<String, Object>>) dictionary.getOrDefault("items", List.of());
    }

    protected long itemCountInDatabase(String code) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM sys_dict_item i
                  JOIN sys_dict_type t ON t.id = i.dict_type_id
                 WHERE t.dict_code = ?
                """, Long.class, code);
    }
}
