package com.merine.rebuild.task;

import static org.assertj.core.api.Assertions.*;

import com.merine.rebuild.auth.AuthenticatedAccount;
import com.merine.rebuild.intelligence.IntelligenceService;
import com.merine.rebuild.intelligence.IntelligenceTaskAccess;
import com.merine.rebuild.intelligence.dto.AssessmentRequests.RecordAssessment;
import com.merine.rebuild.intelligence.dto.IntelligenceRequests.*;
import com.merine.rebuild.system.security.PermissionCodes;
import com.merine.rebuild.task.dto.TaskRequests;
import com.merine.rebuild.task.dto.TaskIntelligenceRequests.*;
import java.time.Instant;
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

/** 多连接请求及真实 SQL 失败，仅清理本用例在隔离测试库建立的合成记录。 */
@SpringBootTest
@ActiveProfiles("test")
class TaskIntelligenceConcurrencyTest {
    @Autowired IntelligenceService flows;
    @Autowired IntelligenceTaskAccess assessments;
    @Autowired TaskIntelligenceService integration;
    @Autowired TaskService tasks;
    @Autowired JdbcTemplate sql;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;
    private final List<Long> users = new ArrayList<>();
    private Authentication hq, handler;
    private String firstCode, otherCode;
    private long topic;

    @BeforeEach
    void fixtures() throws Exception {
        try (var c = dataSource.getConnection()) {
            assertThat(c.getCatalog()).isEqualTo("merine_rebuild_test");
        }
        long root = sql.queryForObject("SELECT id FROM sys_unit WHERE unit_level=1 LIMIT 1", Long.class);
        var divisions = sql.queryForList("SELECT id,unit_code FROM sys_unit WHERE parent_id=? ORDER BY id LIMIT 2", root);
        firstCode = divisions.getFirst().get("unit_code").toString();
        otherCode = divisions.get(1).get("unit_code").toString();
        hq = principal(root);
        handler = principal(((Number) divisions.get(1).get("id")).longValue());
        var draft = flows.create(hq, key(), new IntelligenceDraftRequest(
                "合成并发来源", "仅提供给第一个支队的原文", List.of(firstCode), List.of(firstCode), "", 0));
        topic = Long.parseLong(draft.id());
        flows.send(hq, topic, null, key(), new IntelligenceSendRequest(List.of(firstCode), ""));
    }

    private Authentication principal(long unit) {
        String login = "regr.link.concurrent." + key().replace("-", "");
        sql.update("INSERT INTO sys_user(login_name,display_name,password_hash,unit_id) VALUES(?,?,?,?)",
                login, "合成并发账号", "synthetic-test-only", unit);
        long id = sql.queryForObject("SELECT id FROM sys_user WHERE login_name=?", Long.class, login);
        users.add(id);
        var permissions = PermissionCodes.all();
        return new UsernamePasswordAuthenticationToken(
                new AuthenticatedAccount(id, login, login, "合成单位", List.of(), List.of(), permissions, 0),
                null, permissions.stream().map(SimpleGrantedAuthority::new).toList());
    }

    private static String key() { return UUID.randomUUID().toString(); }

    private CreateIntelligenceTask input(String target) {
        return new CreateIntelligenceTask("并发核查", "核对事实", "提交明确结论",
                Instant.now().plusSeconds(86400), List.of(target), "提供给任务参与方的摘要",
                null, new RecordAssessment(null, "有必要核查", "VERIFY"), null);
    }

    private <T> List<T> parallel(Callable<T> call) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<T> ready = () -> { start.await(); return call.call(); };
            Future<T> first = pool.submit(ready), second = pool.submit(ready);
            start.countDown();
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
    }

    @Test
    void summaryOnlyReadCommitsNormallyOutsideTestTransaction() {
        var input = input(otherCode);
        var created = integration.create(hq, topic, key(), input);
        long task = Long.parseLong(created.id()), branch = Long.parseLong(created.branches().getFirst().id());
        var source = integration.source(handler, task);
        assertThat(source.backgroundSummary()).isEqualTo(input.backgroundSummary());
        assertThat(source.topicId()).isNull();
        assertThat(source.adoptedAssessment()).isNull();
        tasks.accept(handler, task, branch, key());
        var finished = tasks.submitResult(handler, task, branch, key(), new TaskRequests.SubmitResult("NOT_FOUND", "核查结束", "正式结论", null));
        assertThat(finished.status()).isEqualTo("AWAITING_CLOSE");
        tasks.close(hq, task, key(), new TaskRequests.Close("核查已结束"));
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM task_intel_return r JOIN task_intel_source s ON s.id=r.source_link_id WHERE s.task_id=?", Integer.class, task)).isZero();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM intel_supplement WHERE topic_id=?", Integer.class, topic)).isZero();
    }

    @Test
    void concurrentAssessmentAndCompoundCreateEachCommitOnlyOnce() throws Exception {
        String assessmentKey = key();
        var records = parallel(() -> assessments.record(hq, topic, assessmentKey,
                new RecordAssessment(null, "并发判断", "WATCH")));
        assertThat(records.getFirst().id()).isEqualTo(records.get(1).id());
        String createKey = key();
        var input = input(otherCode);
        var created = parallel(() -> integration.create(hq, topic, createKey, input));
        assertThat(created.getFirst().id()).isEqualTo(created.get(1).id());
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM intel_assessment WHERE topic_id=?", Integer.class, topic)).isEqualTo(2);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM task_intel_source WHERE topic_id=?", Integer.class, topic)).isEqualTo(1);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM task_command WHERE action_code='CREATE_INTEL' AND idempotency_key=?", Integer.class, createKey)).isEqualTo(1);
    }

    @Test
    void failureAfterFreshAssessmentLeavesNoAssessmentTaskOrCommand() {
        String key = key();
        assertThatThrownBy(() -> integration.create(hq, topic, key, input("no-such-unit")))
                .isInstanceOf(com.merine.rebuild.common.ApiException.class);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM intel_assessment WHERE topic_id=?", Integer.class, topic)).isZero();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM task_intel_source WHERE topic_id=?", Integer.class, topic)).isZero();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM task_order WHERE issuer_user_id=?", Integer.class, users.getFirst())).isZero();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM task_command WHERE idempotency_key=?", Integer.class, key)).isZero();
    }

    @Test
    void realSqlFailureRollsBackCrossModuleCreate() {
        var tx = new TransactionTemplate(transactions);
        String createKey = key();
        long numberBefore = sql.queryForObject("SELECT COALESCE(SUM(last_seq),0) FROM task_no_counter", Long.class);
        assertThatThrownBy(() -> tx.execute(status -> {
            var created = integration.create(hq, topic, createKey, input(otherCode));
            sql.update("INSERT INTO task_intel_source(task_id,topic_id,assessment_id,last_supplement_id,created_unit_id,created_user_id,created_unit_name,created_user_name,created_at) SELECT task_id,topic_id,assessment_id,last_supplement_id,created_unit_id,created_user_id,created_unit_name,created_user_name,created_at FROM task_intel_source WHERE task_id=?", Long.parseLong(created.id()));
            return created;
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM intel_assessment WHERE topic_id=?", Integer.class, topic)).isZero();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM task_order WHERE issuer_user_id=?", Integer.class, users.getFirst())).isZero();
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM task_command WHERE idempotency_key=?", Integer.class, createKey)).isZero();
        assertThat(sql.queryForObject("SELECT COALESCE(SUM(last_seq),0) FROM task_no_counter", Long.class)).isEqualTo(numberBefore);


    }

    @AfterEach
    void cleanup() {
        if (users.isEmpty()) return;
        long source = users.getFirst();
        sql.update("DELETE r FROM task_intel_return r JOIN task_intel_source s ON s.id=r.source_link_id WHERE s.topic_id=?", topic);
        sql.update("DELETE FROM task_intel_source WHERE topic_id=?", topic);
        sql.update("DELETE c FROM task_command c JOIN task_order o ON o.id=c.task_id WHERE o.issuer_user_id=?", source);
        sql.update("DELETE a FROM task_action a JOIN task_order o ON o.id=a.task_id WHERE o.issuer_user_id=?", source);
        for (String table : List.of("task_result", "task_transfer_request", "task_assignment")) {
            sql.update("DELETE x FROM " + table + " x JOIN task_branch b ON b.id=x.branch_id JOIN task_order o ON o.id=b.task_id WHERE o.issuer_user_id=?", source);
        }
        sql.update("DELETE b FROM task_branch b JOIN task_order o ON o.id=b.task_id WHERE o.issuer_user_id=?", source);
        sql.update("DELETE FROM task_order WHERE issuer_user_id=?", source);
        sql.update("DELETE FROM intel_assessment WHERE topic_id=?", topic);
        sql.update("DELETE r FROM intel_receipt r JOIN intel_send s ON s.id=r.send_id WHERE s.topic_id=?", topic);
        for (String table : List.of("intel_send", "intel_scope_unit", "intel_draft_target", "intel_supplement", "intel_command")) {
            sql.update("DELETE FROM " + table + " WHERE topic_id=?", topic);
        }
        sql.update("DELETE FROM intel_topic WHERE id=?", topic);
        for (long user : users) sql.update("DELETE FROM sys_user WHERE id=?", user);
        users.clear();
    }
}
