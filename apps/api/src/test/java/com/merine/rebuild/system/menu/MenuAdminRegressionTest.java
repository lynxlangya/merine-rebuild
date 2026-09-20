package com.merine.rebuild.system.menu;

import static org.assertj.core.api.Assertions.assertThat;
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
 * 菜单与按钮级权限的关键行为回归。
 *
 * 每条用例都走完整过滤器链与真实 MySQL：断言 HTTP 状态、错误码、库中真实状态与登录行为。
 * 「导航里出现什么」与「接口能不能调用」是两件事，这里分别断言，避免把隐藏菜单当成授权。
 */
@DisplayName("菜单与按钮级权限回归")
class MenuAdminRegressionTest extends MenuAdminRegressionSupport {

    @Test
    @DisplayName("未登录访问菜单接口一律 401；登录即可读自己的导航但看不到未授权页面")
    void myMenusRequireLoginButShowOnlyGrantedPages() throws Exception {
        assertUnauthenticatedJson(getMenus(null));
        assertUnauthenticatedJson(getMyMenus(null));

        // 只读账号只有 system:user:read：导航只剩「系统管理 → 用户管理」，其下的按钮一个都不出现
        MvcResult readerNav = getMyMenus(readerSession());
        assertThat(readerNav.getResponse().getStatus()).isEqualTo(200);
        List<Map<String, Object>> groups = jsonOf(bodyOf(readerNav), "$.data");
        assertThat(groups).hasSize(1);
        Map<String, Object> group = groups.getFirst();
        assertThat(group.get("name")).isEqualTo("系统管理");
        List<Map<String, Object>> pages = castChildren(group);
        assertThat(pages).hasSize(1);
        assertThat(pages.getFirst().get("name")).isEqualTo("用户管理");
        assertThat(pages.getFirst().get("routeKey")).isEqualTo("system.users");
        assertThat(castChildren(pages.getFirst()))
                .as("没有 user:create 之类按钮权限时，按钮节点不下发").isEmpty();

        // 多了 system:user:create 之后，同一个页面下出现「新建用户」按钮节点
        List<Map<String, Object>> writerGroups = jsonOf(bodyOf(getMyMenus(writerSession())), "$.data");
        List<Map<String, Object>> writerPages = castChildren(writerGroups.getFirst());
        assertThat(castChildren(writerPages.getFirst()).stream().map(node -> node.get("name")))
                .containsExactly("新建用户");
    }

    @Test
    @DisplayName("管理树与可选页面清单：引导节点齐全，route key 清单来自前端注册表")
    void adminTreeAndRouteKeysComeFromTheBootstrapAndRegistry() throws Exception {
        MockHttpSession admin = adminSession();
        MvcResult tree = getMenus(admin);

        assertThat(findNode(tree, "系统管理").get("type")).isEqualTo("DIRECTORY");
        Map<String, Object> usersPage = findNode(tree, "用户管理");
        assertThat(usersPage.get("routeKey")).isEqualTo("system.users");
        assertThat(usersPage.get("permissionCode")).isEqualTo(PermissionCodes.USER_READ);
        assertThat(findNode(tree, "重置密码").get("permissionCode"))
                .isEqualTo(PermissionCodes.USER_RESET_PASSWORD);

        MvcResult routeKeys = getWithSession(get(MENUS_PATH + "/route-keys"), admin);
        assertThat(routeKeys.getResponse().getStatus()).isEqualTo(200);
        List<String> keys = jsonOf(bodyOf(routeKeys), "$.data[*].key");
        assertThat(keys)
                .containsExactlyInAnyOrder("system.users", "system.roles", "system.menus",
                        "system.units", "dev.diagnostics");
    }

    @Test
    @DisplayName("新增节点：页面必须绑已注册 route key，层级与权限码格式都受校验")
    void creatingNodesValidatesRouteKeyHierarchyAndPermissionCode() throws Exception {
        MockHttpSession admin = adminSession();

        MvcResult directory = createMenu(null, "DIRECTORY", TEST_DIRECTORY_NAME, null, null, 200, admin);
        assertThat(directory.getResponse().getStatus()).isEqualTo(201);
        String directoryId = (String) jsonOf(bodyOf(directory), "$.data.id");

        MvcResult unknownRoute = createMenu(directoryId, "PAGE", NAME_PREFIX + "未知页面",
                "unknown.route", CODE_PREFIX + "page", 10, admin);
        assertError(unknownRoute, 400, "MENU_ROUTE_KEY_UNKNOWN");

        MvcResult takenRoute = createMenu(directoryId, "PAGE", NAME_PREFIX + "重复页面",
                "system.users", CODE_PREFIX + "page", 10, admin);
        assertError(takenRoute, 409, "MENU_ROUTE_KEY_TAKEN");

        MvcResult wrongHierarchy = createMenu(directoryId, "BUTTON", NAME_PREFIX + "层级错误",
                null, CODE_PREFIX + "wrong", 10, admin);
        assertError(wrongHierarchy, 400, "MENU_TYPE_NOT_ALLOWED");

        MvcResult badCode = createMenu(null, "BUTTON", NAME_PREFIX + "权限码错误", null,
                "Bad Code", 10, admin);
        assertValidationError(badCode, "permissionCode");

        assertThat(menuNamed(NAME_PREFIX + "未知页面")).isFalse();
        assertThat(permissionExists(CODE_PREFIX + "page")).isFalse();
    }

    @Test
    @DisplayName("编辑节点：版本冲突 409、改名同步权限名称、不能挂到自己的子树下")
    void updatingNodesChecksVersionAndPreventsCycles() throws Exception {
        MockHttpSession admin = adminSession();
        String parentId = (String) jsonOf(bodyOf(createMenu(null, "DIRECTORY", TEST_DIRECTORY_NAME,
                null, null, 200, admin)), "$.data.id");
        String childId = (String) jsonOf(bodyOf(createMenu(parentId, "DIRECTORY",
                NAME_PREFIX + "子目录", null, null, 10, admin)), "$.data.id");

        MvcResult cyclic = updateMenu(parentId, versionOf(findNode(getMenus(admin), TEST_DIRECTORY_NAME)),
                childId, TEST_DIRECTORY_NAME, null, 200, "ENABLED", admin);
        assertError(cyclic, 409, "MENU_PARENT_CYCLE");

        MvcResult stale = updateMenu(parentId, versionOf(findNode(getMenus(admin), TEST_DIRECTORY_NAME)),
                null, NAME_PREFIX + "验收目录（改）", null, 200, "ENABLED", admin);
        assertThat(stale.getResponse().getStatus()).as("先改名成功").isEqualTo(200);
        MvcResult conflict = updateMenu(parentId,
                versionOf(findNode(getMenus(admin), NAME_PREFIX + "验收目录（改）")) - 1,
                null, NAME_PREFIX + "验收目录（再改）", null, 200, "ENABLED", admin);
        assertError(conflict, 409, "MENU_VERSION_CONFLICT");

        String usersPageId = findNode(getMenus(admin), "用户管理").get("id").toString();
        String code = CODE_PREFIX + "button";
        String buttonId = (String) jsonOf(bodyOf(createMenu(usersPageId, "BUTTON",
                NAME_PREFIX + "按钮", null, code, 50, admin)), "$.data.id");
        MvcResult renamedButton = updateMenu(buttonId,
                versionOf(findNode(getMenus(admin), NAME_PREFIX + "按钮")), usersPageId,
                NAME_PREFIX + "按钮（改）", null, 50, "ENABLED", admin);
        assertThat(renamedButton.getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT permission_name FROM sys_permission WHERE permission_code = ?",
                String.class, code)).isEqualTo(NAME_PREFIX + "按钮（改）");
    }

    @Test
    @DisplayName("按钮级判权：能看不能建、能建不能改、重置密码要单独权限")
    void buttonLevelPermissionsSeparateReadCreateUpdateAndReset() throws Exception {
        MockHttpSession reader = readerSession();
        MockHttpSession writer = writerSession();

        assertThat(getWithSession(get(USERS_PATH), reader).getResponse().getStatus())
                .as("有 user:read 就能读用户列表").isEqualTo(200);
        String newUser = objectMapper.writeValueAsString(Map.of(
                "loginName", LOGIN_PREFIX + "created",
                "displayName", "回归新建账号",
                "unitCode", UNIT_CODE,
                "roleCodes", List.of(READER_ROLE_CODE),
                "password", RAW_PASSWORD));
        assertForbidden(sendJson(post(USERS_PATH).content(newUser), reader));

        MvcResult created = sendJson(post(USERS_PATH).content(newUser), writer);
        assertThat(created.getResponse().getStatus()).as("有 user:create 就能新建").isEqualTo(201);
        String createdId = (String) jsonOf(bodyOf(created), "$.data.id");
        int createdVersion = intOf(bodyOf(created), "$.data.version");

        assertForbidden(sendJson(put(USERS_PATH + "/" + createdId).content(
                objectMapper.writeValueAsString(Map.of(
                        "version", createdVersion,
                        "displayName", "回归改名",
                        "unitCode", UNIT_CODE,
                        "roleCodes", List.of(READER_ROLE_CODE)))), writer));
        assertForbidden(sendJson(post(USERS_PATH + "/" + createdId + "/reset-password").content(
                objectMapper.writeValueAsString(Map.of(
                        "version", createdVersion, "newPassword", "regr-menu-new-secret"))), writer));
        assertForbidden(sendJson(post(USERS_PATH + "/disable").content(
                objectMapper.writeValueAsString(Map.of("userIds", List.of(createdId)))), writer));
        assertForbidden(getWithSession(get(ROLES_PATH), writer));
        assertForbidden(getMenus(writer));
    }

    @Test
    @DisplayName("删除子树：预览与回执写明影响，解除角色授权并让持有者重新登录")
    void deletingSubtreeRemovesGrantsAndInvalidatesHolders() throws Exception {
        MockHttpSession admin = adminSession();
        String usersPageId = findNode(getMenus(admin), "用户管理").get("id").toString();
        String code = CODE_PREFIX + "deletable";
        String buttonId = (String) jsonOf(bodyOf(createMenu(usersPageId, "BUTTON",
                NAME_PREFIX + "待删按钮", null, code, 60, admin)), "$.data.id");
        jdbcTemplate.update("""
                INSERT INTO sys_role_permission (role_id, permission_id)
                SELECT r.id, p.id FROM sys_role r, sys_permission p
                 WHERE r.role_code = ? AND p.permission_code = ?
                """, READER_ROLE_CODE, code);
        MockHttpSession reader = readerSession();
        assertThat(grantsOf(code)).isEqualTo(1);
        int version = versionOf(findNode(getMenus(admin), NAME_PREFIX + "待删按钮"));

        MvcResult impact = getWithSession(get(MENUS_PATH + "/" + buttonId + "/delete-impact"), admin);
        assertThat(impact.getResponse().getStatus()).isEqualTo(200);
        assertThat(intOf(bodyOf(impact), "$.data.deletedNodes")).isEqualTo(1);
        assertThat(intOf(bodyOf(impact), "$.data.affectedRoles")).isEqualTo(1);

        MvcResult deleted = deleteMenu(buttonId, version, admin);
        assertThat(deleted.getResponse().getStatus()).isEqualTo(200);
        assertThat(intOf(bodyOf(deleted), "$.data.deletedNodes")).isEqualTo(1);
        assertThat(intOf(bodyOf(deleted), "$.data.affectedRoles")).isEqualTo(1);

        assertThat(permissionExists(code)).as("权限码随节点一起删除").isFalse();
        assertThat(grantsOf(code)).as("角色授权关系一并解除").isZero();
        assertThat(authorizationVersionInDatabase(READER_LOGIN)).as("持有者授权版本递增").isEqualTo(1);
        assertThat(currentSession(reader).getResponse().getStatus()).as("持有者旧会话失效")
                .isEqualTo(401);
    }

    @Test
    @DisplayName("恢复默认菜单：删掉的引导按钮被补齐，重复执行不再新建")
    void restoreOnlyFillsMissingBootstrapEntries() throws Exception {
        MockHttpSession admin = adminSession();
        Map<String, Object> resetButton = findNode(getMenus(admin), "重置密码");
        String code = PermissionCodes.USER_RESET_PASSWORD;

        MvcResult deleted = deleteMenu(idOf(resetButton), versionOf(resetButton), admin);
        assertThat(deleted.getResponse().getStatus()).isEqualTo(200);
        assertThat(menuNamed("重置密码")).isFalse();
        assertThat(permissionExists(code)).as("删节点会一起删掉权限码").isFalse();

        MvcResult restored = sendJson(post(MENUS_PATH + "/restore"), admin);
        assertThat(restored.getResponse().getStatus()).isEqualTo(200);
        assertThat(intOf(bodyOf(restored), "$.data.createdMenus")).isEqualTo(1);
        assertThat(intOf(bodyOf(restored), "$.data.createdPermissions")).isEqualTo(1);
        assertThat(menuNamed("重置密码")).isTrue();
        assertThat(permissionExists(code)).isTrue();
        assertThat(findNode(getMenus(admin), "重置密码").get("permissionCode")).isEqualTo(code);

        MvcResult again = sendJson(post(MENUS_PATH + "/restore"), admin);
        assertThat(intOf(bodyOf(again), "$.data.createdMenus")).isZero();
        assertThat(intOf(bodyOf(again), "$.data.createdPermissions")).isZero();
    }

    @Test
    @DisplayName("停用页面只影响导航：导航里不出现，接口判定与已有授权不变")
    void disablingPageHidesNavigationButKeepsApiAccess() throws Exception {
        MockHttpSession admin = adminSession();
        MockHttpSession reader = readerSession();
        Map<String, Object> usersPage = findNode(getMenus(admin), "用户管理");

        String systemsDirectoryId = findNode(getMenus(admin), "系统管理").get("id").toString();
        MvcResult disabled = updateMenu(idOf(usersPage), versionOf(usersPage), systemsDirectoryId,
                "用户管理", "system.users", 10, "DISABLED", admin);
        assertThat(disabled.getResponse().getStatus()).isEqualTo(200);

        List<Map<String, Object>> hiddenNav = jsonOf(bodyOf(getMyMenus(reader)), "$.data");
        assertThat(hiddenNav).as("停用后导航里不再出现").isEmpty();
        assertThat(getWithSession(get(USERS_PATH), reader).getResponse().getStatus())
                .as("隐藏菜单不等于禁止访问：接口仍按权限码判定").isEqualTo(200);

        Map<String, Object> disabledPage = findNode(getMenus(admin), "用户管理");
        assertThat(updateMenu(idOf(disabledPage), versionOf(disabledPage), systemsDirectoryId,
                "用户管理", "system.users", 10, "ENABLED", admin).getResponse().getStatus())
                .isEqualTo(200);
        List<Map<String, Object>> restoredNav = jsonOf(bodyOf(getMyMenus(reader)), "$.data");
        assertThat(restoredNav).isNotEmpty();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castChildren(Map<String, Object> node) {
        return (List<Map<String, Object>>) node.getOrDefault("children", List.of());
    }
}
