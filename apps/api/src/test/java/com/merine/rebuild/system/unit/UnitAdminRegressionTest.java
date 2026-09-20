package com.merine.rebuild.system.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * 单位管理回归测试。
 *
 * 复用用户管理测试基座，是为了沿用同一套真实 Spring 上下文、真实 MySQL、真实登录、
 * CSRF 和合成数据命名空间；本类只增加组织树与单位写路径的断言，不替换任何 Bean。
 */
@DisplayName("单位管理回归")
class UnitAdminRegressionTest extends UnitAdminRegressionSupport {
    private static final String UNITS_PATH = "/api/system/units";
    private static final String CHILD_CODE = UNIT_CODE_PREFIX + "CHILD";
    private static final String GRANDCHILD_CODE = UNIT_CODE_PREFIX + "GRANDCHILD";
    private static final String LEAF_CODE = UNIT_CODE_PREFIX + "LEAF";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("真实组织数据是 1+7+42，三个修正层级正确，且演示单位已经退出")
    void importedRealUnitTreeHasExpectedShape() {
        assertThat(count("SELECT COUNT(*) FROM sys_unit WHERE unit_level = 1 AND unit_code = 'ORG_001'"))
                .as("一级单位为浙江省公安厅海防总队").isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM sys_unit WHERE unit_level = 2"))
                .as("二级单位为 7 个支队").isEqualTo(7);
        assertThat(count("SELECT COUNT(*) FROM sys_unit WHERE unit_level = 3 AND unit_code REGEXP '^[0-9]+$'"))
                .as("真实数据中的三级单位为 42 个").isEqualTo(42);
        assertThat(count("SELECT COUNT(*) FROM sys_unit WHERE unit_code = 'DEMO-UNIT-A'"))
                .as("演示单位必须由 V9 清除").isZero();

        Map<String, Integer> childrenByParent = childrenByParent();
        assertThat(childrenByParent)
                .containsEntry("ORG_002", 1)
                .containsEntry("ORG_003", 10)
                .containsEntry("ORG_004", 10)
                .containsEntry("ORG_005", 4)
                .containsEntry("ORG_007", 3)
                .containsEntry("ORG_010", 6)
                .containsEntry("ORG_011", 8);

        assertThat(parentCode("330114")).isEqualTo("ORG_002");
        assertThat(parentCode("330921")).isEqualTo("ORG_010");
        assertThat(parentCode("330922")).isEqualTo("ORG_010");
    }

    @Test
    @DisplayName("单位树和写接口沿用系统管理门禁：未登录 401，非管理员 403")
    void unitManagementEndpointsAreGuarded() throws Exception {
        assertUnauthenticatedJson(getJson(UNITS_PATH + "/tree", null));
        MockHttpSession analyst = signIn(ANALYST_LOGIN, RAW_PASSWORD);
        assertForbidden(getJson(UNITS_PATH + "/tree", analyst));
        assertForbidden(sendJson(post(UNITS_PATH).content(
                createUnitBody(CHILD_CODE, "越权新建单位", UNIT_ALPHA, "3301")), analyst));

        assertThat(count("SELECT COUNT(*) FROM sys_unit WHERE unit_code = ?", CHILD_CODE)).isZero();
    }

    @Test
    @DisplayName("管理员可以新建、编辑并删除空单位，树中立即反映真实结果")
    void adminCanCreateUpdateAndDeleteAnEmptyUnit() throws Exception {
        MockHttpSession admin = adminSession();

        MvcResult created = sendJson(post(UNITS_PATH).content(
                createUnitBody(CHILD_CODE, "回归新建单位", UNIT_ALPHA, "3301")), admin);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat((String) jsonOf(bodyOf(created), "$.data.code")).isEqualTo(CHILD_CODE);
        assertThat((String) jsonOf(bodyOf(created), "$.data.parentCode")).isEqualTo(UNIT_ALPHA);
        assertThat(intOf(bodyOf(created), "$.data.level")).isEqualTo(2);
        assertSavedParent(CHILD_CODE, UNIT_ALPHA);

        MvcResult updated = sendJson(put(UNITS_PATH + "/" + CHILD_CODE).content(
                updateUnitBody(0, "回归改名单位", UNIT_ALPHA, "3302")), admin);
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) jsonOf(bodyOf(updated), "$.data.name")).isEqualTo("回归改名单位");
        assertThat(intOf(bodyOf(updated), "$.data.version")).isEqualTo(1);
        assertThat(unitName(CHILD_CODE)).isEqualTo("回归改名单位");

        MvcResult tree = getJson(UNITS_PATH + "/tree", admin);
        assertThat(tree.getResponse().getStatus()).isEqualTo(200);
        JsonNode childNode = findNode(parseTree(tree), CHILD_CODE);
        assertThat(childNode).as("新建单位必须出现在组织树中").isNotNull();
        assertThat(childNode.path("parentCode").asText()).isEqualTo(UNIT_ALPHA);

        MvcResult deleted = sendJson(delete(UNITS_PATH + "/" + CHILD_CODE), admin);
        assertThat(deleted.getResponse().getStatus()).isEqualTo(200);
        assertThat(count("SELECT COUNT(*) FROM sys_unit WHERE unit_code = ?", CHILD_CODE)).isZero();
    }

    @Test
    @DisplayName("重复编码、第二个根、第四级、循环父级和旧版本都被拒绝，且不留下写入")
    void invalidUnitWritesAreRejectedWithoutPartialChanges() throws Exception {
        MockHttpSession admin = adminSession();

        assertError(sendJson(post(UNITS_PATH).content(
                createUnitBody(UNIT_ALPHA, "重复编码", UNIT_ALPHA, "3301")), admin),
                409, "UNIT_CODE_TAKEN");
        assertError(sendJson(post(UNITS_PATH).content(
                createUnitBody(UNIT_CODE_PREFIX + "ROOT2", "第二个根", null, "33")), admin),
                409, "ROOT_ALREADY_EXISTS");

        MvcResult child = sendJson(post(UNITS_PATH).content(
                createUnitBody(CHILD_CODE, "回归父级单位", UNIT_ALPHA, "3301")), admin);
        assertThat(child.getResponse().getStatus()).isEqualTo(201);
        MvcResult grandchild = sendJson(post(UNITS_PATH).content(
                createUnitBody(GRANDCHILD_CODE, "回归三级单位", CHILD_CODE, "3302")), admin);
        assertThat(grandchild.getResponse().getStatus()).isEqualTo(201);

        assertError(sendJson(post(UNITS_PATH).content(
                createUnitBody(UNIT_CODE_PREFIX + "LEVEL4", "第四级", GRANDCHILD_CODE, "3303")), admin),
                409, "UNIT_LEVEL_LIMIT");
        assertError(sendJson(put(UNITS_PATH + "/" + CHILD_CODE).content(
                updateUnitBody(0, "循环父级", GRANDCHILD_CODE, "3301")), admin),
                409, "UNIT_PARENT_CYCLE");
        assertError(sendJson(put(UNITS_PATH + "/" + CHILD_CODE).content(
                updateUnitBody(99, "旧版本覆盖", UNIT_ALPHA, "3301")), admin),
                409, "UNIT_VERSION_CONFLICT");

        assertThat(count("SELECT COUNT(*) FROM sys_unit WHERE unit_code = ?", UNIT_CODE_PREFIX + "LEVEL4"))
                .isZero();
        assertThat(unitName(CHILD_CODE)).isEqualTo("回归父级单位");
    }

    @Test
    @DisplayName("有下级或有用户的单位不能删除，空叶子删除后数据库确实没有残留")
    void unitWithChildrenOrUsersCannotBeDeleted() throws Exception {
        MockHttpSession admin = adminSession();

        MvcResult child = sendJson(post(UNITS_PATH).content(
                createUnitBody(CHILD_CODE, "回归父级单位", UNIT_ALPHA, "3301")), admin);
        assertThat(child.getResponse().getStatus()).isEqualTo(201);
        MvcResult grandchild = sendJson(post(UNITS_PATH).content(
                createUnitBody(GRANDCHILD_CODE, "回归三级单位", CHILD_CODE, "3302")), admin);
        assertThat(grandchild.getResponse().getStatus()).isEqualTo(201);

        assertError(sendJson(delete(UNITS_PATH + "/" + CHILD_CODE), admin),
                409, "UNIT_HAS_CHILDREN");
        assertThat(count("SELECT COUNT(*) FROM sys_unit WHERE unit_code = ?", CHILD_CODE)).isEqualTo(1);

        MvcResult leaf = sendJson(post(UNITS_PATH).content(
                createUnitBody(LEAF_CODE, "回归空叶子", UNIT_ALPHA, "3303")), admin);
        assertThat(leaf.getResponse().getStatus()).isEqualTo(201);
        String leafUser = LOGIN_NAME_PREFIX + "leafuser";
        MvcResult user = sendJson(post(USERS_PATH).content(
                createBody(leafUser, "叶子单位用户", LEAF_CODE, List.of(ANALYST_ROLE_CODE), RAW_PASSWORD)),
                admin);
        assertThat(user.getResponse().getStatus()).isEqualTo(201);

        assertError(sendJson(delete(UNITS_PATH + "/" + LEAF_CODE), admin),
                409, "UNIT_HAS_USERS");
        assertThat(count("SELECT COUNT(*) FROM sys_unit WHERE unit_code = ?", LEAF_CODE)).isEqualTo(1);
    }

    private int count(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0 : value.intValue();
    }

    private Map<String, Integer> childrenByParent() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT p.unit_code AS parent_code, COUNT(c.id) AS child_count
                  FROM sys_unit p
                  JOIN sys_unit c ON c.parent_id = p.id
                 WHERE p.unit_code IN ('ORG_002','ORG_003','ORG_004','ORG_005','ORG_007','ORG_010','ORG_011')
                 GROUP BY p.id, p.unit_code
                """);
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            result.put((String) row.get("parent_code"), ((Number) row.get("child_count")).intValue());
        }
        return result;
    }

    private String parentCode(String childCode) {
        return jdbcTemplate.queryForObject("""
                SELECT p.unit_code
                  FROM sys_unit c
                  JOIN sys_unit p ON p.id = c.parent_id
                 WHERE c.unit_code = ?
                """, String.class, childCode);
    }

    private String unitName(String unitCode) {
        return jdbcTemplate.queryForObject("SELECT unit_name FROM sys_unit WHERE unit_code = ?",
                String.class, unitCode);
    }

    private void assertSavedParent(String childCode, String parentCode) {
        assertThat(parentCode(childCode)).isEqualTo(parentCode);
    }

    private String createUnitBody(String code, String name, String parentCode, String areaCode)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("name", name);
        body.put("parentCode", parentCode);
        body.put("areaCode", areaCode);
        return objectMapper.writeValueAsString(body);
    }

    private String updateUnitBody(int version, String name, String parentCode, String areaCode)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", version);
        body.put("name", name);
        body.put("parentCode", parentCode);
        body.put("areaCode", areaCode);
        return objectMapper.writeValueAsString(body);
    }

    private JsonNode parseTree(MvcResult result) throws Exception {
        return objectMapper.readTree(bodyOf(result)).path("data");
    }

    private static JsonNode findNode(JsonNode nodes, String code) {
        if (nodes == null || nodes.isNull()) return null;
        if (nodes.isArray()) {
            for (JsonNode node : nodes) {
                JsonNode found = findNode(node, code);
                if (found != null) return found;
            }
            return null;
        }
        if (code.equals(nodes.path("code").asText())) return nodes;
        return findNode(nodes.path("children"), code);
    }
}
