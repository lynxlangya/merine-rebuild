package com.merine.rebuild.agent.provider;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import com.merine.rebuild.agent.provider.dto.ProviderRequests.ReasoningEffort;
import com.merine.rebuild.agent.provider.dto.ProviderRequests.Vendor;
import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.system.security.SystemAdminRegressionSupport;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 「获取模型列表」的隔离回归。
 *
 * 出站调用本身不做自动化验证：接口层只接 HTTPS，客户端又拒绝本机与内网 IP 字面量，
 * 隔离环境里没有可用的 TLS 目标。因此这里覆盖「出站前」的全部判定（权限、地址、密钥、解析），
 * 真实上游连通性按需人工验证，避免测试依赖外部服务。
 */
@TestPropertySource(properties =
        "merine.agent.provider-encryption-key=000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
class ProviderDiscoveryRegressionTest extends SystemAdminRegressionSupport {
    private static final String URL = "/api/agent/providers/models/discover";
    private static final String KEY = "synthetic-discovery-key";
    @Autowired ProviderService service;

    @BeforeEach void resetProviders() {
        jdbcTemplate.update("DELETE FROM ai_model_provider WHERE name LIKE 'REGR-DISCOVERY%'");
    }
    @AfterEach void cleanup() {
        resetProviders();
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE role_id IN (SELECT id FROM sys_role WHERE role_code='REGR-DISCOVERY-VIEW')");
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id IN (SELECT id FROM sys_role WHERE role_code='REGR-DISCOVERY-VIEW')");
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code='REGR-DISCOVERY-VIEW'");
    }
    Map<String,Object> request(String baseUrl, String apiKey) {
        Map<String,Object> data = new HashMap<>();
        data.put("baseUrl", baseUrl);
        data.put("apiKey", apiKey);
        data.put("vendor", "DEEPSEEK");
        return data;
    }
    MvcResult discover(MockHttpSession session, Map<String,Object> data) throws Exception {
        return sendJson(post(URL).content(objectMapper.writeValueAsString(data)), session);
    }

    @Test void rejectsUnsafeTargetsAndMissingKeyBeforeAnyOutboundCall() throws Exception {
        var session = adminSession();
        assertValidationError(discover(session, request("http://api.example.com", KEY)), "baseUrl");
        assertValidationError(discover(session, request("https://api.example.com/chat/completions", KEY)), "baseUrl");
        assertValidationError(discover(session, request("https://api.example.com", "")), "apiKey");
        assertValidationError(discover(session, request("https://api.example.com", "key with space")), "apiKey");
        for (String target : List.of("https://127.0.0.1:8443/v1", "https://localhost/v1", "https://[::1]/v1", "https://10.0.0.8/v1")) {
            var result = discover(session, request(target, KEY));
            assertError(result, 400, "MODEL_LIST_BLOCKED_TARGET");
            String field = jsonOf(bodyOf(result), "$.fieldErrors[0].field");
            assertThat(field).isEqualTo("baseUrl");
        }
        // 连接不存在时按未找到处理，不会用空密钥发出站请求
        var missing = request("https://api.example.com", "");
        missing.put("providerId", UUID.randomUUID().toString());
        assertError(discover(session, missing), 404, "PROVIDER_NOT_FOUND");
    }

    @Test void savedConnectionReusesItsStoredKey() throws Exception {
        var session = adminSession();
        Map<String,Object> input = new HashMap<>(Map.of("vendor", "DEEPSEEK",
                "name", "REGR-DISCOVERY-STORED", "remark", "隔离回归配置",
                "website", "https://platform.deepseek.com", "baseUrl", "https://api.deepseek.com",
                "apiKey", KEY, "status", "ENABLED"));
        var created = sendJson(post("/api/agent/providers").content(objectMapper.writeValueAsString(input)), session);
        String id = jsonOf(bodyOf(created), "$.data.id");
        // 表单未填新密钥时服务端取已保存的那份；这里直接读解析结果，不真的出站。
        assertThat(service.storedKey(id)).isEqualTo(KEY);
        String encrypted = jdbcTemplate.queryForObject(
                "SELECT api_key_cipher FROM ai_model_provider WHERE id=?", String.class, id);
        assertThat(encrypted).startsWith("v1.").doesNotContain(KEY);
    }

    @Test void requiresCreateForNewAndUpdateForSavedConnection() throws Exception {
        jdbcTemplate.update("INSERT INTO sys_role(role_code,role_name,status) VALUES('REGR-DISCOVERY-VIEW','模型清单权限回归','ENABLED')");
        long role = jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE role_code='REGR-DISCOVERY-VIEW'", Long.class);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id=?", adminUserId);
        jdbcTemplate.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", adminUserId, role);
        jdbcTemplate.update("""
                INSERT INTO sys_role_permission(role_id,permission_id)
                SELECT ?,id FROM sys_permission WHERE permission_code IN ('agent:provider:read','agent:provider:create')
                """, role);

        var creator = signIn(ADMIN_LOGIN, ADMIN_PASSWORD);
        // 只给新增权限：新建场景放行到地址校验，已保存场景（需要编辑权限）被拦截
        assertValidationError(discover(creator, request("http://api.example.com", KEY)), "baseUrl");
        Map<String,Object> saved = request("https://api.example.com", KEY);
        saved.put("providerId", UUID.randomUUID().toString());
        assertForbidden(discover(creator, saved));

        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id=?", role);
        var readOnly = signIn(ADMIN_LOGIN, ADMIN_PASSWORD);
        assertForbidden(discover(readOnly, request("https://api.example.com", KEY)));
        Map<String,Object> denied = request("https://api.example.com", KEY);
        denied.put("providerId", UUID.randomUUID().toString());
        assertForbidden(discover(readOnly, denied));
    }

    @Test void parsesOpenAiCompatibleCatalogWithEffortDirectory() {
        var parsed = ProviderCatalogClient.parse(Vendor.DEEPSEEK, objectMapper,
                "{\"object\":\"list\",\"data\":[{\"id\":\"deepseek-flash\",\"owned_by\":\"deepseek\"},{\"id\":\"deepseek-v4-pro\"},{\"id\":\"deepseek-flash\"}]}");
        assertThat(parsed.models()).hasSize(2);
        assertThat(parsed.models().getFirst().modelId()).isEqualTo("deepseek-flash");
        assertThat(parsed.models().getFirst().ownedBy()).isEqualTo("deepseek");
        assertThat(parsed.models().get(1).ownedBy()).isNull();
        assertThat(parsed.models().getFirst().reasoningEfforts())
                .isEqualTo(List.of(ReasoningEffort.NONE, ReasoningEffort.LOW, ReasoningEffort.HIGH, ReasoningEffort.MAX));

        for (String body : List.of("not json", "{\"object\":\"list\"}", "{\"data\":[]}", "{\"data\":[{\"id\":\"  \"}]}")) {
            assertThatThrownBy(() -> ProviderCatalogClient.parse(Vendor.DEEPSEEK, objectMapper, body))
                    .isInstanceOf(ApiException.class)
                    .extracting(error -> ((ApiException) error).code())
                    .isEqualTo("MODEL_LIST_PROTOCOL_ERROR");
        }
    }

    @Test void effortDirectoryFollowsOfficialValuesOnly() {
        // DeepSeek：none 关闭思考 + 三档强度，默认 high
        assertThat(ReasoningEffortCatalog.effortsFor(Vendor.DEEPSEEK, "deepseek-v4-pro"))
                .isEqualTo(List.of(ReasoningEffort.NONE, ReasoningEffort.LOW, ReasoningEffort.HIGH, ReasoningEffort.MAX));
        assertThat(ReasoningEffortCatalog.defaultFor(Vendor.DEEPSEEK, "deepseek-flash"))
                .isEqualTo(ReasoningEffort.HIGH);

        // 千问 Qwen3.8 系列：low/medium/xhigh，默认 xhigh（文档未列 none/high/max）
        assertThat(ReasoningEffortCatalog.effortsFor(Vendor.QWEN, "qwen3.8-flash"))
                .isEqualTo(List.of(ReasoningEffort.LOW, ReasoningEffort.MEDIUM, ReasoningEffort.XHIGH));
        assertThat(ReasoningEffortCatalog.defaultFor(Vendor.QWEN, "qwen3.8-max"))
                .isEqualTo(ReasoningEffort.XHIGH);
        assertThat(ReasoningEffortCatalog.effortsFor(Vendor.QWEN, "qwen3.8-2.4t-a95b")).isEmpty();
        // 实时/翻译等非 Chat Completions 形态不给档位
        assertThat(ReasoningEffortCatalog.effortsFor(Vendor.QWEN, "qwen3.8-omni-flash-realtime")).isEmpty();

        // 千问上的第三方直供模型：glm-5.3 与 kimi-k3 三档、默认 max；月之暗面直供 kimi 仅 max
        assertThat(ReasoningEffortCatalog.effortsFor(Vendor.QWEN, "glm-5.3"))
                .isEqualTo(List.of(ReasoningEffort.LOW, ReasoningEffort.HIGH, ReasoningEffort.MAX));
        assertThat(ReasoningEffortCatalog.defaultFor(Vendor.QWEN, "kimi-k3"))
                .isEqualTo(ReasoningEffort.MAX);
        assertThat(ReasoningEffortCatalog.effortsFor(Vendor.QWEN, "kimi/kimi-k3"))
                .isEqualTo(List.of(ReasoningEffort.MAX));

        // 千问上的 DeepSeek 直供：默认版本 high/max，带日期版本与 4.1-flash 三档
        assertThat(ReasoningEffortCatalog.effortsFor(Vendor.QWEN, "deepseek-v4-pro"))
                .isEqualTo(List.of(ReasoningEffort.HIGH, ReasoningEffort.MAX));
        assertThat(ReasoningEffortCatalog.effortsFor(Vendor.QWEN, "deepseek-v4-pro-0813"))
                .isEqualTo(List.of(ReasoningEffort.LOW, ReasoningEffort.HIGH, ReasoningEffort.MAX));

        // 文档未给取值的系列与其他供应商：留空，不猜
        assertThat(ReasoningEffortCatalog.effortsFor(Vendor.QWEN, "qwen3.7-flash")).isEmpty();
        assertThat(ReasoningEffortCatalog.effortsFor(Vendor.MOONSHOT, "kimi-k2")).isEmpty();
        assertThat(ReasoningEffortCatalog.defaultFor(Vendor.QWEN, "qwen3.7-flash")).isNull();
    }

    @Test void effortCatalogEndpointAnswersPerModelAndKeepsReadOnlySpheres() throws Exception {
        var session = adminSession();
        var body = Map.of("vendor", "QWEN", "modelIds", List.of(
                "qwen3.8-flash", "glm-5.3", "qwen3.7-flash", "kimi/kimi-k3"));
        var result = sendJson(post("/api/agent/providers/reasoning-efforts")
                .content(objectMapper.writeValueAsString(body)), session);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String response = bodyOf(result);
        assertThat((Boolean) jsonOf(response, "$.data.covered")).isTrue();
        List<Map<String, Object>> models = jsonOf(response, "$.data.models");
        assertThat(models).hasSize(4);
        assertThat(models.getFirst().get("reasoningEfforts"))
                .isEqualTo(List.of("LOW", "MEDIUM", "XHIGH"));
        assertThat(models.getFirst().get("defaultReasoningEffort")).isEqualTo("XHIGH");
        assertThat(models.get(1).get("reasoningEfforts"))
                .isEqualTo(List.of("LOW", "HIGH", "MAX"));
        assertThat(models.get(1).get("defaultReasoningEffort")).isEqualTo("MAX");
        assertThat(models.get(2).get("reasoningEfforts")).isEqualTo(List.of());
        assertThat(models.get(2).get("defaultReasoningEffort")).isNull();
        assertThat(models.get(3).get("reasoningEfforts")).isEqualTo(List.of("MAX"));

        // 未整理官方目录的供应商：covered=false，界面保留自行选择
        var custom = sendJson(post("/api/agent/providers/reasoning-efforts")
                .content(objectMapper.writeValueAsString(Map.of("vendor", "CUSTOM",
                        "modelIds", List.of("local-llm")))), session);
        assertThat((Boolean) jsonOf(bodyOf(custom), "$.data.covered")).isFalse();
        assertThat((List<?>) jsonOf(bodyOf(custom), "$.data.models[0].reasoningEfforts")).isEmpty();

        // 只读角色可以查目录；没有该权限的角色被拒绝
        jdbcTemplate.update("INSERT INTO sys_role(role_code,role_name,status) VALUES('REGR-DISCOVERY-VIEW','模型清单权限回归','ENABLED')");
        long role = jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE role_code='REGR-DISCOVERY-VIEW'", Long.class);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id=?", adminUserId);
        jdbcTemplate.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)", adminUserId, role);
        var denied = signIn(ADMIN_LOGIN, ADMIN_PASSWORD);
        assertForbidden(sendJson(post("/api/agent/providers/reasoning-efforts")
                .content(objectMapper.writeValueAsString(body)), denied));
        jdbcTemplate.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT ?,id FROM sys_permission WHERE permission_code='agent:provider:read'", role);
        var viewer = signIn(ADMIN_LOGIN, ADMIN_PASSWORD);
        assertThat(sendJson(post("/api/agent/providers/reasoning-efforts")
                .content(objectMapper.writeValueAsString(body)), viewer).getResponse().getStatus()).isEqualTo(200);
    }

    @Test void decryptFailureIsReportedAsKeyUnavailable() {
        String id = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO ai_model_provider(id,vendor,name,remark,website,base_url,api_key_cipher,status)
                VALUES(?, 'DEEPSEEK', 'REGR-DISCOVERY-TAMPERED', '隔离回归配置', 'https://platform.deepseek.com',
                       'https://api.deepseek.com', 'v1.tampered', 'ENABLED')
                """, id);
        assertThatThrownBy(() -> service.storedKey(id))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo("MODEL_KEY_UNAVAILABLE");
    }
}
