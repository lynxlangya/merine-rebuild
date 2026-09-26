package com.merine.rebuild.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/** dev profile 的免密入口使用真实测试库，并复用正常会话与 CSRF 规则。 */
@ActiveProfiles(profiles = {"test", "dev"}, inheritProfiles = false)
@TestPropertySource(properties = "merine.security.dev-login-enabled=true")
class DevLoginRegressionTest extends AuthSessionRegressionSupport {

    @Test
    void accountChoicesExcludeDisabledAccountsAndPasswords() throws Exception {
        MvcResult listed = mockMvc.perform(get("/api/auth/dev/accounts")).andReturn();
        assertThat(listed.getResponse().getStatus()).isEqualTo(200);
        String body = bodyOf(listed);
        assertThat(body).contains(LOGIN_NAME, DISPLAY_NAME, UNIT_NAME)
                .doesNotContain(RAW_PASSWORD, passwordHashInDatabase(), "passwordHash");
        assertThat(((Number) jsonOf(body, "$.data[0].unitLevel")).intValue()).isEqualTo(1);

        updateAccountStatus("DISABLED");
        String afterDisable = bodyOf(mockMvc.perform(get("/api/auth/dev/accounts")).andReturn());
        assertThat(afterDisable).doesNotContain(LOGIN_NAME);
    }

    @Test
    void selectedAccountGetsNormalSessionWithoutPassword() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Cookie csrf = issueCsrfToken(session);
        String sessionIdBefore = session.getId();

        MvcResult result = mockMvc.perform(withCsrf(post("/api/auth/dev/session")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginName\":\"" + LOGIN_NAME + "\",\"rememberMe\":false}"), csrf)
                .session(session)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat((String) jsonOf(bodyOf(result), "$.data.loginName")).isEqualTo(LOGIN_NAME);
        assertThat(session.getId()).isNotEqualTo(sessionIdBefore);
        assertThat(currentSession(session).getResponse().getStatus()).isEqualTo(200);
        assertThat(mockMvc.perform(get("/api/bootstrap").session(session)).andReturn().getResponse().getStatus())
                .isEqualTo(200);

        updateAccountStatus("DISABLED");
        assertUnauthenticatedJson(currentSession(session));
    }

    @Test
    void csrfAndCurrentAccountStatusStillApply() throws Exception {
        String body = "{\"loginName\":\"" + LOGIN_NAME + "\"}";
        MvcResult noCsrf = mockMvc.perform(post("/api/auth/dev/session")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertThat(noCsrf.getResponse().getStatus()).isEqualTo(403);

        updateAccountStatus("DISABLED");
        Cookie csrf = issueCsrfToken(null);
        MvcResult disabled = mockMvc.perform(withCsrf(post("/api/auth/dev/session")
                .contentType(MediaType.APPLICATION_JSON).content(body), csrf)).andReturn();
        assertThat(disabled.getResponse().getStatus()).isEqualTo(403);
        assertThat((String) jsonOf(bodyOf(disabled), "$.code")).isEqualTo("ACCOUNT_DISABLED");
    }
}
