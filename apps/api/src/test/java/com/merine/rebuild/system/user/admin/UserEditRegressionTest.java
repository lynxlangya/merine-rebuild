package com.merine.rebuild.system.user.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.system.user.admin.dto.UserRequests;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;

/** 跨请求的账号编辑与会话不变量，连接与事务由真实应用管理。 */
class UserEditRegressionTest extends UserAdminRegressionSupport {
    @Autowired
    private UserAdminService service;

    @Test
    void disableThenEnableCannotReviveAnIdleSession() throws Exception {
        var admin = adminSession();
        var idleSession = signIn(ANALYST_LOGIN, RAW_PASSWORD);
        long id = userId(ANALYST_LOGIN);

        var disabled = sendJson(post(USERS_PATH + "/disable").content(statusBody(id)), admin);
        assertThat(disabled.getResponse().getStatus()).isEqualTo(200);
        assertThat(authorizationVersionInDatabase(ANALYST_LOGIN)).isEqualTo(1);
        int disabledVersion = intOf(bodyOf(disabled), "$.data[0].version");

        // 重复停用不再次递增；账号在停用期间没有发出任何请求。
        var repeated = sendJson(post(USERS_PATH + "/disable").content(statusBody(id)), admin);
        assertThat(repeated.getResponse().getStatus()).isEqualTo(200);
        assertThat(intOf(bodyOf(repeated), "$.data[0].version")).isEqualTo(disabledVersion);
        var enabled = sendJson(post(USERS_PATH + "/enable").content(statusBody(id)), admin);
        assertThat(enabled.getResponse().getStatus()).isEqualTo(200);

        assertError(currentSession(idleSession), 401, "UNAUTHENTICATED");
        assertThat(idleSession.isInvalid()).isTrue();
        assertThat(currentSession(signIn(ANALYST_LOGIN, RAW_PASSWORD)).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void staleFormCannotRestoreRevokedRolesAndStaleResetIsRejected() throws Exception {
        var admin = adminSession();
        long id = userId(ANALYST_LOGIN);
        var opened = getJson(USERS_PATH + "/" + id, admin);
        int version = intOf(bodyOf(opened), "$.data.version");
        String originalHash = passwordHashInDatabase(ANALYST_LOGIN);

        var changed = sendJson(put(USERS_PATH + "/" + id).content(
                updateBody(ANALYST_DISPLAY, UNIT_ALPHA, List.of(EDITOR_ROLE_CODE), version)), admin);
        assertThat(changed.getResponse().getStatus()).isEqualTo(200);
        var stale = sendJson(put(USERS_PATH + "/" + id).content(
                updateBody("旧表单姓名", UNIT_BETA, List.of(ANALYST_ROLE_CODE), version)), admin);
        assertError(stale, 409, "USER_VERSION_CONFLICT");
        // 密码是独立动作：拿着过期版本重置密码同样被拒绝，credential 不会被覆盖
        var staleReset = sendJson(post(USERS_PATH + "/" + id + "/reset-password").content(
                resetPasswordBody(version, "stale-reset-secret")), admin);
        assertError(staleReset, 409, "USER_VERSION_CONFLICT");
        assertThat(roleCodesInDatabase(id)).containsExactly(EDITOR_ROLE_CODE);
        assertThat(displayNameInDatabase(ANALYST_LOGIN)).isEqualTo(ANALYST_DISPLAY);
        assertThat(unitCodeInDatabase(id)).isEqualTo(UNIT_ALPHA);
        assertThat(passwordHashInDatabase(ANALYST_LOGIN)).isEqualTo(originalHash);
        assertThat(authorizationVersionInDatabase(ANALYST_LOGIN)).isEqualTo(1);

        var retry = sendJson(put(USERS_PATH + "/" + id).content(updateBody("核对后的姓名", UNIT_ALPHA,
                List.of(EDITOR_ROLE_CODE), intOf(bodyOf(changed), "$.data.version"))), admin);
        assertThat(retry.getResponse().getStatus()).isEqualTo(200);
        assertThat(displayNameInDatabase(ANALYST_LOGIN)).isEqualTo("核对后的姓名");
    }

    @Test
    void anEditVersionIsRequiredAndStatusChangesMakeOldFormsStale() throws Exception {
        var admin = adminSession();
        long id = userId(ANALYST_LOGIN);
        var missing = sendJson(put(USERS_PATH + "/" + id).content(objectMapper.writeValueAsString(Map.of(
                "displayName", ANALYST_DISPLAY, "unitCode", UNIT_ALPHA,
                "roleCodes", List.of(ANALYST_ROLE_CODE)))), admin);
        assertValidationError(missing, "version");
        assertThat(sendJson(post(USERS_PATH + "/disable").content(statusBody(id)), admin)
                .getResponse().getStatus()).isEqualTo(200);
        assertError(sendJson(put(USERS_PATH + "/" + id).content(
                updateBody("过期姓名", UNIT_ALPHA, List.of(ANALYST_ROLE_CODE))), admin),
                409, "USER_VERSION_CONFLICT");
    }

    @Test
    void concurrentSavesOfOneVersionCommitExactlyOneWholeEdit() throws Exception {
        long id = userId(ANALYST_LOGIN);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> save = () -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                try {
                    service.update(id, new UserRequests.UpdateUser(0, "并发编辑", UNIT_BETA,
                            List.of(EDITOR_ROLE_CODE)));
                    return true;
                } catch (ApiException conflict) {
                    assertThat(conflict.code()).isEqualTo("USER_VERSION_CONFLICT");
                    return false;
                }
            };
            var first = executor.submit(save);
            var second = executor.submit(save);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(service.get(id).version()).isEqualTo(1);
        assertThat(roleCodesInDatabase(id)).containsExactly(EDITOR_ROLE_CODE);
        assertThat(unitCodeInDatabase(id)).isEqualTo(UNIT_BETA);
        assertThat(authorizationVersionInDatabase(ANALYST_LOGIN)).isEqualTo(1);
    }

    @Test
    void passwordsOverTheUtf8LimitReturnFieldErrorsWithoutWriting() throws Exception {
        var admin = adminSession();
        long id = userId(ANALYST_LOGIN);
        String hash = passwordHashInDatabase(ANALYST_LOGIN);
        for (String password : List.of("x".repeat(73), "字".repeat(25), "😀".repeat(19))) {
            assertValidationError(sendJson(post(USERS_PATH).content(createBody(
                    LOGIN_NAME_PREFIX + "overlimit", "长度边界", UNIT_ALPHA,
                    List.of(ANALYST_ROLE_CODE), password)), admin), "password");
            assertValidationError(sendJson(post(USERS_PATH + "/" + id + "/reset-password").content(
                    resetPasswordBody(0, password)), admin), "newPassword");
            assertValidationError(login(ANALYST_LOGIN, password, issueCsrfToken(null),
                    new MockHttpSession()), "password");
        }
        assertThat(countUsersWithLoginName(LOGIN_NAME_PREFIX + "overlimit")).isZero();
        assertThat(passwordHashInDatabase(ANALYST_LOGIN)).isEqualTo(hash);
        assertThat(service.get(id).version()).isZero();
    }

    @Test
    void passwordsAtTheUtf8LimitCanBeCreatedResetAndUsedForLogin() throws Exception {
        var admin = adminSession();
        String loginName = LOGIN_NAME_PREFIX + "boundary";
        String ascii = "x".repeat(72);
        var created = sendJson(post(USERS_PATH).content(createBody(loginName, "长度边界", UNIT_ALPHA,
                List.of(ANALYST_ROLE_CODE), ascii)), admin);
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        var session = signIn(loginName, ascii);
        long id = userIdInDatabase(loginName);
        String unicode = "字".repeat(24);
        var reset = sendJson(put(USERS_PATH + "/" + id).content(updateBody("长度边界", UNIT_ALPHA,
                List.of(ANALYST_ROLE_CODE), intOf(bodyOf(created), "$.data.version"))), admin);
        assertThat(reset.getResponse().getStatus()).isEqualTo(200);
        var resetPassword = sendJson(post(USERS_PATH + "/" + id + "/reset-password").content(
                resetPasswordBody(intOf(bodyOf(reset), "$.data.version"), unicode)), admin);
        assertThat(resetPassword.getResponse().getStatus()).isEqualTo(200);
        assertError(currentSession(session), 401, "UNAUTHENTICATED");
        assertThat(currentSession(signIn(loginName, unicode)).getResponse().getStatus()).isEqualTo(200);
    }
}
