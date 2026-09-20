package com.merine.rebuild.system.dictionary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.merine.rebuild.system.security.PermissionCodes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 字典模块的关键行为回归。
 *
 * 核心不变量：系统枚举与业务字典是同一套数据、同一套接口，都能在线维护；取值是不可变标识，
 * 字典类型与字典项都只停用、不删除（没有删除接口）；读接口登录即可、写接口要权限码；
 * 字典只影响展示，不改变任何业务判定。
 */
@DisplayName("字典模块回归")
class DictionaryRegressionTest extends DictionaryRegressionSupport {

    @Test
    @DisplayName("未登录读字典与管理接口一律 401 JSON")
    void unauthenticatedRequestsAreRejected() throws Exception {
        assertUnauthenticatedJson(getWithSession(get(READ_PATH), null));
        assertUnauthenticatedJson(getWithSession(get(ADMIN_PATH), null));
        assertUnauthenticatedJson(getWithSession(get(ADMIN_PATH + "/common.status"), null));
    }

    @Test
    @DisplayName("系统枚举与业务字典同源：能读、能改名、能给系统枚举加字典项")
    @SuppressWarnings("unchecked")
    void systemDictionariesAreEditable() throws Exception {
        MockHttpSession admin = adminSession();

        MvcResult read = read(admin);
        assertThat(dictionaryOf(read, STATUS_CODE)).satisfies(dictionary -> {
            assertThat(itemsOf(dictionary).stream().map(item -> item.get("value")))
                    .containsExactly("ENABLED", "DISABLED");
        });
        assertThat(dictionaryOf(read, MENU_TYPE_CODE)).isNotNull();
        assertThat(dictionaryOf(read, UNIT_LEVEL_CODE)).isNotNull();

        List<Map<String, Object>> list = jsonOf(bodyOf(adminList(admin)), "$.data");
        assertThat(list).anySatisfy(row -> {
            assertThat(row.get("code")).isEqualTo(STATUS_CODE);
            assertThat(row.get("name")).isEqualTo("通用状态");
        });

        // 与业务字典完全一样：改名、停用/启用、加字典项都能落库
        MvcResult renamed = updateDictionary(admin, STATUS_CODE, 0, "启用状态（改）", "ENABLED");
        assertThat(renamed.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) jsonOf(bodyOf(renamed), "$.data.name")).isEqualTo("启用状态（改）");
        assertThat(createItem(admin, STATUS_CODE, "ARCHIVED", "已归档", 30).getResponse().getStatus())
                .isEqualTo(201);
        assertThat(itemsOf(dictionaryOf(read(admin, STATUS_CODE), STATUS_CODE))
                .stream().map(item -> item.get("value")))
                .containsExactly("ENABLED", "DISABLED", "ARCHIVED");

        // 编码仍然唯一：拿已有的字典编码新建照旧 409
        assertError(createDictionary(admin, STATUS_CODE, "重复编码"), 409, "DICT_CODE_TAKEN");
    }

    @Test
    @DisplayName("新建字典：编码重复 409、编码与取值格式 400")
    void creatingBusinessDictionaryValidatesCode() throws Exception {
        MockHttpSession admin = adminSession();

        assertThat(createDictionary(admin, DEMO_CODE, "回归业务字典").getResponse().getStatus())
                .isEqualTo(201);
        MvcResult duplicate = createDictionary(admin, DEMO_CODE, "重复编码");
        assertError(duplicate, 409, "DICT_CODE_TAKEN");
        MvcResult otherPrefix = createDictionary(admin, NAMESPACE_PROBE_CODE, "系统命名空间下的普通字典");
        assertThat(otherPrefix.getResponse().getStatus())
                .as("编码前缀不再有保留概念，唯一性由唯一键保证").isEqualTo(201);
        MvcResult badCode = createDictionary(admin, "Bad Code", "格式错误");
        assertValidationError(badCode, "code");
        MvcResult badItem = createItem(admin, DEMO_CODE, "含空格 的值", "非法取值", 10);
        assertValidationError(badItem, "value");
        assertThat(itemCountInDatabase(DEMO_CODE)).as("被拒绝的请求不留数据").isZero();
    }

    @Test
    @DisplayName("字典项：新增、编辑、停用后读接口仍返回该值但不适合再选")
    void itemsCanBeEditedAndDisabledButStayResolvable() throws Exception {
        MockHttpSession admin = adminSession();
        assertThat(createDictionary(admin, DEMO_CODE, "回归业务字典").getResponse().getStatus())
                .isEqualTo(201);

        MvcResult first = createItem(admin, DEMO_CODE, "FISH", "渔船", 20);
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        MvcResult second = createItem(admin, DEMO_CODE, "CARGO", "货船", 10);
        assertThat(second.getResponse().getStatus()).isEqualTo(201);
        MvcResult duplicate = createItem(admin, DEMO_CODE, "FISH", "渔船（重复）", 30);
        assertError(duplicate, 409, "DICT_ITEM_VALUE_TAKEN");

        MvcResult updated = updateItem(admin, DEMO_CODE, "FISH", 0, "渔船（改）", 5, "DISABLED");
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) jsonOf(bodyOf(updated), "$.data.label")).isEqualTo("渔船（改）");
        assertThat((String) jsonOf(bodyOf(updated), "$.data.status")).isEqualTo("DISABLED");

        Map<String, Object> dictionary = dictionaryOf(read(admin, DEMO_CODE), DEMO_CODE);
        List<Map<String, Object>> items = itemsOf(dictionary);
        assertThat(items).hasSize(2);
        assertThat(items.getFirst().get("value")).as("按排序升序：FISH 排 5，CARGO 排 10")
                .isEqualTo("FISH");
        assertThat(items.getFirst().get("status")).as("停用项仍返回，前端用它解析历史标签")
                .isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("字典项没有删除接口：DELETE 一律 405，库里数据原样保留")
    void itemsCannotBeDeleted() throws Exception {
        MockHttpSession admin = adminSession();
        assertThat(createDictionary(admin, DEMO_CODE, "回归业务字典").getResponse().getStatus())
                .isEqualTo(201);
        assertThat(createItem(admin, DEMO_CODE, "FISH", "渔船", 10).getResponse().getStatus())
                .isEqualTo(201);

        // 字典是全局参考数据：误删会让标签与选择项立刻退化，因此刻意不提供删除能力
        MvcResult deleted = deleteItem(admin, DEMO_CODE, "FISH");
        assertThat(deleted.getResponse().getStatus())
                .as("删除字典项响应：%s", deleted.getResponse().getContentAsString())
                .isEqualTo(405);
        assertThat((String) jsonOf(bodyOf(deleted), "$.code")).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(itemCountInDatabase(DEMO_CODE)).as("被拒绝的删除不能动数据").isEqualTo(1);
        assertThat(itemsOf(dictionaryOf(read(admin, DEMO_CODE), DEMO_CODE)))
                .singleElement()
                .satisfies(item -> assertThat(item.get("value")).isEqualTo("FISH"));

        // 也没有"删除字典类型"这条路：PUT 之外的写方法一律不支持
        assertThat(sendJson(delete(ADMIN_PATH + "/" + DEMO_CODE), admin).getResponse().getStatus())
                .isEqualTo(405);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_type WHERE dict_code = ?", Long.class, DEMO_CODE))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("停用字典类型：读接口返回空项列表；重新启用后字典项回来")
    void disabledDictionaryTypeReturnsNoItems() throws Exception {
        MockHttpSession admin = adminSession();
        assertThat(createDictionary(admin, DEMO_CODE, "回归业务字典").getResponse().getStatus())
                .isEqualTo(201);
        assertThat(createItem(admin, DEMO_CODE, "FISH", "渔船", 10).getResponse().getStatus())
                .isEqualTo(201);

        assertThat(updateDictionary(admin, DEMO_CODE, 0, "回归业务字典", "DISABLED")
                .getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> disabled = dictionaryOf(read(admin, DEMO_CODE), DEMO_CODE);
        assertThat(disabled.get("status")).isEqualTo("DISABLED");
        assertThat(itemsOf(disabled)).as("类型停用后使用侧拿不到选项").isEmpty();
        Map<String, Object> adminView = jsonOf(bodyOf(adminDetail(DEMO_CODE, admin)), "$.data");
        assertThat(itemsOf(adminView))
                .as("管理端仍要看到全部字典项，否则停用后就没法继续维护")
                .hasSize(1);
        assertThat(itemCountInDatabase(DEMO_CODE)).as("库里字典项没有被删除").isEqualTo(1);

        assertThat(updateDictionary(admin, DEMO_CODE, 1, "回归业务字典", "ENABLED")
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(itemsOf(dictionaryOf(read(admin, DEMO_CODE), DEMO_CODE))).hasSize(1);
    }

    @Test
    @DisplayName("版本冲突与未知编码：类型 409、字典项 409、读接口 400、详情 404")
    void versionConflictsAndUnknownCodesAreReported() throws Exception {
        MockHttpSession admin = adminSession();
        assertThat(createDictionary(admin, DEMO_CODE, "回归业务字典").getResponse().getStatus())
                .isEqualTo(201);
        assertThat(createItem(admin, DEMO_CODE, "FISH", "渔船", 10).getResponse().getStatus())
                .isEqualTo(201);

        MvcResult staleType = updateDictionary(admin, DEMO_CODE, 5, "旧版本改名", "ENABLED");
        assertError(staleType, 409, "DICT_VERSION_CONFLICT");
        MvcResult staleItem = updateItem(admin, DEMO_CODE, "FISH", 5, "旧版本改名", 10, "ENABLED");
        assertError(staleItem, 409, "DICT_ITEM_VERSION_CONFLICT");

        MvcResult unknownRead = read(admin, "regr.not.exists");
        assertError(unknownRead, 400, "UNKNOWN_DICTIONARY");
        MvcResult missingDetail = adminDetail(CODE_PREFIX + "missing", admin);
        assertError(missingDetail, 404, "DICT_NOT_FOUND");
        MvcResult missingItem = updateItem(admin, DEMO_CODE, "NOT_THERE", 0, "不存在", 10, "ENABLED");
        assertError(missingItem, 404, "DICT_ITEM_NOT_FOUND");
    }

    @Test
    @DisplayName("读接口登录即可；管理接口要权限码，只有读权限时增删改一律 403")
    void readingNeedsLoginOnlyWhileWritingNeedsPermissions() throws Exception {
        MockHttpSession plain = plainSession();
        MockHttpSession reader = readerSession();

        assertThat(read(plain).getResponse().getStatus()).as("使用侧读字典登录即可").isEqualTo(200);
        assertForbidden(adminList(plain));
        assertForbidden(adminDetail(STATUS_CODE, plain));

        assertThat(adminList(reader).getResponse().getStatus()).as("只读权限能看管理页").isEqualTo(200);
        assertForbidden(createDictionary(reader, DEMO_CODE, "越权新建"));
        assertForbidden(sendJson(put(ADMIN_PATH + "/" + DEMO_CODE).content(
                objectMapper.writeValueAsString(Map.of("version", 0, "name", "越权编辑",
                        "description", "", "status", "ENABLED"))), reader));
        // 越权请求不能留下任何数据
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_dict_type WHERE dict_code = ?", Long.class, DEMO_CODE))
                .isZero();
        assertThat(itemCountInDatabase(STATUS_CODE)).as("并发越权写入不能改动系统枚举").isEqualTo(2);
    }

    @Test
    @DisplayName("字典只影响展示：字典里出现同名取值时，用户启停仍按业务值判定")
    void dictionaryDoesNotChangeBusinessValidation() throws Exception {
        MockHttpSession admin = adminSession();
        // 建一本取值恰好叫 ENABLED / DISABLED 的业务字典，并把标签改成业务文案
        assertThat(createDictionary(admin, DEMO_CODE, "回归状态字典").getResponse().getStatus())
                .isEqualTo(201);
        assertThat(createItem(admin, DEMO_CODE, "ENABLED", "在线", 10).getResponse().getStatus())
                .isEqualTo(201);
        assertThat(createItem(admin, DEMO_CODE, "DISABLED", "离线", 20).getResponse().getStatus())
                .isEqualTo(201);
        assertThat(updateItem(admin, DEMO_CODE, "ENABLED", 0, "在线（改）", 10, "ENABLED")
                .getResponse().getStatus()).isEqualTo(200);

        // 用户接口仍按 ENABLED / DISABLED 判定：停用再启用照样可用
        long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE login_name = ?", Long.class, PLAIN_LOGIN);
        MvcResult disabled = sendJson(post(USERS_PATH + "/disable").content(
                objectMapper.writeValueAsString(Map.of("userIds", List.of(Long.toString(userId))))),
                admin);
        assertThat(disabled.getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM sys_user WHERE id = ?", String.class, userId))
                .isEqualTo("DISABLED");
        MvcResult enabled = sendJson(post(USERS_PATH + "/enable").content(
                objectMapper.writeValueAsString(Map.of("userIds", List.of(Long.toString(userId))))),
                admin);
        assertThat(enabled.getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM sys_user WHERE id = ?", String.class, userId))
                .isEqualTo("ENABLED");
    }

    @Test
    @DisplayName("字典选项按 code 批量读取，未请求的字典不会出现")
    void batchReadReturnsOnlyRequestedDictionaries() throws Exception {
        MockHttpSession admin = adminSession();
        assertThat(createDictionary(admin, DEMO_CODE, "回归业务字典").getResponse().getStatus())
                .isEqualTo(201);

        MvcResult read = read(admin, STATUS_CODE, DEMO_CODE);
        List<Map<String, Object>> dictionaries = jsonOf(bodyOf(read), "$.data");
        assertThat(dictionaries.stream().map(dictionary -> dictionary.get("code")))
                .containsExactlyInAnyOrder(STATUS_CODE, DEMO_CODE);
        assertThat(PermissionCodes.DICT_READ).isEqualTo("system:dict:read");
    }
}
