package com.merine.rebuild.intelligence;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import com.merine.rebuild.support.MockMvcRegressionSupport;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;

/** 真正经过身份、CSRF、Controller、JSON 序列化及 MySQL，核对新契约的必填/可空形状。 */
@Transactional
class IntelligenceApiRegressionTest extends MockMvcRegressionSupport {
    private MockHttpSession account(long unitId) throws Exception {
        String login="regr.intel.http."+UUID.randomUUID().toString().replace("-","");String password=UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO sys_user(login_name,display_name,password_hash,unit_id) VALUES(?,?,?,?)",login,"合成 API 账号",passwordEncoder.encode(password),unitId);
        jdbcTemplate.update("INSERT INTO sys_user_role(user_id,role_id) SELECT u.id,r.id FROM sys_user u JOIN sys_role r ON r.role_code='SYSTEM_ADMIN' WHERE u.login_name=?",login);
        return signIn(login,password);
    }
    @Test void controllerContractAndAuthorizationMatchActualJson() throws Exception {
        long root=jdbcTemplate.queryForObject("SELECT id FROM sys_unit WHERE unit_level=1 LIMIT 1",Long.class);
        var units=jdbcTemplate.queryForList("SELECT id,unit_code FROM sys_unit WHERE parent_id=? ORDER BY id LIMIT 2",root);
        String target=(String)units.get(1).get("unit_code");
        MockHttpSession source=account(((Number)units.get(0).get("id")).longValue());
        MockHttpSession recipient=account(((Number)units.get(1).get("id")).longValue());
        MockHttpSession outsider=account(root);
        String create=objectMapper.writeValueAsString(Map.of("title","合成 API 情报","body","正文","scopeUnitCodes",List.of(target),"targetUnitCodes",List.of(target),"version",0));
        var result=sendJson(post("/api/intelligence-topics").header("Idempotency-Key",UUID.randomUUID().toString()).content(create),source);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String id=jsonOf(bodyOf(result),"$.data.id");
        Map<String,Object> draft=jsonOf(bodyOf(result),"$.data");
        assertThat(draft).containsKeys("id","publishedAt","receipts","supplements","allowedActions");
        assertThat(draft.get("publishedAt")).isNull();assertThat(draft.get("id")).isInstanceOf(String.class);
        assertThat(getJson("/api/intelligence-topics/"+id,recipient).getResponse().getStatus()).isEqualTo(404);
        var sent=sendJson(post("/api/intelligence-topics/"+id+"/send").header("Idempotency-Key",UUID.randomUUID().toString()).content(objectMapper.writeValueAsString(Map.of("targetUnitCodes",List.of(target)))),source);
        assertThat(sent.getResponse().getStatus()).isEqualTo(200);
        String receipt=jsonOf(bodyOf(sent),"$.data.receipts[0].id");
        String receiptUrl="/api/intelligence-topics/"+id+"/receipts/"+receipt;
        Map<String,Object> received=jsonOf(bodyOf(getJson("/api/intelligence-topics/"+id,recipient)),"$.data.receipts[0]");
        assertThat(received).containsKeys("signedAt","parentReceiptId","feedbacks","allowedActions").doesNotContainKey("readers");
        assertThat(received.get("signedAt")).isNull();assertThat(received.get("parentReceiptId")).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM intel_read WHERE receipt_id=?",Integer.class,receipt)).isZero();
        var feedback=sendJson(post(receiptUrl+"/feedbacks").header("Idempotency-Key",UUID.randomUUID().toString()).content("{\"body\":\"线索\"}"),recipient);
        assertThat(feedback.getResponse().getStatus()).isEqualTo(409);assertThat((String)jsonOf(bodyOf(feedback),"$.code")).isEqualTo("SIGN_REQUIRED");
        assertThat(sendJson(post(receiptUrl+"/read").header("Idempotency-Key",UUID.randomUUID().toString()),recipient).getResponse().getStatus()).isEqualTo(404);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM intel_read WHERE receipt_id=?",Integer.class,receipt)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_permission WHERE permission_code='intelligence:topic:read-receipt'",Integer.class)).isZero();
        assertThat(sendJson(post(receiptUrl+"/sign").header("Idempotency-Key",UUID.randomUUID().toString()),recipient).getResponse().getStatus()).isEqualTo(200);
        assertThat(sendJson(post(receiptUrl+"/feedbacks").header("Idempotency-Key",UUID.randomUUID().toString()).content("{\"body\":\"线索\"}"),recipient).getResponse().getStatus()).isEqualTo(200);
        assertThat(getJson("/api/intelligence-topics/"+id,outsider).getResponse().getStatus()).isEqualTo(404);
        assertThat(intOf(bodyOf(getJson("/api/intelligence-topics?view=received",outsider)),"$.data.total")).isZero();
        var invalid=sendJson(post("/api/intelligence-topics").header("Idempotency-Key",UUID.randomUUID().toString()).content("{\"title\":\"缺字段\"}"),source);
        assertThat(invalid.getResponse().getStatus()).isEqualTo(400);
    }
}
