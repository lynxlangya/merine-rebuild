package com.merine.rebuild.system.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 用户管理的关键行为回归。
 *
 * 每条用例都走完整的过滤器链与真实 MySQL：断言的是 HTTP 状态、错误码、字段错误、
 * 库中真实状态与登录行为，不是 mock 的调用次数。访问控制由 {@link UserAdminGuard}
 * 按登录时下发的 {@code ROLE_<角色编码>} 判定，因此这里的调用方身份都靠真实登录取得。
 */
@DisplayName("用户管理回归")
class UserAdminRegressionTest extends UserAdminRegressionSupport {

    @Test
    @DisplayName("未登录调用用户管理接口一律 401 JSON（不是 HTML），且不产生任何写入")
    void unauthenticatedUserAdminRequestsReturnJsonUnauthenticatedWithoutWritingAnything() throws Exception {
        String attemptedLogin = LOGIN_NAME_PREFIX + "ghost";

        assertUnauthenticatedJson(listUsers(null, "keyword", LOGIN_NAME_PREFIX));
        assertUnauthenticatedJson(getJson(USERS_PATH + "/" + userId(ADMIN_LOGIN), null));
        assertUnauthenticatedJson(sendJson(post(USERS_PATH).content(
                createBody(attemptedLogin, "未登录尝试", UNIT_ALPHA, List.of(ANALYST_ROLE_CODE),
                        RAW_PASSWORD)), null));
        assertUnauthenticatedJson(sendJson(post(USERS_PATH + "/disable").content(
                statusBody(userId(ADMIN_LOGIN))), null));

        assertThat(countUsersWithLoginName(attemptedLogin)).as("未登录的写请求不能落库").isZero();
        assertThat(statusInDatabase(ADMIN_LOGIN)).as("未登录的停用请求不能改状态").isEqualTo("ENABLED");
        assertThat(countSyntheticUsers()).isEqualTo(FIXTURE_COUNT);
    }

    @Test
    @DisplayName("已登录但角色不是用户管理员：用户管理接口返回 403 FORBIDDEN，且没有产生任何写入")
    void nonAdminRoleGetsForbiddenOnUserAdminEndpointsAndWritesNothing() throws Exception {
        // 隔离测试库里没有演示账号（test-db 只跑迁移，不跑 seed），
        // 因此这里用同形状的合成非管理员账号：角色不在 merine.security.user-admin-role-codes 里。
        MockHttpSession analystSession = signIn(ANALYST_LOGIN, RAW_PASSWORD);
        String attemptedLogin = LOGIN_NAME_PREFIX + "byanalyst";

        assertForbidden(listUsers(analystSession, "keyword", LOGIN_NAME_PREFIX));
        assertForbidden(getJson(USERS_PATH + "/" + userId(ADMIN_LOGIN), analystSession));
        assertForbidden(sendJson(post(USERS_PATH).content(
                createBody(attemptedLogin, "越权尝试", UNIT_ALPHA, List.of(ANALYST_ROLE_CODE),
                        RAW_PASSWORD)), analystSession));
        assertForbidden(sendJson(post(USERS_PATH + "/disable").content(
                statusBody(userId(ADMIN_LOGIN))), analystSession));

        assertThat(countUsersWithLoginName(attemptedLogin)).as("被拒绝的新建不能落库").isZero();
        assertThat(countSyntheticUsers()).as("被拒绝的请求不能产生任何写入").isEqualTo(FIXTURE_COUNT);
        assertThat(statusInDatabase(ADMIN_LOGIN)).as("被拒绝的停用不能改状态").isEqualTo("ENABLED");
        assertThat(analystSession.isInvalid()).as("403 不退出登录，会话应继续有效").isFalse();
    }

    @Test
    @DisplayName("管理员查询列表返回 200：结构含 items/total/page/pageSize，行内容与库中数据一致")
    void adminListingReturnsPagedEnvelopeMatchingDatabaseRows() throws Exception {
        MvcResult result = listUsers(adminSession(), "keyword", LOGIN_NAME_PREFIX);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = bodyOf(result);
        assertThat((String) jsonOf(body, "$.code")).isEqualTo("OK");
        assertThat(intOf(body, "$.data.page")).isEqualTo(1);
        assertThat(intOf(body, "$.data.pageSize")).isEqualTo(20);
        assertThat(intOf(body, "$.data.total")).isEqualTo(FIXTURE_COUNT);

        List<Map<String, Object>> items = jsonOf(body, "$.data.items");
        assertThat(items).hasSize(FIXTURE_COUNT);

        Map<String, Object> adminRow = itemOf(items, ADMIN_LOGIN);
        assertThat(adminRow.get("id")).isEqualTo(Long.toString(userId(ADMIN_LOGIN)));
        assertThat(adminRow.get("loginName")).isEqualTo(ADMIN_LOGIN);
        assertThat(adminRow.get("displayName")).isEqualTo(ADMIN_DISPLAY);
        assertThat(adminRow.get("unitCode")).isEqualTo(UNIT_ALPHA);
        assertThat(adminRow.get("unitName")).isEqualTo(UNIT_ALPHA_NAME);
        assertThat(adminRow.get("status")).isEqualTo("ENABLED");
        assertThat((List<String>) adminRow.get("roleCodes")).containsExactly(ADMIN_ROLE_CODE);
        assertThat((List<String>) adminRow.get("roleNames")).containsExactly(ADMIN_ROLE_NAME);
        assertThat(adminRow.get("lastLoginAt")).as("本用例的调用方登录过，列表要反映真实登录时间")
                .isNotNull();
        assertThat(itemOf(items, EDITOR_LOGIN).get("lastLoginAt"))
                .as("从未登录的账号 lastLoginAt 为 null").isNull();

        // 多角色账号：roleCodes 按编码升序，roleNames 与之下标一一对应
        Map<String, Object> editorRow = itemOf(items, EDITOR_LOGIN);
        assertThat((List<String>) editorRow.get("roleCodes"))
                .containsExactly(ANALYST_ROLE_CODE, EDITOR_ROLE_CODE);
        assertThat((List<String>) editorRow.get("roleNames"))
                .containsExactly(ANALYST_ROLE_NAME, EDITOR_ROLE_NAME);

        assertThat(body).as("用户列表不得输出任何密码字段").doesNotContain("password").doesNotContain("Password");
        assertThat(body).as("响应体不得回显库中的密码哈希")
                .doesNotContain(passwordHashInDatabase(ADMIN_LOGIN));
    }

    @Test
    @DisplayName("列表筛选：关键字同时匹配账号与姓名，单位、角色、状态各自生效，组合条件取交集")
    void listFiltersNarrowByKeywordUnitRoleAndStatusAndCombineAsIntersection() throws Exception {
        MockHttpSession admin = adminSession();

        assertThat(loginNamesOf(listUsers(admin, "keyword", EDITOR_LOGIN)))
                .as("关键字匹配账号").containsExactly(EDITOR_LOGIN);
        assertThat(NAMED_LOGIN).as("这条账号里不含该关键字，因此只可能靠姓名命中")
                .doesNotContain(NAME_KEYWORD);
        assertThat(loginNamesOf(listUsers(admin, "keyword", NAME_KEYWORD)))
                .as("同一个关键字参数也匹配姓名").containsExactly(NAMED_LOGIN);

        assertThat(loginNamesOf(listUsers(admin, "keyword", LOGIN_NAME_PREFIX, "unitCode", UNIT_BETA)))
                .as("单位筛选按 unit_code 精确匹配")
                .containsExactlyInAnyOrder(EDITOR_LOGIN, UNDERSCORE_LOGIN, UNDERSCORE_DECOY_LOGIN,
                        PERCENT_LOGIN, PERCENT_DECOY_LOGIN);
        assertThat(loginNamesOf(listUsers(admin, "keyword", LOGIN_NAME_PREFIX,
                "roleCode", EDITOR_ROLE_CODE)))
                .as("角色筛选按授予关系匹配，与角色名称无关").containsExactly(EDITOR_LOGIN);
        assertThat(loginNamesOf(listUsers(admin, "keyword", LOGIN_NAME_PREFIX, "status", "DISABLED")))
                .as("状态筛选").containsExactly(DISABLED_LOGIN);

        assertThat(loginNamesOf(listUsers(admin, "keyword", PAGE_LOGIN_KEYWORD, "unitCode", UNIT_ALPHA,
                "roleCode", ANALYST_ROLE_CODE, "status", "ENABLED")))
                .as("组合条件取交集").containsExactlyInAnyOrderElementsOf(PAGE_LOGINS);
        assertThat(loginNamesOf(listUsers(admin, "keyword", PAGE_LOGIN_KEYWORD, "unitCode", UNIT_BETA)))
                .as("加入一个不匹配的条件后结果为空，说明每个条件都真的参与了筛选").isEmpty();
    }

    @Test
    @DisplayName("关键字里的 % 与 _ 按字面匹配：含下划线的账号只命中自己，不命中同形账号")
    void keywordPercentAndUnderscoreAreMatchedLiterallyNotAsWildcards() throws Exception {
        MockHttpSession admin = adminSession();

        assertThat(loginNamesOf(listUsers(admin, "keyword", UNDERSCORE_LOGIN)))
                .as("_ 必须按字面匹配，不能当成单字符通配符")
                .containsExactly(UNDERSCORE_LOGIN);
        assertThat(loginNamesOf(listUsers(admin, "keyword", PERCENT_LOGIN)))
                .as("% 必须按字面匹配，不能当成任意长度通配符")
                .containsExactly(PERCENT_LOGIN);

        // 反证：把通配符位置换成普通字符后干扰账号本身能被搜到，
        // 说明上面两条不是「搜索整体失效」造成的假通过。
        assertThat(loginNamesOf(listUsers(admin, "keyword", UNDERSCORE_DECOY_LOGIN)))
                .containsExactly(UNDERSCORE_DECOY_LOGIN);
        assertThat(loginNamesOf(listUsers(admin, "keyword", PERCENT_DECOY_LOGIN)))
                .containsExactly(PERCENT_DECOY_LOGIN);
    }

    @Test
    @DisplayName("分页：pageSize 生效且 total 为匹配总数；页码越界与 pageSize 超限各自返回 400")
    void pagingHonoursPageSizeAndRejectsPageOutOfRangeAndOversizedPageSize() throws Exception {
        MockHttpSession admin = adminSession();

        MvcResult firstPage = listUsers(admin, "keyword", PAGE_LOGIN_KEYWORD, "page", "1", "pageSize", "2");
        assertThat(firstPage.getResponse().getStatus()).isEqualTo(200);
        String firstBody = bodyOf(firstPage);
        assertThat(intOf(firstBody, "$.data.page")).isEqualTo(1);
        assertThat(intOf(firstBody, "$.data.pageSize")).isEqualTo(2);
        assertThat(intOf(firstBody, "$.data.total")).as("total 是匹配总数，不随分页变化").isEqualTo(3);
        assertThat(loginNamesOf(firstPage)).containsExactly(PAGE_LOGINS.get(0), PAGE_LOGINS.get(1));

        MvcResult secondPage = listUsers(admin, "keyword", PAGE_LOGIN_KEYWORD, "page", "2", "pageSize", "2");
        assertThat(secondPage.getResponse().getStatus()).isEqualTo(200);
        assertThat(intOf(bodyOf(secondPage), "$.data.total")).isEqualTo(3);
        assertThat(loginNamesOf(secondPage)).containsExactly(PAGE_LOGINS.get(2));

        MvcResult outOfRange = listUsers(admin, "keyword", PAGE_LOGIN_KEYWORD, "page", "5", "pageSize", "20");
        assertError(outOfRange, 400, "PAGE_OUT_OF_RANGE");

        MvcResult tooLarge = listUsers(admin, "keyword", PAGE_LOGIN_KEYWORD, "pageSize", "101");
        assertError(tooLarge, 400, "INVALID_PAGE_SIZE");

        // 空结果不是越界：没有匹配行时，页码再大也只是空列表
        MvcResult emptyResult = listUsers(admin, "keyword", LOGIN_NAME_PREFIX + "nosuch", "page", "3");
        assertThat(emptyResult.getResponse().getStatus()).isEqualTo(200);
        assertThat(intOf(bodyOf(emptyResult), "$.data.total")).isZero();
        assertThat(itemsOf(emptyResult)).isEmpty();
    }

    @Test
    @DisplayName("页码大到偏移量溢出 int 时返回 400 PAGE_OUT_OF_RANGE（不是 500），极小越界与边界页一并对照")
    void hugePageNumberIsRejectedAsOutOfRangeInsteadOfOverflowingIntoAServerError() throws Exception {
        MockHttpSession admin = adminSession();

        // 3 个匹配行、pageSize 100：偏移量 (21474838 - 1) × 100 = 2147483700 超过 int 上限。
        // 用 int 计算时会溢出成负 OFFSET，MySQL 判成语法错误而变成 500；
        // 改用 long 之后，越界在第一道判定里就被挡下，与页码大小无关。
        assertError(listUsers(admin, "keyword", PAGE_LOGIN_KEYWORD, "page", "21474838", "pageSize", "100"),
                400, "PAGE_OUT_OF_RANGE");
        // 更极端的页码同样是 400，而不是服务故障
        assertError(listUsers(admin, "keyword", PAGE_LOGIN_KEYWORD,
                "page", Integer.toString(Integer.MAX_VALUE), "pageSize", "100"), 400, "PAGE_OUT_OF_RANGE");

        // 极小越界：默认 pageSize 20 时这 3 个账号只有 1 页，第 2 页已经越界
        assertError(listUsers(admin, "keyword", PAGE_LOGIN_KEYWORD, "page", "2"), 400, "PAGE_OUT_OF_RANGE");

        // 边界：恰好等于总页数（pageSize 1、共 3 页时第 3 页）能取到，再往后一页才越界
        MvcResult lastPage = listUsers(admin, "keyword", PAGE_LOGIN_KEYWORD, "page", "3", "pageSize", "1");
        assertThat(lastPage.getResponse().getStatus()).isEqualTo(200);
        assertThat(intOf(bodyOf(lastPage), "$.data.total")).isEqualTo(3);
        assertThat(loginNamesOf(lastPage)).containsExactly(PAGE_LOGINS.get(2));
        assertError(listUsers(admin, "keyword", PAGE_LOGIN_KEYWORD, "page", "4", "pageSize", "1"),
                400, "PAGE_OUT_OF_RANGE");
    }

    @Test
    @DisplayName("新建用户返回 201：库中密码是 BCrypt 哈希且不等于明文，用该账号与新密码能真实登录")
    void createdUserIsStoredAsBcryptHashAndCanReallyLogIn() throws Exception {
        String loginName = LOGIN_NAME_PREFIX + "created";
        String password = "regr-created-secret";

        MvcResult created = sendJson(post(USERS_PATH).content(
                createBody(loginName, "回归新建账号", UNIT_BETA, List.of(ANALYST_ROLE_CODE), password)),
                adminSession());

        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        String body = bodyOf(created);
        assertThat((String) jsonOf(body, "$.code")).isEqualTo("OK");
        assertThat((String) jsonOf(body, "$.data.loginName")).isEqualTo(loginName);
        assertThat((String) jsonOf(body, "$.data.displayName")).isEqualTo("回归新建账号");
        assertThat((String) jsonOf(body, "$.data.unitCode")).isEqualTo(UNIT_BETA);
        assertThat((String) jsonOf(body, "$.data.status")).isEqualTo("ENABLED");
        assertThat((List<String>) jsonOf(body, "$.data.roleCodes")).containsExactly(ANALYST_ROLE_CODE);
        assertThat((String) jsonOf(body, "$.data.id")).isEqualTo(Long.toString(userIdInDatabase(loginName)));

        String hash = passwordHashInDatabase(loginName);
        assertThat(hash).as("库里只能存 BCrypt 哈希").startsWith("$2");
        assertThat(hash).as("哈希不能等于明文，也不能包含明文").isNotEqualTo(password)
                .doesNotContain(password);
        assertThat(passwordEncoder.matches(password, hash)).as("该哈希必须能校验出这个密码").isTrue();
        assertThat(roleCodesInDatabase(userIdInDatabase(loginName))).containsExactly(ANALYST_ROLE_CODE);
        assertThat(lastLoginAtInDatabase(loginName)).as("新建账号从未登录").isNull();

        // 真实登录：走认证接口，不是断言 mock
        MvcResult login = login(loginName, password, issueCsrfToken(null), new MockHttpSession());
        assertThat(login.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) jsonOf(bodyOf(login), "$.data.loginName")).isEqualTo(loginName);
        assertThat(lastLoginAtInDatabase(loginName)).as("登录成功必须登记 last_login_at").isNotNull();
    }

    @Test
    @DisplayName("账号重复返回 409 LOGIN_NAME_TAKEN，且不会写出第二行")
    void duplicateLoginNameIsRejectedWithConflictAndWritesNoSecondRow() throws Exception {
        String loginName = LOGIN_NAME_PREFIX + "dup";

        MvcResult first = sendJson(post(USERS_PATH).content(
                createBody(loginName, "回归重复账号", UNIT_ALPHA, List.of(ANALYST_ROLE_CODE), RAW_PASSWORD)),
                adminSession());
        assertThat(first.getResponse().getStatus()).isEqualTo(201);

        MvcResult second = sendJson(post(USERS_PATH).content(
                createBody(loginName, "另一个姓名", UNIT_BETA, List.of(ANALYST_ROLE_CODE),
                        "regr-dup-secret")), adminSession());
        assertError(second, 409, "LOGIN_NAME_TAKEN");

        assertThat(countUsersWithLoginName(loginName)).as("重复请求不能写出第二行").isEqualTo(1);
        assertThat(displayNameInDatabase(loginName)).as("已存在的账号不被改动").isEqualTo("回归重复账号");
        assertThat(statusInDatabase(loginName)).isEqualTo("ENABLED");
    }

    @Test
    @DisplayName("账号格式不合规、姓名为空、角色为空、密码过短各自返回 400，字段错误落在对应字段")
    void invalidCreatePayloadsReportFieldErrorsOnTheOffendingField() throws Exception {
        MockHttpSession admin = adminSession();

        MvcResult badLoginName = sendJson(post(USERS_PATH).content(
                createBody("BAD Name", "回归校验", UNIT_ALPHA, List.of(ANALYST_ROLE_CODE), RAW_PASSWORD)),
                admin);
        assertValidationError(badLoginName, "loginName");

        MvcResult blankDisplayName = sendJson(post(USERS_PATH).content(
                createBody(LOGIN_NAME_PREFIX + "valid1", "   ", UNIT_ALPHA,
                        List.of(ANALYST_ROLE_CODE), RAW_PASSWORD)), admin);
        assertValidationError(blankDisplayName, "displayName");

        MvcResult emptyRoles = sendJson(post(USERS_PATH).content(
                createBody(LOGIN_NAME_PREFIX + "valid2", "回归校验", UNIT_ALPHA, List.of(), RAW_PASSWORD)),
                admin);
        assertValidationError(emptyRoles, "roleCodes");

        MvcResult shortPassword = sendJson(post(USERS_PATH).content(
                createBody(LOGIN_NAME_PREFIX + "valid3", "回归校验", UNIT_ALPHA,
                        List.of(ANALYST_ROLE_CODE), "12345")), admin);
        assertValidationError(shortPassword, "password");

        assertThat(countSyntheticUsers()).as("校验失败的请求不能落库").isEqualTo(FIXTURE_COUNT);
    }

    @Test
    @DisplayName("单位不存在返回 400 UNKNOWN_UNIT，角色不存在返回 400 UNKNOWN_ROLE，都不落库")
    void unknownUnitAndUnknownRoleAreRejectedWithoutWritingAnything() throws Exception {
        MockHttpSession admin = adminSession();

        MvcResult unknownUnit = sendJson(post(USERS_PATH).content(
                createBody(LOGIN_NAME_PREFIX + "unknown.unit", "回归未知单位",
                        UNIT_CODE_PREFIX + "NOPE", List.of(ANALYST_ROLE_CODE), RAW_PASSWORD)), admin);
        assertError(unknownUnit, 400, "UNKNOWN_UNIT");

        MvcResult unknownRole = sendJson(post(USERS_PATH).content(
                createBody(LOGIN_NAME_PREFIX + "unknown.role", "回归未知角色", UNIT_ALPHA,
                        List.of(ROLE_CODE_PREFIX + "NOPE"), RAW_PASSWORD)), admin);
        assertError(unknownRole, 400, "UNKNOWN_ROLE");

        assertThat(countSyntheticUsers()).as("被拒绝的新建不能落库").isEqualTo(FIXTURE_COUNT);
        assertThat(countUsersWithLoginName(LOGIN_NAME_PREFIX + "unknown.unit")).isZero();
        assertThat(countUsersWithLoginName(LOGIN_NAME_PREFIX + "unknown.role")).isZero();
    }

    @Test
    @DisplayName("编辑改变角色：authorization_version 递增，该账号已有会话在下一次请求即 401")
    void changingRolesBumpsAuthorizationVersionAndInvalidatesTheExistingSession() throws Exception {
        MockHttpSession admin = adminSession();
        MockHttpSession analystSession = signIn(ANALYST_LOGIN, RAW_PASSWORD);
        assertThat(currentSession(analystSession).getResponse().getStatus()).as("改角色之前会话可用")
                .isEqualTo(200);
        long analystId = userId(ANALYST_LOGIN);
        assertThat(authorizationVersionInDatabase(ANALYST_LOGIN)).isZero();

        MvcResult updated = sendJson(put(USERS_PATH + "/" + analystId).content(
                updateBody(ANALYST_DISPLAY, UNIT_ALPHA, List.of(EDITOR_ROLE_CODE), null)), admin);

        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat((List<String>) jsonOf(bodyOf(updated), "$.data.roleCodes"))
                .containsExactly(EDITOR_ROLE_CODE);
        assertThat(authorizationVersionInDatabase(ANALYST_LOGIN))
                .as("角色变化必须递增授权版本").isEqualTo(1);
        assertThat(roleCodesInDatabase(analystId))
                .as("角色关系在库里真实替换，不是只改了旧角色").containsExactly(EDITOR_ROLE_CODE);

        MvcResult afterChange = currentSession(analystSession);
        assertThat(afterChange.getResponse().getStatus()).isEqualTo(401);
        assertThat((String) jsonOf(bodyOf(afterChange), "$.code")).isEqualTo("UNAUTHENTICATED");
        assertThat(analystSession.isInvalid()).as("角色变化必须作废已有会话").isTrue();

        // 重新登录拿到新角色与新版本，说明上面的失效来自版本变化而不是账号不可用
        MockHttpSession reLogin = signIn(ANALYST_LOGIN, RAW_PASSWORD);
        MvcResult current = currentSession(reLogin);
        assertThat(current.getResponse().getStatus()).isEqualTo(200);
        assertThat(intOf(bodyOf(current), "$.data.authorizationVersion")).isEqualTo(1);
    }

    @Test
    @DisplayName("编辑重置密码：authorization_version 递增，新密码可登录而旧密码返回 401")
    void resettingPasswordBumpsAuthorizationVersionAndReplacesTheCredential() throws Exception {
        String newPassword = "regr-user-reset-secret";
        long analystId = userId(ANALYST_LOGIN);
        String oldHash = passwordHashInDatabase(ANALYST_LOGIN);

        // 单位与角色都保持不变：版本递增只能来自密码变化
        MvcResult updated = sendJson(put(USERS_PATH + "/" + analystId).content(
                updateBody(ANALYST_DISPLAY, UNIT_ALPHA, List.of(ANALYST_ROLE_CODE), newPassword)),
                adminSession());

        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat(authorizationVersionInDatabase(ANALYST_LOGIN))
                .as("密码变化必须递增授权版本").isEqualTo(1);

        String newHash = passwordHashInDatabase(ANALYST_LOGIN);
        assertThat(newHash).isNotEqualTo(oldHash).startsWith("$2").doesNotContain(newPassword);
        assertThat(passwordEncoder.matches(newPassword, newHash)).isTrue();
        assertThat(passwordEncoder.matches(RAW_PASSWORD, newHash)).as("旧密码不能继续有效").isFalse();

        MvcResult withNewPassword = login(ANALYST_LOGIN, newPassword, issueCsrfToken(null),
                new MockHttpSession());
        assertThat(withNewPassword.getResponse().getStatus()).isEqualTo(200);

        MvcResult withOldPassword = login(ANALYST_LOGIN, RAW_PASSWORD, issueCsrfToken(null),
                new MockHttpSession());
        assertError(withOldPassword, 401, "INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("停用账号：已有会话下一次请求即 401，重新登录返回 403 ACCOUNT_DISABLED")
    void disablingAccountInvalidatesExistingSessionAndRefusesNewLogin() throws Exception {
        MockHttpSession admin = adminSession();
        MockHttpSession analystSession = signIn(ANALYST_LOGIN, RAW_PASSWORD);
        assertThat(currentSession(analystSession).getResponse().getStatus()).as("停用之前会话可用")
                .isEqualTo(200);

        MvcResult disabled = sendJson(post(USERS_PATH + "/disable").content(
                statusBody(userId(ANALYST_LOGIN))), admin);

        assertThat(disabled.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) jsonOf(bodyOf(disabled), "$.data[0].status")).isEqualTo("DISABLED");
        assertThat((String) jsonOf(bodyOf(disabled), "$.data[0].loginName")).isEqualTo(ANALYST_LOGIN);
        assertThat(statusInDatabase(ANALYST_LOGIN)).isEqualTo("DISABLED");

        MvcResult afterDisable = currentSession(analystSession);
        assertThat(afterDisable.getResponse().getStatus()).isEqualTo(401);
        assertThat((String) jsonOf(bodyOf(afterDisable), "$.code")).isEqualTo("UNAUTHENTICATED");
        assertThat(analystSession.isInvalid())
                .as("停用必须作废服务端会话，而不是只让本次请求失败").isTrue();

        // 密码正确但账号已停用：重新登录必须被明确拒绝
        MvcResult relogin = login(ANALYST_LOGIN, RAW_PASSWORD, issueCsrfToken(null),
                new MockHttpSession());
        assertError(relogin, 403, "ACCOUNT_DISABLED");
    }

    @Test
    @DisplayName("停用最后一个启用状态的管理员返回 409 LAST_USER_ADMIN；存在第二个启用管理员时可以停用其一")
    void disablingTheLastEnabledAdminIsRejectedWithConflictButAllowedWithASecondAdmin() throws Exception {
        MockHttpSession admin = adminSession();
        long adminId = userId(ADMIN_LOGIN);
        assertThat(countEnabledAdmins()).as("用例前提：库里只有一个启用状态的管理员").isEqualTo(1);

        MvcResult rejected = sendJson(post(USERS_PATH + "/disable").content(statusBody(adminId)), admin);

        assertError(rejected, 409, "LAST_USER_ADMIN");
        assertThat(statusInDatabase(ADMIN_LOGIN)).as("被拒绝的停用不能改状态").isEqualTo("ENABLED");
        assertThat(countEnabledAdmins()).isEqualTo(1);

        // 造出第二个启用管理员后，停用其中一个不再会把所有人挡在用户管理之外
        String secondAdminLogin = LOGIN_NAME_PREFIX + "admin2";
        MvcResult created = sendJson(post(USERS_PATH).content(
                createBody(secondAdminLogin, "回归管理员乙", UNIT_ALPHA, List.of(ADMIN_ROLE_CODE),
                        RAW_PASSWORD)), admin);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        long secondAdminId = userIdInDatabase(secondAdminLogin);
        assertThat(secondAdminId).isNotEqualTo(adminId);
        assertThat(countEnabledAdmins()).isEqualTo(2);

        MvcResult disabled = sendJson(post(USERS_PATH + "/disable").content(
                statusBody(secondAdminId)), admin);

        assertThat(disabled.getResponse().getStatus()).isEqualTo(200);
        assertThat(statusInDatabase(secondAdminLogin)).isEqualTo("DISABLED");
        assertThat(statusInDatabase(ADMIN_LOGIN)).as("另一个管理员不受影响").isEqualTo("ENABLED");
        assertThat(countEnabledAdmins()).isEqualTo(1);
    }

    @Test
    @DisplayName("移除最后一个可用管理员的管理角色返回 409 LAST_USER_ADMIN，整次编辑都不落库")
    void removingTheAdminRoleFromTheLastUsableAdminIsRejectedAndRollsTheWholeUpdateBack() throws Exception {
        MockHttpSession admin = adminSession();
        long adminId = userId(ADMIN_LOGIN);
        String passwordHashBefore = passwordHashInDatabase(ADMIN_LOGIN);
        assertThat(countEnabledAdmins()).as("用例前提：库里只有一个启用状态的管理员").isEqualTo(1);

        // 同一次请求里还改了姓名、归属单位与密码：被拒绝时这些写入同样不能留下
        MvcResult rejected = sendJson(put(USERS_PATH + "/" + adminId).content(
                updateBody("回归管理员（改）", UNIT_BETA, List.of(ANALYST_ROLE_CODE),
                        "regr-last-admin-secret")), admin);

        assertError(rejected, 409, "LAST_USER_ADMIN");
        assertThat((String) jsonOf(bodyOf(rejected), "$.message"))
                .as("错误消息要说清是“不能移除最后一个可用管理员的角色”")
                .isEqualTo("不能移除最后一个可用管理员的角色");
        assertThat(roleCodesInDatabase(adminId)).as("被拒绝的编辑不能摘掉管理角色")
                .containsExactly(ADMIN_ROLE_CODE);
        assertThat(displayNameInDatabase(ADMIN_LOGIN)).as("被拒绝的编辑不能改姓名")
                .isEqualTo(ADMIN_DISPLAY);
        assertThat(unitCodeInDatabase(adminId)).as("被拒绝的编辑不能改归属单位").isEqualTo(UNIT_ALPHA);
        assertThat(passwordHashInDatabase(ADMIN_LOGIN)).as("被拒绝的编辑不能改密码")
                .isEqualTo(passwordHashBefore);
        assertThat(authorizationVersionInDatabase(ADMIN_LOGIN))
                .as("没有落库的编辑不应递增授权版本").isZero();
        assertThat(countEnabledAdmins()).isEqualTo(1);

        // 调用方仍是管理员：既没有丢掉角色，也没有被自己的请求踢出会话
        assertThat(currentSession(admin).getResponse().getStatus()).isEqualTo(200);
        assertThat(listUsers(admin, "keyword", LOGIN_NAME_PREFIX).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("存在第二个可用管理员时，摘掉其管理角色成功：角色关系真实替换，授权版本递增")
    void removingTheAdminRoleSucceedsWhenAnotherUsableAdminExists() throws Exception {
        MockHttpSession admin = adminSession();
        String secondAdminLogin = LOGIN_NAME_PREFIX + "admin.two";
        MvcResult created = sendJson(post(USERS_PATH).content(
                createBody(secondAdminLogin, "回归管理员二", UNIT_ALPHA,
                        List.of(ADMIN_ROLE_CODE, ANALYST_ROLE_CODE), RAW_PASSWORD)), admin);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        long secondAdminId = userIdInDatabase(secondAdminLogin);
        assertThat(countEnabledAdmins()).as("用例前提：现在有两个启用状态的管理员").isEqualTo(2);

        // 只摘掉管理角色、保留另一个角色：调用方仍是可用管理员，因此这次编辑应当成功
        MvcResult updated = sendJson(put(USERS_PATH + "/" + secondAdminId).content(
                updateBody("回归管理员二", UNIT_ALPHA, List.of(ANALYST_ROLE_CODE), null)), admin);

        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat((List<String>) jsonOf(bodyOf(updated), "$.data.roleCodes"))
                .containsExactly(ANALYST_ROLE_CODE);
        assertThat(roleCodesInDatabase(secondAdminId))
                .as("角色关系在库里真实替换，不是只加了新角色").containsExactly(ANALYST_ROLE_CODE);
        assertThat(statusInDatabase(secondAdminLogin)).as("这条路径只改角色，不动账号状态")
                .isEqualTo("ENABLED");
        assertThat(authorizationVersionInDatabase(secondAdminLogin))
                .as("角色变化必须递增授权版本").isEqualTo(1);
        assertThat(countEnabledAdmins()).as("摘掉角色后系统里仍剩一个可用管理员").isEqualTo(1);
    }

    @Test
    @DisplayName("所属单位被停用的管理员不算可用管理员：停用唯一另一个可用管理员返回 409，单位恢复后同一请求成功")
    void adminInDisabledUnitIsNotUsableSoTheLastUsableAdminCannotBeDisabled() throws Exception {
        MockHttpSession admin = adminSession();
        String secondAdminLogin = LOGIN_NAME_PREFIX + "admin.offunit";
        String secondAdminPassword = "regr-offunit-admin-secret";

        MvcResult created = sendJson(post(USERS_PATH).content(
                createBody(secondAdminLogin, "回归停用单位管理员", UNIT_BETA, List.of(ADMIN_ROLE_CODE),
                        secondAdminPassword)), admin);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        long secondAdminId = userIdInDatabase(secondAdminLogin);

        // 单位启用时他确实能登录：说明这条账号本身是"真正的管理员"，差别只在单位状态
        assertThat(login(secondAdminLogin, secondAdminPassword, issueCsrfToken(null),
                new MockHttpSession()).getResponse().getStatus()).isEqualTo(200);

        setUnitStatus(UNIT_BETA, "DISABLED");

        assertThat(statusInDatabase(secondAdminLogin)).as("被停用的是单位，账号本身仍是启用状态")
                .isEqualTo("ENABLED");
        assertThat(roleCodesInDatabase(secondAdminId)).as("角色也没被摘掉")
                .containsExactly(ADMIN_ROLE_CODE);
        assertThat(countEnabledAdmins())
                .as("只看账号状态时他仍在计数里，这正是修复前口径会算错的地方").isEqualTo(2);

        // 登录侧：单位停用后他与登录判定一致地不可用（口径对齐的参照物）
        assertError(login(secondAdminLogin, secondAdminPassword, issueCsrfToken(null),
                new MockHttpSession()), 403, "ACCOUNT_DISABLED");

        // 守卫侧：剩下的唯一另一个可用管理员（调用方自己）因此不能被停用
        MvcResult rejected = sendJson(post(USERS_PATH + "/disable").content(
                statusBody(userId(ADMIN_LOGIN))), admin);
        assertError(rejected, 409, "LAST_USER_ADMIN");
        assertThat(statusInDatabase(ADMIN_LOGIN)).as("被拒绝的停用不能改状态").isEqualTo("ENABLED");

        // 对照：单位恢复启用后同一个请求通过，说明差别只来自那一行的单位状态
        setUnitStatus(UNIT_BETA, "ENABLED");
        MvcResult disabled = sendJson(post(USERS_PATH + "/disable").content(
                statusBody(userId(ADMIN_LOGIN))), admin);
        assertThat(disabled.getResponse().getStatus()).isEqualTo(200);
        assertThat(statusInDatabase(ADMIN_LOGIN)).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("停用或启用不存在的用户返回 404 USER_NOT_FOUND，库中没有任何账号被改动")
    void disablingOrEnablingAnUnknownUserReturnsNotFound() throws Exception {
        MockHttpSession admin = adminSession();
        long unknownId = 987654321L;
        assertThat(userExists(unknownId)).as("用例前提：这个 id 在库里不存在").isFalse();

        MvcResult disable = sendJson(post(USERS_PATH + "/disable").content(statusBody(unknownId)), admin);
        assertError(disable, 404, "USER_NOT_FOUND");

        MvcResult enable = sendJson(post(USERS_PATH + "/enable").content(statusBody(unknownId)), admin);
        assertError(enable, 404, "USER_NOT_FOUND");

        assertThat(statusInDatabase(DISABLED_LOGIN)).as("批量动作失败不能顺手改到别的账号")
                .isEqualTo("DISABLED");
        assertThat(countSyntheticUsers()).isEqualTo(FIXTURE_COUNT);
    }

    @Test
    @DisplayName("编辑时账号不可改：请求体里塞入 loginName 不会改变库中的账号")
    void updateIgnoresLoginNameFieldInRequestBody() throws Exception {
        long analystId = userId(ANALYST_LOGIN);
        String injectedLogin = LOGIN_NAME_PREFIX + "injected";
        String renamedDisplay = ANALYST_DISPLAY + "（改）";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("version", 0);
        body.put("loginName", injectedLogin);
        body.put("displayName", renamedDisplay);
        body.put("unitCode", UNIT_ALPHA);
        body.put("roleCodes", List.of(ANALYST_ROLE_CODE));

        MvcResult updated = sendJson(put(USERS_PATH + "/" + analystId)
                .content(objectMapper.writeValueAsString(body)), adminSession());

        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat(loginNameInDatabase(analystId)).as("登录名必须保持原样").isEqualTo(ANALYST_LOGIN);
        assertThat(countUsersWithLoginName(injectedLogin)).as("不得凭空出现新账号").isZero();
        assertThat(displayNameInDatabase(ANALYST_LOGIN))
                .as("其余字段正常更新，说明请求确实被处理了").isEqualTo(renamedDisplay);
        assertThat(authorizationVersionInDatabase(ANALYST_LOGIN))
                .as("只有账号字段被忽略时不应递增授权版本").isZero();
    }

    @Test
    @DisplayName("单位与角色查询同属这道门：非管理员 403，管理员拿得到本用例插入的合成行")
    void unitAndRoleLookupsAreAdminOnly() throws Exception {
        MockHttpSession analystSession = signIn(ANALYST_LOGIN, RAW_PASSWORD);
        assertUnauthenticatedJson(getJson("/api/system/units", null));
        assertForbidden(getJson("/api/system/units", analystSession));
        assertForbidden(getJson("/api/system/roles", analystSession));

        MockHttpSession admin = adminSession();
        MvcResult units = getJson("/api/system/units", admin);
        assertThat(units.getResponse().getStatus()).isEqualTo(200);
        List<Map<String, Object>> unitRows = jsonOf(bodyOf(units), "$.data");
        assertThat(unitRows).anySatisfy(row -> {
            assertThat(row.get("code")).isEqualTo(UNIT_ALPHA);
            assertThat(row.get("name")).isEqualTo(UNIT_ALPHA_NAME);
            assertThat(row.get("status")).isEqualTo("ENABLED");
        });

        MvcResult roles = getJson("/api/system/roles", admin);
        assertThat(roles.getResponse().getStatus()).isEqualTo(200);
        List<Map<String, Object>> roleRows = jsonOf(bodyOf(roles), "$.data");
        assertThat(roleRows).anySatisfy(row -> {
            assertThat(row.get("code")).isEqualTo(ADMIN_ROLE_CODE);
            assertThat(row.get("name")).isEqualTo(ADMIN_ROLE_NAME);
        });

        // 停用的单位与角色也要出现在选项里（是否可选由新建/编辑时判定）
        assertThat(unitRows).anySatisfy(row -> {
            assertThat(row.get("code")).isEqualTo(DISABLED_UNIT_CODE);
            assertThat(row.get("status")).isEqualTo("DISABLED");
        });
        assertThat(roleRows).anySatisfy(row -> {
            assertThat(row.get("code")).isEqualTo(DISABLED_ROLE_CODE);
            assertThat(row.get("status")).isEqualTo("DISABLED");
        });
    }

    @Test
    @DisplayName("关键字含中文时按姓名命中：200 且只命中该账号（修复前这里会因 collation 冲突 500）")
    void chineseKeywordMatchesDisplayNameInsteadOfFailing() throws Exception {
        // 触发条件：关键字是非 ASCII 串，而它与 ascii_bin 的 login_name 一起参与 LIKE。
        // 修复前 MySQL 报 Illegal mix of collations (1267)，接口返回 500 INTERNAL_ERROR。
        assertThat(CHINESE_NAME_KEYWORD.chars().anyMatch(character -> character > 0x7F))
                .as("关键字必须真的含非 ASCII 字符，否则这条用例失去意义").isTrue();
        assertThat(CHINESE_LOGIN.chars().allMatch(character -> character < 0x80))
                .as("账号是纯 ASCII，命中只可能来自姓名").isTrue();

        MvcResult result = listUsers(adminSession(), "keyword", CHINESE_NAME_KEYWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = bodyOf(result);
        assertThat((String) jsonOf(body, "$.code")).isEqualTo("OK");
        assertThat(intOf(body, "$.data.total")).isEqualTo(1);
        assertThat(loginNamesOf(result)).containsExactly(CHINESE_LOGIN);
        assertThat((String) jsonOf(body, "$.data.items[0].displayName")).isEqualTo(CHINESE_DISPLAY);
    }

    @Test
    @DisplayName("登录接口收到非 ASCII 登录名返回 401 INVALID_CREDENTIALS，不是 500")
    void loginWithNonAsciiLoginNameIsRejectedAsInvalidCredentials() throws Exception {
        String nonAsciiLogin = "回归管理员";
        assertThat(nonAsciiLogin.chars().anyMatch(character -> character > 0x7F)).isTrue();

        // 修复前：login_name 的等值比较触发 collation 冲突（1267），登录接口 500
        assertError(login(nonAsciiLogin, RAW_PASSWORD, issueCsrfToken(null), new MockHttpSession()),
                401, "INVALID_CREDENTIALS");

        // 已存在账号 + 非 ASCII 后缀：同样只能是 401，不能因为字符集差异变成服务故障
        assertError(login(CHINESE_LOGIN + "回归", RAW_PASSWORD, issueCsrfToken(null),
                new MockHttpSession()), 401, "INVALID_CREDENTIALS");

        // 对照：同一密码配正确账号可以登录，说明上面的 401 来自账号名本身
        assertThat(login(ANALYST_LOGIN, RAW_PASSWORD, issueCsrfToken(null), new MockHttpSession())
                .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("登录名仍大小写敏感：全大写的同名账号返回 401，小写原名可以登录")
    void loginNameRemainsCaseSensitiveAfterCollationFix() throws Exception {
        String upperCaseLogin = ADMIN_LOGIN.toUpperCase(Locale.ROOT);
        assertThat(upperCaseLogin).isNotEqualTo(ADMIN_LOGIN);

        // ascii_bin 的大小写敏感语义不能被 CONVERT ... COLLATE 改掉
        assertError(login(upperCaseLogin, RAW_PASSWORD, issueCsrfToken(null), new MockHttpSession()),
                401, "INVALID_CREDENTIALS");
        assertThat(countUsersWithLoginName(upperCaseLogin)).as("库里只有小写账号").isZero();

        assertThat(login(ADMIN_LOGIN, RAW_PASSWORD, issueCsrfToken(null), new MockHttpSession())
                .getResponse().getStatus()).as("小写原名必须能登录").isEqualTo(200);
    }

    @Test
    @DisplayName("单位与角色筛选传非 ASCII 编码返回 200 且结果为空，不是 500")
    void nonAsciiUnitAndRoleFiltersReturnEmptyInsteadOfFailing() throws Exception {
        MockHttpSession admin = adminSession();
        String nonAsciiCode = "回归单位甲";
        assertThat(nonAsciiCode.chars().anyMatch(character -> character > 0x7F)).isTrue();

        // 修复前：等值比较的 collation 冲突同样会让这两条筛选 500
        MvcResult byUnit = listUsers(admin, "keyword", LOGIN_NAME_PREFIX, "unitCode", nonAsciiCode);
        assertThat(byUnit.getResponse().getStatus()).isEqualTo(200);
        assertThat(intOf(bodyOf(byUnit), "$.data.total")).isZero();
        assertThat(itemsOf(byUnit)).isEmpty();

        MvcResult byRole = listUsers(admin, "keyword", LOGIN_NAME_PREFIX, "roleCode", nonAsciiCode);
        assertThat(byRole.getResponse().getStatus()).isEqualTo(200);
        assertThat(intOf(bodyOf(byRole), "$.data.total")).isZero();
        assertThat(itemsOf(byRole)).isEmpty();

        // 对照：同一筛选传真实编码仍能命中，说明不是筛选整体失效
        assertThat(loginNamesOf(listUsers(admin, "keyword", LOGIN_NAME_PREFIX,
                "unitCode", UNIT_BETA))).hasSize(5);
        assertThat(loginNamesOf(listUsers(admin, "keyword", LOGIN_NAME_PREFIX,
                "roleCode", EDITOR_ROLE_CODE))).containsExactly(EDITOR_LOGIN);
    }

    @Test
    @DisplayName("单位已停用：新建与编辑用户都返回 400 UNIT_DISABLED，且没有写入")
    void creatingOrUpdatingUserWithDisabledUnitIsRejected() throws Exception {
        MockHttpSession admin = adminSession();
        String attemptedLogin = LOGIN_NAME_PREFIX + "offunit";
        long analystId = userId(ANALYST_LOGIN);

        MvcResult created = sendJson(post(USERS_PATH).content(
                createBody(attemptedLogin, "回归停用单位", DISABLED_UNIT_CODE,
                        List.of(ANALYST_ROLE_CODE), RAW_PASSWORD)), admin);
        assertError(created, 400, "UNIT_DISABLED");
        assertThat(countUsersWithLoginName(attemptedLogin)).as("被拒绝的新建不能落库").isZero();

        MvcResult updated = sendJson(put(USERS_PATH + "/" + analystId).content(
                updateBody(ANALYST_DISPLAY, DISABLED_UNIT_CODE, List.of(ANALYST_ROLE_CODE), null)),
                admin);
        assertError(updated, 400, "UNIT_DISABLED");
        assertThat(unitCodeInDatabase(analystId)).as("被拒绝的编辑不能改归属").isEqualTo(UNIT_ALPHA);
        assertThat(authorizationVersionInDatabase(ANALYST_LOGIN)).isZero();
    }

    @Test
    @DisplayName("角色已停用：新建与编辑用户都返回 400 ROLE_DISABLED，且角色关系不变")
    void creatingOrUpdatingUserWithDisabledRoleIsRejected() throws Exception {
        MockHttpSession admin = adminSession();
        String attemptedLogin = LOGIN_NAME_PREFIX + "offrole";
        long analystId = userId(ANALYST_LOGIN);

        MvcResult created = sendJson(post(USERS_PATH).content(
                createBody(attemptedLogin, "回归停用角色", UNIT_ALPHA, List.of(DISABLED_ROLE_CODE),
                        RAW_PASSWORD)), admin);
        assertError(created, 400, "ROLE_DISABLED");
        assertThat(countUsersWithLoginName(attemptedLogin)).as("被拒绝的新建不能落库").isZero();

        MvcResult updated = sendJson(put(USERS_PATH + "/" + analystId).content(
                updateBody(ANALYST_DISPLAY, UNIT_ALPHA, List.of(DISABLED_ROLE_CODE), null)), admin);
        assertError(updated, 400, "ROLE_DISABLED");
        assertThat(roleCodesInDatabase(analystId)).as("被拒绝的编辑不能改角色")
                .containsExactly(ANALYST_ROLE_CODE);
        assertThat(authorizationVersionInDatabase(ANALYST_LOGIN)).isZero();
    }

    @Test
    @DisplayName("单位停用后：该单位已有账号重新登录 403 ACCOUNT_DISABLED，已有会话下一次请求即 401；单位恢复后能正常登录")
    void disabledUnitBlocksExistingAccountsSessionAndRelogin() throws Exception {
        MockHttpSession admin = adminSession();
        String loginName = LOGIN_NAME_PREFIX + "offunit";
        String password = "regr-offunit-secret";

        // 先在启用状态的停用单位下把账号建好：账号本身始终是启用的，
        // 后面被拦住的只可能是单位状态。账号建在本用例内，命名空间清理会带走它。
        setUnitStatus(DISABLED_UNIT_CODE, "ENABLED");
        MvcResult created = sendJson(post(USERS_PATH).content(
                createBody(loginName, "回归停用单位账号", DISABLED_UNIT_CODE,
                        List.of(ANALYST_ROLE_CODE), password)), admin);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);

        // 单位启用时能正常登录并拿到会话，作为后面失效判定的对照
        MockHttpSession session = signIn(loginName, password);
        assertThat(currentSession(session).getResponse().getStatus()).isEqualTo(200);

        setUnitStatus(DISABLED_UNIT_CODE, "DISABLED");

        // AccountStateFilter 的失效判定同时看账号与所属单位状态
        MvcResult afterDisable = currentSession(session);
        assertThat(afterDisable.getResponse().getStatus()).isEqualTo(401);
        assertThat((String) jsonOf(bodyOf(afterDisable), "$.code")).isEqualTo("UNAUTHENTICATED");
        assertThat(session.isInvalid()).as("单位停用必须作废已有会话").isTrue();

        assertThat(statusInDatabase(loginName)).as("账号本身没有被停用，拦住它的是单位状态")
                .isEqualTo("ENABLED");
        assertError(login(loginName, password, issueCsrfToken(null), new MockHttpSession()),
                403, "ACCOUNT_DISABLED");

        // 对照组：单位恢复启用后同一账号能正常登录，说明上面的 403 来自单位状态
        setUnitStatus(DISABLED_UNIT_CODE, "ENABLED");
        assertThat(login(loginName, password, issueCsrfToken(null), new MockHttpSession())
                .getResponse().getStatus()).isEqualTo(200);
    }
}
