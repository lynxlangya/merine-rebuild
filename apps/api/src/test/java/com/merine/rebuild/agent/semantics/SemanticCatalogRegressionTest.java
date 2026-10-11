package com.merine.rebuild.agent.semantics;

import static org.assertj.core.api.Assertions.assertThat;

import com.merine.rebuild.agent.semantics.dto.SemanticCatalogView;
import com.merine.rebuild.agent.semantics.dto.SemanticCatalogView.DefinitionStatus;
import com.merine.rebuild.system.security.PermissionCodes;
import com.merine.rebuild.system.security.SystemAdminRegressionSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 复用真实 Session/权限与隔离测试库，接口返回实际固定目录，不替换读取服务。 */
class SemanticCatalogRegressionTest extends SystemAdminRegressionSupport {
    private static final String URL = "/api/agent/semantics";
    private static final String READER_ROLE = "REGR-SEMANTIC-READER";

    @Autowired
    SemanticCatalogService catalog;

    @AfterEach
    void cleanupReaderRole() {
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE role_id IN (SELECT id FROM sys_role WHERE role_code=?)",
                READER_ROLE);
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id IN (SELECT id FROM sys_role WHERE role_code=?)",
                READER_ROLE);
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code=?", READER_ROLE);
    }

    @Test
    void anonymousRequestCannotReadDefinitions() throws Exception {
        assertUnauthenticatedJson(getJson(URL, null));
    }

    @Test
    void authenticatedUserWithoutChatPermissionCannotReadDefinitions() throws Exception {
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id=?", adminUserId);
        var response = getJson(URL, signIn(ADMIN_LOGIN, ADMIN_PASSWORD));
        assertError(response, 403, "FORBIDDEN");
        assertThat(bodyOf(response)).doesNotContain("task.completed-count", "schemaVersion");
    }

    @Test
    void chatPermissionAloneCanReadExactCatalogWithoutBusinessRecords() throws Exception {
        jdbcTemplate.update("INSERT INTO sys_role(role_code,role_name,status) VALUES(?,?,'ENABLED')",
                READER_ROLE, "回归资料库阅读角色");
        long roleId = jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE role_code=?", Long.class,
                READER_ROLE);
        jdbcTemplate.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT ?,id FROM sys_permission WHERE permission_code=?",
                roleId, PermissionCodes.CHAT_USE);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id=?", adminUserId);
        jdbcTemplate.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", adminUserId, roleId);
        var response = getJson(URL, signIn(ADMIN_LOGIN, ADMIN_PASSWORD));
        assertThat(response.getResponse().getStatus()).isEqualTo(200);
        var actual = objectMapper.treeToValue(objectMapper.readTree(bodyOf(response)).path("data"),
                SemanticCatalogView.class);
        assertThat(actual).isEqualTo(catalog.catalog());
        var metric = actual.topics().getFirst().metrics().getFirst();
        assertThat(actual.revision()).isEqualTo(3);
        assertThat(metric.revision()).isEqualTo(3);
        assertThat(metric.status()).isEqualTo(DefinitionStatus.CONFIRMED);
        assertThat(metric.pendingQuestions()).isEmpty();
        assertThat((String) jsonOf(bodyOf(response), "$.requestId")).isNotBlank();
    }
}
