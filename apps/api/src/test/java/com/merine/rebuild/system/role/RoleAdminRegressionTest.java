package com.merine.rebuild.system.role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.merine.rebuild.system.security.PermissionCodes;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 角色管理的关键行为回归。
 *
 * 每条用例都走完整的过滤器链与真实 MySQL：断言 HTTP 状态、错误码、字段错误与库中真实状态。
 * 「还剩几个可用管理员」不让测试复刻覆盖 SQL，而是用真实登录 + 调用管理接口的可观察结果判定。
 */
@DisplayName("角色管理回归")
class RoleAdminRegressionTest extends RoleAdminRegressionSupport {

    @Test
    @DisplayName("未登录调用角色管理接口一律 401 JSON，且不产生任何写入")
    void unauthenticatedRoleRequestsAreRejectedWithoutWriting() throws Exception {
        String attemptedCode = CODE_PREFIX + "GHOST";

        assertUnauthenticatedJson(getJson(ROLES_PATH, null));
        assertUnauthenticatedJson(getJson(OPTIONS_PATH, null));
        assertUnauthenticatedJson(getJson(PERMISSION_TREE_PATH, null));
        assertUnauthenticatedJson(sendJson(post(ROLES_PATH).content(
                createRoleBody(attemptedCode, "未登录尝试", null, List.of())), null));
        assertUnauthenticatedJson(sendJson(post(ROLES_PATH + "/disable").content(
                roleStatusBody(TARGET_ROLE_CODE)), null));
        assertUnauthenticatedJson(sendJson(
                delete(ROLES_PATH + "/" + TARGET_ROLE_CODE + "?version=0"), null));

        assertThat(roleExists(attemptedCode)).as("未登录的新建不能落库").isFalse();
        assertThat(roleStatusInDatabase(TARGET_ROLE_CODE)).as("未登录的停用不能改状态")
                .isEqualTo("ENABLED");
    }

    @Test
    @DisplayName("判权看权限码：只有角色管理权限的账号能读能写角色，但读不了用户列表")
    void roleManagerCanManageRolesButNotUsers() throws Exception {
        MockHttpSession manager = managerSession();

        assertThat(getJson(ROLES_PATH, manager).getResponse().getStatus()).isEqualTo(200);
        assertThat(getJson(PERMISSION_TREE_PATH, manager).getResponse().getStatus()).isEqualTo(200);
        assertForbidden(getJson(USERS_PATH, manager));

        String code = CODE_PREFIX + "BYMANAGER";
        MvcResult created = sendJson(post(ROLES_PATH).content(
                createRoleBody(code, "角色管理员新建", "由只有角色权限的账号创建",
                        List.of(PermissionCodes.UNIT_READ))), manager);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat(rolePermissionCodesInDatabase(code)).containsExactly(PermissionCodes.UNIT_READ);
    }

    @Test
    @DisplayName("只有用户管理查看权限的账号能读角色选项，但进不了角色管理与权限清单")
    void userReaderCanReadRoleOptionsOnly() throws Exception {
        MockHttpSession reader = readerSession();

        MvcResult options = getJson(OPTIONS_PATH, reader);
        assertThat(options.getResponse().getStatus()).isEqualTo(200);
        List<Map<String, Object>> rows = jsonOf(bodyOf(options), "$.data");
        assertThat(rows).anySatisfy(row -> {
            assertThat(row.get("code")).isEqualTo(TARGET_ROLE_CODE);
            assertThat(row.get("name")).isEqualTo(TARGET_ROLE_NAME);
            assertThat(row.get("status")).isEqualTo("ENABLED");
        });

        assertForbidden(getJson(ROLES_PATH, reader));
        assertForbidden(getJson(PERMISSION_TREE_PATH, reader));
        assertForbidden(sendJson(post(ROLES_PATH).content(
                createRoleBody(CODE_PREFIX + "BYREADER", "越权尝试", null, List.of())), reader));
        assertThat(roleExists(CODE_PREFIX + "BYREADER")).as("被拒绝的新建不能落库").isFalse();
    }

    @Test
    @DisplayName("列表返回分页结构：成员数、权限数、说明与内置标记都与库中一致")
    void listReturnsPagedEnvelopeWithCounts() throws Exception {
        MvcResult result = listRoles(adminSession(), "keyword", CODE_PREFIX);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = bodyOf(result);
        assertThat(intOf(body, "$.data.page")).isEqualTo(1);
        assertThat(intOf(body, "$.data.pageSize")).isEqualTo(20);
        List<Map<String, Object>> items = itemsOf(result);

        Map<String, Object> target = itemOf(items, TARGET_ROLE_CODE);
        assertThat(target.get("name")).isEqualTo(TARGET_ROLE_NAME);
        assertThat(target.get("description")).isEqualTo(TARGET_ROLE_DESCRIPTION);
        assertThat(target.get("status")).isEqualTo("ENABLED");
        assertThat(target.get("builtin")).isEqualTo(false);
        assertThat(target.get("permissionCount")).isEqualTo(TARGET_PERMISSIONS.size());
        assertThat(target.get("userCount")).isEqualTo(1);
        assertThat((String) target.get("updatedAt")).isNotBlank();

        Map<String, Object> builtin = itemOf(items, BUILTIN_ROLE_CODE);
        assertThat(builtin.get("builtin")).isEqualTo(true);
        assertThat(builtin.get("permissionCount"))
                .as("内置角色恒拥有全部权限码，与库里有没有授予行无关")
                .isEqualTo(PermissionCodes.all().size());

        MvcResult outOfRange = listRoles(adminSession(),
                "keyword", CODE_PREFIX, "page", "99", "pageSize", "20");
        assertError(outOfRange, 400, "PAGE_OUT_OF_RANGE");
    }

    @Test
    @DisplayName("新建角色：编码重复 409、未知权限码 400、编码格式 400，失败一律不落库")
    void createRoleValidatesCodeAndPermissions() throws Exception {
        MockHttpSession admin = adminSession();

        MvcResult duplicate = sendJson(post(ROLES_PATH).content(
                createRoleBody(TARGET_ROLE_CODE, "重复编码", null, List.of())), admin);
        assertError(duplicate, 409, "ROLE_CODE_TAKEN");
        assertThat(roleNameInDatabase(TARGET_ROLE_CODE)).isEqualTo(TARGET_ROLE_NAME);

        MvcResult unknownPermission = sendJson(post(ROLES_PATH).content(
                createRoleBody(CODE_PREFIX + "BADPERM", "未知权限", null,
                        List.of("system:role:archive"))), admin);
        assertError(unknownPermission, 400, "UNKNOWN_PERMISSION");
        assertThat(roleExists(CODE_PREFIX + "BADPERM")).isFalse();

        MvcResult badCode = sendJson(post(ROLES_PATH).content(
                createRoleBody("回归 编码", "格式错误", null, List.of())), admin);
        assertValidationError(badCode, "code");
        assertThat(roleExists("回归 编码")).isFalse();
    }

    @Test
    @DisplayName("编辑角色：权限集合变化写入库并让持有者旧会话失效；旧版本再提交返回 409")
    void updateRoleReplacesPermissionsAndInvalidatesHolderSessions() throws Exception {
        MockHttpSession admin = adminSession();
        MockHttpSession holder = holderSession();
        int version = roleVersionInDatabase(TARGET_ROLE_CODE);
        assertThat(authorizationVersionInDatabase(HOLDER_LOGIN)).isZero();

        MvcResult updated = sendJson(put(ROLES_PATH + "/" + TARGET_ROLE_CODE).content(
                updateRoleBody(version, "回归目标角色（改）", "改后说明",
                        List.of(PermissionCodes.ROLE_READ))), admin);
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat(roleNameInDatabase(TARGET_ROLE_CODE)).isEqualTo("回归目标角色（改）");
        assertThat(roleDescriptionInDatabase(TARGET_ROLE_CODE)).isEqualTo("改后说明");
        assertThat(rolePermissionCodesInDatabase(TARGET_ROLE_CODE))
                .containsExactly(PermissionCodes.ROLE_READ);
        assertThat(authorizationVersionInDatabase(HOLDER_LOGIN))
                .as("权限集合变化必须让持有者旧会话失效").isEqualTo(1);
        assertThat(currentSession(holder).getResponse().getStatus()).isEqualTo(401);

        MvcResult conflict = sendJson(put(ROLES_PATH + "/" + TARGET_ROLE_CODE).content(
                updateRoleBody(version, "再改一次", null, List.of(PermissionCodes.ROLE_READ))), admin);
        assertError(conflict, 409, "ROLE_VERSION_CONFLICT");
        assertThat(roleNameInDatabase(TARGET_ROLE_CODE)).as("冲突的编辑不能落库")
                .isEqualTo("回归目标角色（改）");
    }

    @Test
    @DisplayName("只改名称与说明不递增持有者授权版本，在线会话不受影响")
    void renamingRoleDoesNotInvalidateSessions() throws Exception {
        MockHttpSession holder = holderSession();
        int version = roleVersionInDatabase(TARGET_ROLE_CODE);

        MvcResult updated = sendJson(put(ROLES_PATH + "/" + TARGET_ROLE_CODE).content(
                updateRoleBody(version, "回归目标角色（改名）", TARGET_ROLE_DESCRIPTION,
                        TARGET_PERMISSIONS)), adminSession());

        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat(authorizationVersionInDatabase(HOLDER_LOGIN))
                .as("改名与改说明只是展示性变更").isZero();
        assertThat(currentSession(holder).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("停用角色：持有者旧会话失效、授予关系保留；重复停用是幂等成功且不重复递增版本")
    void disablingRoleInvalidatesHoldersAndKeepsGrants() throws Exception {
        MockHttpSession admin = adminSession();
        MockHttpSession holder = holderSession();

        MvcResult disabled = sendJson(post(ROLES_PATH + "/disable").content(
                roleStatusBody(TARGET_ROLE_CODE)), admin);
        assertThat(disabled.getResponse().getStatus()).isEqualTo(200);
        assertThat(roleStatusInDatabase(TARGET_ROLE_CODE)).isEqualTo("DISABLED");
        assertThat(roleHolderCountInDatabase(TARGET_ROLE_CODE))
                .as("停用角色不改授予关系").isEqualTo(1);
        assertThat(authorizationVersionInDatabase(HOLDER_LOGIN)).isEqualTo(1);
        assertThat(currentSession(holder).getResponse().getStatus()).isEqualTo(401);

        int versionAfterDisable = roleVersionInDatabase(TARGET_ROLE_CODE);
        MvcResult again = sendJson(post(ROLES_PATH + "/disable").content(
                roleStatusBody(TARGET_ROLE_CODE)), admin);
        assertThat(again.getResponse().getStatus()).as("重复停用是幂等成功").isEqualTo(200);
        assertThat(roleVersionInDatabase(TARGET_ROLE_CODE))
                .as("没有实际状态变化时不再递增版本").isEqualTo(versionAfterDisable);
        assertThat(authorizationVersionInDatabase(HOLDER_LOGIN))
                .as("幂等请求不重复递增持有者授权版本").isEqualTo(1);
    }

    @Test
    @DisplayName("删除角色：有成员 409、版本不符 409、非内置且无成员可删、内置角色 409")
    void deleteRoleGuards() throws Exception {
        MockHttpSession admin = adminSession();

        MvcResult inUse = sendJson(delete(ROLES_PATH + "/" + TARGET_ROLE_CODE
                + "?version=" + roleVersionInDatabase(TARGET_ROLE_CODE)), admin);
        assertError(inUse, 409, "ROLE_IN_USE");
        assertThat(roleExists(TARGET_ROLE_CODE)).isTrue();

        MvcResult builtin = sendJson(delete(ROLES_PATH + "/" + BUILTIN_ROLE_CODE
                + "?version=" + roleVersionInDatabase(BUILTIN_ROLE_CODE)), admin);
        assertError(builtin, 409, "ROLE_BUILTIN_PROTECTED");
        assertThat(roleExists(BUILTIN_ROLE_CODE)).isTrue();

        String emptyCode = CODE_PREFIX + "EMPTY";
        assertThat(sendJson(post(ROLES_PATH).content(
                createRoleBody(emptyCode, "空角色", "没有任何成员", List.of())), admin)
                .getResponse().getStatus()).isEqualTo(201);

        MvcResult staleVersion = sendJson(delete(ROLES_PATH + "/" + emptyCode + "?version=99"), admin);
        assertError(staleVersion, 409, "ROLE_VERSION_CONFLICT");
        assertThat(roleExists(emptyCode)).isTrue();

        MvcResult deleted = sendJson(delete(ROLES_PATH + "/" + emptyCode
                + "?version=" + roleVersionInDatabase(emptyCode)), admin);
        assertThat(deleted.getResponse().getStatus()).isEqualTo(200);
        assertThat(roleExists(emptyCode)).isFalse();
    }

    @Test
    @DisplayName("内置角色：详情返回全部权限码，修改权限返回 409 且不落库")
    void builtinRolePermissionsAreReadOnly() throws Exception {
        MockHttpSession admin = adminSession();

        MvcResult detail = getJson(ROLES_PATH + "/" + BUILTIN_ROLE_CODE, admin);
        assertThat(detail.getResponse().getStatus()).isEqualTo(200);
        List<String> permissionCodes = jsonOf(bodyOf(detail), "$.data.permissionCodes");
        assertThat(permissionCodes).containsExactlyInAnyOrderElementsOf(PermissionCodes.all());
        assertThat((Boolean) jsonOf(bodyOf(detail), "$.data.builtin")).isTrue();

        MvcResult rejected = sendJson(put(ROLES_PATH + "/" + BUILTIN_ROLE_CODE).content(
                updateRoleBody(roleVersionInDatabase(BUILTIN_ROLE_CODE), BUILTIN_ROLE_NAME, null,
                        List.of(PermissionCodes.UNIT_READ))), admin);
        assertError(rejected, 409, "ROLE_BUILTIN_PROTECTED");
        assertThat(rolePermissionCodesInDatabase(BUILTIN_ROLE_CODE))
                .as("内置角色的权限行本来就不参与判定，这里确认接口也没有写它").isEmpty();

        MvcResult renamed = sendJson(put(ROLES_PATH + "/" + BUILTIN_ROLE_CODE).content(
                updateRoleBody(roleVersionInDatabase(BUILTIN_ROLE_CODE), "回归内置管理员（改名）",
                        "内置角色只允许改名称与说明", PermissionCodes.all())), admin);
        assertThat(renamed.getResponse().getStatus()).isEqualTo(200);
        assertThat(roleNameInDatabase(BUILTIN_ROLE_CODE)).isEqualTo("回归内置管理员（改名）");
    }

    @Test
    @DisplayName("管理底线：停用唯一可用管理员的角色返回 409，且不落库、不递增版本")
    void disablingTheOnlyUsableAdminRoleIsRejected() throws Exception {
        MockHttpSession admin = adminSession();
        int versionBefore = authorizationVersionInDatabase(ADMIN_LOGIN);

        MvcResult rejected = sendJson(post(ROLES_PATH + "/disable").content(
                roleStatusBody(BUILTIN_ROLE_CODE)), admin);

        assertError(rejected, 409, "LAST_USER_ADMIN");
        assertThat(roleStatusInDatabase(BUILTIN_ROLE_CODE)).as("被拒绝的停用不能改状态")
                .isEqualTo("ENABLED");
        assertThat(authorizationVersionInDatabase(ADMIN_LOGIN)).as("没有落库就别递增授权版本")
                .isEqualTo(versionBefore);
        assertThat(canManageRoles(ADMIN_LOGIN)).as("调用方仍然能管理系统").isTrue();
    }

    @Test
    @DisplayName("管理底线：把最后一个管理员的角色权限摘空返回 409；有权限兜底账号时同一动作可行")
    void removingBaselinePermissionsFromTheLastAdminRoleIsRejected() throws Exception {
        MockHttpSession admin = adminSession();
        String baselineCode = CODE_PREFIX + "BASELINE";

        // 先造出第二个管理员：他只靠基线权限码兜底，不持有内置角色
        assertThat(sendJson(post(ROLES_PATH).content(
                createRoleBody(baselineCode, "回归基线角色", "只带管理底线权限",
                        PermissionCodes.ADMIN_BASELINE)), admin).getResponse().getStatus())
                .isEqualTo(201);
        MvcResult created = sendJson(post(USERS_PATH).content(objectMapper.writeValueAsString(Map.of(
                "loginName", SECOND_ADMIN_LOGIN,
                "displayName", SECOND_ADMIN_DISPLAY,
                "unitCode", UNIT_CODE,
                "roleCodes", List.of(baselineCode),
                "password", RAW_PASSWORD))), admin);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat(canManageRoles(SECOND_ADMIN_LOGIN)).isTrue();

        // 覆盖交给权限兜底账号后，停用内置角色是允许的
        MvcResult disabled = sendJson(post(ROLES_PATH + "/disable").content(
                roleStatusBody(BUILTIN_ROLE_CODE)), admin);
        assertThat(disabled.getResponse().getStatus())
                .as("还有靠权限码兜底的管理员时，停用内置角色应当被允许").isEqualTo(200);

        // 现在唯一的可用管理员是这个只有基线权限的账号：摘掉它的关键权限必须被拒绝
        MockHttpSession fallbackAdmin = signIn(SECOND_ADMIN_LOGIN, RAW_PASSWORD);
        MvcResult rejected = sendJson(put(ROLES_PATH + "/" + baselineCode).content(
                updateRoleBody(roleVersionInDatabase(baselineCode), "回归基线角色", null,
                        List.of(PermissionCodes.ROLE_UPDATE))), fallbackAdmin);

        assertError(rejected, 409, "LAST_USER_ADMIN");
        assertThat((String) jsonOf(bodyOf(rejected), "$.message"))
                .isEqualTo("不能移除最后一个可用管理员的角色权限");
        assertThat(rolePermissionCodesInDatabase(baselineCode))
                .as("被拒绝的权限调整不能落库").containsExactlyInAnyOrderElementsOf(
                        PermissionCodes.ADMIN_BASELINE);
        assertThat(canManageRoles(SECOND_ADMIN_LOGIN))
                .as("被拒绝之后这个账号仍然能管理系统").isTrue();
    }

    @Test
    @DisplayName("并发停用两个仅剩的管理账号：只有一个成功，系统始终有人可管理")
    void concurrentDisablesKeepAtLeastOneManager() throws Exception {
        MockHttpSession firstAdmin = adminSession();
        createSecondAdmin(firstAdmin);
        MockHttpSession secondAdmin = signIn(SECOND_ADMIN_LOGIN, RAW_PASSWORD);

        long firstId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE login_name = ?", Long.class, ADMIN_LOGIN);
        long secondId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE login_name = ?", Long.class, SECOND_ADMIN_LOGIN);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            Callable<Integer> disableFirst = disableTask(ready, start, firstAdmin, firstId);
            Callable<Integer> disableSecond = disableTask(ready, start, secondAdmin, secondId);
            Future<Integer> firstResult = pool.submit(disableFirst);
            Future<Integer> secondResult = pool.submit(disableSecond);
            ready.await();
            start.countDown();

            assertThat(List.of(firstResult.get(), secondResult.get()))
                    .as("两个请求都试图停用仅剩的管理账号，只能有一个成功")
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }

        boolean firstStillManages = canManageRoles(ADMIN_LOGIN);
        boolean secondStillManages = canManageRoles(SECOND_ADMIN_LOGIN);
        assertThat(firstStillManages ^ secondStillManages)
                .as("必须且只能剩下一个还能管理系统的账号")
                .isTrue();
    }

    private Callable<Integer> disableTask(CountDownLatch ready, CountDownLatch start,
                                          MockHttpSession session, long userId) {
        return () -> {
            ready.countDown();
            start.await();
            return sendJson(post(USERS_PATH + "/disable")
                    .content(objectMapper.writeValueAsString(Map.of("userIds", List.of(
                            Long.toString(userId))))), session)
                    .getResponse().getStatus();
        };
    }

    @Test
    @DisplayName("成员清单只读分页：返回账号、姓名、单位与状态；角色不存在返回 404")
    void memberListIsReadOnlyPaged() throws Exception {
        MvcResult result = roleMembers(TARGET_ROLE_CODE, adminSession(),
                "page", "1", "pageSize", "20");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = bodyOf(result);
        assertThat(intOf(body, "$.data.total")).isEqualTo(1);
        List<Map<String, Object>> items = jsonOf(body, "$.data.items");
        assertThat(items).hasSize(1);
        Map<String, Object> member = items.getFirst();
        assertThat(member.get("loginName")).isEqualTo(HOLDER_LOGIN);
        assertThat(member.get("displayName")).isEqualTo(HOLDER_DISPLAY);
        assertThat(member.get("unitName")).isEqualTo(UNIT_NAME);
        assertThat(member.get("status")).isEqualTo("ENABLED");

        assertError(roleMembers(CODE_PREFIX + "NOPE", adminSession()), 404, "ROLE_NOT_FOUND");
        assertError(getJson(ROLES_PATH + "/" + CODE_PREFIX + "NOPE", adminSession()),
                404, "ROLE_NOT_FOUND");
    }
}
