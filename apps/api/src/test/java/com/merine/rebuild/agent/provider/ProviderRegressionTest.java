package com.merine.rebuild.agent.provider;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import com.merine.rebuild.system.security.SystemAdminRegressionSupport;
import com.merine.rebuild.common.ApiException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

@TestPropertySource(properties="merine.agent.provider-encryption-key=000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
class ProviderRegressionTest extends SystemAdminRegressionSupport {
    private static final String URL="/api/agent/providers";
    private static final String KEY="synthetic-provider-key-for-local-tests";
    @Autowired ProviderKeyCipher cipher;
    @BeforeEach void resetProviders() { jdbcTemplate.update("DELETE FROM ai_model_provider WHERE name LIKE 'REGR-PROVIDER%'"); }
    @AfterEach void cleanup() {
        resetProviders();
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE role_id IN (SELECT id FROM sys_role WHERE role_code='REGR-PROVIDER-VIEW')");
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id IN (SELECT id FROM sys_role WHERE role_code='REGR-PROVIDER-VIEW')");
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code='REGR-PROVIDER-VIEW'");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE role_id IN (SELECT id FROM sys_role WHERE role_code='REGR-PROVIDER-USAGE')");
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id IN (SELECT id FROM sys_role WHERE role_code='REGR-PROVIDER-USAGE')");
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code='REGR-PROVIDER-USAGE'");
    }
    Map<String,Object> input(String name) {
        return new HashMap<>(Map.of("vendor","DEEPSEEK","name",name,"remark","隔离回归配置",
                "website","https://platform.deepseek.com","baseUrl","https://api.deepseek.com","apiKey",KEY,"status","ENABLED"));
    }
    MvcResult create(MockHttpSession session,Map<String,Object> data) throws Exception {
        return sendJson(post(URL).content(objectMapper.writeValueAsString(data)),session);
    }
    MvcResult update(MockHttpSession session,String id,Map<String,Object> data) throws Exception {
        return sendJson(put(URL+"/"+id).content(objectMapper.writeValueAsString(data)),session);
    }
    String idOf(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return jsonOf(bodyOf(result),"$.data.id");
    }
    String stored(String id) {return jdbcTemplate.queryForObject("SELECT api_key_cipher FROM ai_model_provider WHERE id=?",String.class,id);}
    @Test void roundTripNeverReturnsKeyAndEditingRetainsOrReplacesIt() throws Exception {
        var session=adminSession();var data=input("REGR-PROVIDER-ROUNDTRIP");var created=create(session,data);String id=idOf(created);
        assertThat(bodyOf(created)).doesNotContain(KEY,"apiKey","api_key_cipher");
        String encrypted=stored(id);assertThat(encrypted).startsWith("v1.").doesNotContain(KEY);
        assertThat(cipher.decrypt(id,encrypted)).isEqualTo(KEY);
        var listed=getJson(URL+"?search=REGR-PROVIDER-ROUNDTRIP",session);
        assertThat(intOf(bodyOf(listed),"$.data.total")).isEqualTo(1);
        assertThat(bodyOf(listed)).doesNotContain(KEY,"apiKey","api_key_cipher");
        data.put("version",0);data.put("apiKey","");data.put("remark","变更备注");
        assertThat(update(session,id,data).getResponse().getStatus()).isEqualTo(200);
        assertThat(stored(id)).isEqualTo(encrypted);
        assertError(update(session,id,data),409,"PROVIDER_CONFLICT");
        data.put("version",1);data.put("apiKey","synthetic-replacement");data.put("status","DISABLED");
        assertThat(update(session,id,data).getResponse().getStatus()).isEqualTo(200);
        assertThat(cipher.decrypt(id,stored(id))).isEqualTo("synthetic-replacement");
        assertError(sendJson(delete(URL+"/"+id+"?version=1"),session),409,"PROVIDER_CONFLICT");
        assertThat(sendJson(delete(URL+"/"+id+"?version=2"),session).getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_model_provider WHERE id=?",Integer.class,id)).isZero();
    }
    @Test void endpointChangeRequiresExplicitKeyAndInvalidInputNeverPersists() throws Exception {
        var session=adminSession();var data=input("REGR-PROVIDER-VALIDATION");String id=idOf(create(session,data));
        data.put("version",0);data.put("apiKey","");data.put("baseUrl","https://other.example.com/v1");
        assertValidationError(update(session,id,data),"apiKey");
        assertThat(cipher.decrypt(id,stored(id))).isEqualTo(KEY);
        assertThat(jdbcTemplate.queryForObject("SELECT base_url FROM ai_model_provider WHERE id=?",String.class,id)).isEqualTo("https://api.deepseek.com");
        data=input("REGR-PROVIDER-INVALID");data.put("apiKey","");assertValidationError(create(session,data),"apiKey");
        data.put("apiKey",KEY);data.put("baseUrl","https://api.deepseek.com/chat/completions");assertValidationError(create(session,data),"baseUrl");
        data.put("baseUrl","https://user:password@example.com/v1");assertValidationError(create(session,data),"baseUrl");
        data.put("baseUrl","https://example.com:99999");assertValidationError(create(session,data),"baseUrl");
        data.put("baseUrl","http://example.com");assertValidationError(create(session,data),"baseUrl");
        data.put("baseUrl","https://api.deepseek.com");data.put("website","javascript:alert(1)");assertValidationError(create(session,data),"website");
        assertError(create(session,input("REGR-PROVIDER-VALIDATION")),409,"PROVIDER_NAME_EXISTS");
        assertThat(getJson(URL+"?pageSize=1000",session).getResponse().getStatus()).isEqualTo(400);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_model_provider WHERE name='REGR-PROVIDER-INVALID'",Integer.class)).isZero();
    }
    @Test void usageModelOptionsAreLoginOnlyAndExposeEnabledModelsOnly() throws Exception {
        assertUnauthenticatedJson(getJson("/api/agent/models",null));
        var admin=adminSession();
        var on=input("REGR-PROVIDER-OPTION-ON");
        on.put("models",List.of(Map.of("modelId","regr-chat","displayName","回归对话","remark","","status","ENABLED"),
                Map.of("modelId","regr-off","displayName","","remark","","status","DISABLED")));
        String enabledId=idOf(create(admin,on));
        var off=input("REGR-PROVIDER-OPTION-OFF");off.put("status","DISABLED");
        off.put("models",List.of(Map.of("modelId","regr-hidden","displayName","隐藏模型","remark","","status","ENABLED")));
        String offId=idOf(create(admin,off));
        jdbcTemplate.update("INSERT INTO sys_role(role_code,role_name,status) VALUES('REGR-PROVIDER-USAGE','助手模型选项回归','ENABLED')");
        long role=jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE role_code='REGR-PROVIDER-USAGE'",Long.class);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id=?",adminUserId);
        jdbcTemplate.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)",adminUserId,role);
        var plain=signIn(ADMIN_LOGIN,ADMIN_PASSWORD);
        var result=getJson("/api/agent/models",plain);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(bodyOf(result)).contains("regr-chat","回归对话","REGR-PROVIDER-OPTION-ON")
                .doesNotContain("regr-off","regr-hidden","隐藏模型","REGR-PROVIDER-OPTION-OFF",KEY,"api.deepseek.com","隔离回归配置");
        List<?> visible=jsonOf(bodyOf(result),"$.data");
        assertThat(visible).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_model_provider_model WHERE provider_id IN (?,?)",Integer.class,enabledId,offId))
                .isEqualTo(3);
    }

    @Test void modelsRoundTripReplaceAndDeleteWithProvider() throws Exception {
        var session=adminSession();
        var data=input("REGR-PROVIDER-MODELS");
        data.put("models",List.of(
                Map.of("modelId","regr-chat","displayName","","remark","","status","ENABLED",
                        "reasoningEfforts",List.of("MAX","LOW")),
                Map.of("modelId","regr-reasoner","displayName","推理","remark","慢但更强","status","ENABLED",
                        "reasoningEfforts",List.of("NONE","HIGH"))));
        String id=idOf(create(session,data));
        var listed=getJson(URL+"?search=REGR-PROVIDER-MODELS",session);
        assertThat(bodyOf(listed)).contains("regr-chat","regr-reasoner","推理");
        // 空白显示名回填为模型标识，顺序按提交顺序写 sortOrder
        Map<String,Object> first=jsonOf(bodyOf(listed),"$.data.items[0]");
        List<Map<String,Object>> models=jsonOf(bodyOf(listed),"$.data.items[0].models");
        assertThat(models).hasSize(2);
        assertThat(models.getFirst().get("displayName")).isEqualTo("regr-chat");
        assertThat(models.getFirst().get("reasoningEfforts")).isEqualTo(List.of("LOW","MAX"));
        assertThat(models.getFirst().get("sortOrder")).isEqualTo(0);
        assertThat(models.get(1).get("sortOrder")).isEqualTo(10);
        assertThat((Object)first.get("id")).isEqualTo(id);

        // 替换：删一个、改一个、加一个，顺序按新列表
        List<Map<String,Object>> existing=jsonOf(bodyOf(listed),"$.data.items[0].models");
        String keepId=((Map<String,Object>)existing.get(1)).get("id").toString();
        data.put("version",0);data.put("apiKey","");
        data.put("models",List.of(
                Map.of("modelId","regr-reasoner","displayName","推理 V2","remark","","status","DISABLED",
                        "reasoningEfforts",List.of("HIGH")),
                Map.of("modelId","regr-new","displayName","新模型","remark","","status","ENABLED")));
        assertThat(update(session,id,data).getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForList("SELECT model_id FROM ai_model_provider_model WHERE provider_id=? ORDER BY sort_order",String.class,id))
                .containsExactly("regr-reasoner","regr-new");
        assertThat(jdbcTemplate.queryForObject("SELECT id FROM ai_model_provider_model WHERE provider_id=? AND model_id='regr-reasoner'",String.class,id))
                .isEqualTo(keepId);
        assertThat(jdbcTemplate.queryForList(
                "SELECT reasoning_efforts FROM ai_model_provider_model WHERE provider_id=? ORDER BY sort_order",String.class,id))
                .containsExactly("HIGH","");

        // 同一连接下的重复模型标识拒绝
        data.put("version",1);data.put("models",List.of(
                Map.of("modelId","regr-dup","displayName","","remark","","status","ENABLED"),
                Map.of("modelId","regr-dup","displayName","","remark","","status","ENABLED")));
        assertValidationError(update(session,id,data),"models");

        // 删除连接连带删除模型
        assertThat(sendJson(delete(URL+"/"+id+"?version=1"),session).getResponse().getStatus()).isEqualTo(200);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ai_model_provider_model WHERE provider_id=?",Integer.class,id)).isZero();
    }

    @Test void authCsrfAndReadOnlyRoleProtectAllWrites() throws Exception {
        assertUnauthenticatedJson(getJson(URL,null));var admin=adminSession();String id=idOf(create(admin,input("REGR-PROVIDER-AUTH")));
        assertThat(mockMvc.perform(post(URL).session(admin).contentType("application/json").content(objectMapper.writeValueAsString(input("REGR-PROVIDER-CSRF")))).andReturn().getResponse().getStatus()).isEqualTo(403);
        jdbcTemplate.update("INSERT INTO sys_role(role_code,role_name,status) VALUES('REGR-PROVIDER-VIEW','供应商只读回归','ENABLED')");
        long role=jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE role_code='REGR-PROVIDER-VIEW'",Long.class);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id=?",adminUserId);
        jdbcTemplate.update("INSERT INTO sys_user_role(user_id,role_id) VALUES(?,?)",adminUserId,role);
        var denied=signIn(ADMIN_LOGIN,ADMIN_PASSWORD);assertForbidden(getJson(URL,denied));
        assertThat(bodyOf(getJson("/api/me/menus",denied))).doesNotContain("agent.providers");
        jdbcTemplate.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT ?,id FROM sys_permission WHERE permission_code='agent:provider:read'",role);
        var viewer=signIn(ADMIN_LOGIN,ADMIN_PASSWORD);
        assertThat(getJson(URL,viewer).getResponse().getStatus()).isEqualTo(200);
        // 菜单已从后台下线：只读角色能调接口，但 /api/me/menus 不再出现 agent.providers
        assertThat(bodyOf(getJson("/api/me/menus",viewer))).doesNotContain("agent.providers");
        assertForbidden(create(viewer,input("REGR-PROVIDER-DENIED")));
        var data=input("REGR-PROVIDER-AUTH");data.put("version",0);assertForbidden(update(viewer,id,data));
        assertForbidden(sendJson(delete(URL+"/"+id+"?version=0"),viewer));
    }
    @Test void concurrentEditsHaveExactlyOneWinner() throws Exception {
        var first=adminSession();var second=adminSession();String id=idOf(create(first,input("REGR-PROVIDER-RACE")));
        var data=input("REGR-PROVIDER-RACE");data.put("version",0);data.put("apiKey","");
        var gate=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var a=executor.submit(()->{gate.await();return update(first,id,data).getResponse().getStatus();});
            var b=executor.submit(()->{gate.await();return update(second,id,data).getResponse().getStatus();});
            gate.countDown();assertThat(java.util.List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        }
    }
    @Test void encryptionRejectsWrongRecordTamperingAndMissingMasterKey() {
        String encrypted=cipher.encrypt("record-one",KEY);
        assertThat(cipher.encrypt("record-one",KEY)).isNotEqualTo(encrypted);
        assertThatThrownBy(()->cipher.decrypt("record-two",encrypted)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->cipher.decrypt("record-one",encrypted.substring(0,encrypted.length()-6)+"AAAAAA")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->new ProviderKeyCipher("").encrypt("record-one",KEY)).isInstanceOf(ApiException.class);
    }
    @Test void schemaHasDocumentedColumnsAndNoPhysicalReferences() {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ai_model_provider' AND column_comment=''",Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.referential_constraints WHERE constraint_schema=DATABASE() AND table_name='ai_model_provider'",Integer.class)).isZero();
    }
}
