package com.merine.rebuild.intelligence;

import static org.assertj.core.api.Assertions.*;
import com.merine.rebuild.auth.AuthenticatedAccount;
import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.intelligence.dto.IntelligenceRequests.*;
import com.merine.rebuild.intelligence.dto.IntelligenceViews.*;
import com.merine.rebuild.system.security.PermissionCodes;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 多连接竞争及真实 SQL 中途失败；只操作隔离测试库的本用例数据。 */
@SpringBootTest
@ActiveProfiles("test")
class IntelligenceConcurrencyTest {
    @Autowired IntelligenceService service;
    @Autowired JdbcTemplate sql;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;
    private final List<Long> users=new ArrayList<>();
    private Authentication hq,a,a2;
    private String targetCode;
    @BeforeEach void setup() throws Exception {
        try(var c=dataSource.getConnection()) {assertThat(c.getCatalog()).isEqualTo("merine_rebuild_test");}
        long root=sql.queryForObject("SELECT id FROM sys_unit WHERE unit_level=1 LIMIT 1",Long.class);
        long target=sql.queryForObject("SELECT id FROM sys_unit WHERE parent_id=? ORDER BY id LIMIT 1",Long.class,root);
        targetCode=sql.queryForObject("SELECT unit_code FROM sys_unit WHERE id=?",String.class,target);
        hq=principal(root);a=principal(target);a2=principal(target);
    }
    private Authentication principal(long unitId) {
        String login="regr.intel.concurrent."+UUID.randomUUID().toString().replace("-","");
        sql.update("INSERT INTO sys_user(login_name,display_name,password_hash,unit_id) VALUES(?,?,?,?)",login,login,"synthetic-test-only",unitId);
        long id=sql.queryForObject("SELECT id FROM sys_user WHERE login_name=?",Long.class,login);users.add(id);
        var codes=PermissionCodes.all();
        return new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(id,login,login,"合成单位",List.of(),List.of(),codes,0),null,codes.stream().map(SimpleGrantedAuthority::new).toList());
    }
    @AfterEach void cleanup() {
        if(users.isEmpty()) return;
        long source=users.getFirst();
        sql.update("DELETE f FROM intel_feedback f JOIN intel_receipt r ON r.id=f.receipt_id JOIN intel_send s ON s.id=r.send_id JOIN intel_topic t ON t.id=s.topic_id WHERE t.source_user_id=?",source);
        sql.update("DELETE d FROM intel_read d JOIN intel_receipt r ON r.id=d.receipt_id JOIN intel_send s ON s.id=r.send_id JOIN intel_topic t ON t.id=s.topic_id WHERE t.source_user_id=?",source);
        sql.update("DELETE r FROM intel_receipt r JOIN intel_send s ON s.id=r.send_id JOIN intel_topic t ON t.id=s.topic_id WHERE t.source_user_id=?",source);
        for(String table:List.of("intel_send","intel_scope_unit","intel_draft_target","intel_supplement","intel_command"))
            sql.update("DELETE c FROM "+table+" c JOIN intel_topic t ON t.id=c.topic_id WHERE t.source_user_id=?",source);
        sql.update("DELETE FROM intel_topic WHERE source_user_id=?",source);
        for(long user:users) sql.update("DELETE FROM sys_user WHERE id=?",user);
        users.clear();
    }
    private IntelligenceDraftRequest input() {return new IntelligenceDraftRequest("合成并发情报","正文",List.of(targetCode),List.of(targetCode),"说明",0);}
    private List<IntelligenceDetail> parallel(Callable<IntelligenceDetail> x,Callable<IntelligenceDetail> y) throws Exception {
        CountDownLatch start=new CountDownLatch(1);
        try(ExecutorService pool=Executors.newFixedThreadPool(2)) {
            Future<IntelligenceDetail> a=pool.submit(()->{start.await();return x.call();});
            Future<IntelligenceDetail> b=pool.submit(()->{start.await();return y.call();}); start.countDown();
            return List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        }
    }
    @Test void sameCreateIntentReturnsOneTopicAndDistinctKeysHaveUniqueNumbers() throws Exception {
        String key=UUID.randomUUID().toString();
        var same=parallel(()->service.create(hq,key,input()),()->service.create(hq,key,input()));
        assertThat(same.get(0).id()).isEqualTo(same.get(1).id());
        var distinct=parallel(()->service.create(hq,UUID.randomUUID().toString(),input()),()->service.create(hq,UUID.randomUUID().toString(),input()));
        assertThat(distinct.get(0).topicNo()).isNotEqualTo(distinct.get(1).topicNo());
    }
    @Test void concurrentUnitSignRecordsOnlyFirstActorAndSameKeyReplays() throws Exception {
        var draft=service.create(hq,UUID.randomUUID().toString(),input()); long topic=Long.parseLong(draft.id());
        var sent=service.send(hq,topic,null,UUID.randomUUID().toString(),new IntelligenceSendRequest(List.of(targetCode),"说明"));
        long receipt=Long.parseLong(sent.receipts().getFirst().id());String key=UUID.randomUUID().toString();
        var signed=parallel(()->service.receiptAction(a,topic,receipt,"sign",key,null),()->service.receiptAction(a2,topic,receipt,"sign",key,null));
        assertThat(signed.get(0).receipts().getFirst().signedByName()).isEqualTo(signed.get(1).receipts().getFirst().signedByName());
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM intel_command WHERE topic_id=? AND action_code LIKE 'sign:%'",Integer.class,topic)).isEqualTo(1);
        assertThatThrownBy(()->service.receiptAction(a,topic,receipt,"sign",UUID.randomUUID().toString(),null)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("RECEIPT_SIGNED"));
    }
    @Test void distinctSendKeysCannotCreateDuplicateReceiptOrLeaveFailedCommand() throws Exception {
        String other=sql.queryForObject("SELECT unit_code FROM sys_unit WHERE unit_level=2 AND unit_code<>? ORDER BY id LIMIT 1",String.class,targetCode);
        var input=new IntelligenceDraftRequest("合成并发发送","正文",List.of(targetCode,other),List.of(targetCode),"说明",0);
        long topic=Long.parseLong(service.create(hq,UUID.randomUUID().toString(),input).id());
        CountDownLatch start=new CountDownLatch(1);
        Callable<String> attempt=()->{
            start.await();
            try {service.send(hq,topic,null,UUID.randomUUID().toString(),new IntelligenceSendRequest(List.of(targetCode),"说明"));return "SENT";}
            catch(ApiException problem) {return problem.code();}
        };
        try(ExecutorService pool=Executors.newFixedThreadPool(2)) {
            Future<String> first=pool.submit(attempt),second=pool.submit(attempt);start.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder("SENT","UNIT_ALREADY_RECEIVED");
        }
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM intel_receipt r JOIN intel_send s ON s.id=r.send_id WHERE s.topic_id=?",Integer.class,topic)).isEqualTo(1);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM intel_command WHERE topic_id=? AND action_code LIKE 'send:%'",Integer.class,topic)).isEqualTo(1);
    }
    @Test void databaseFailureAfterInsertRollsBackTopicCommandAndNumber() {
        String key=UUID.randomUUID().toString();
        Integer before=sql.queryForObject("SELECT COALESCE(SUM(last_number),0) FROM intel_number_counter",Integer.class);
        TransactionTemplate tx=new TransactionTemplate(transactions);
        assertThatThrownBy(()->tx.execute(status->{
            var d=service.create(hq,key,input());
            sql.update("INSERT INTO intel_topic(topic_no,source_unit_id,source_user_id,source_unit_name,source_user_name,title,body,status,created_at) SELECT topic_no,source_unit_id,source_user_id,source_unit_name,source_user_name,title,body,status,created_at FROM intel_topic WHERE id=?",Long.parseLong(d.id()));
            return d;
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM intel_topic WHERE source_user_id=?",Integer.class,users.getFirst())).isZero();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM intel_command WHERE idempotency_key=?",Integer.class,key)).isZero();
        assertThat(sql.queryForObject("SELECT COALESCE(SUM(last_number),0) FROM intel_number_counter",Integer.class)).isEqualTo(before);
    }
}
