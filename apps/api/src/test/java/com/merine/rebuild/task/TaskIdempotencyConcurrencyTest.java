package com.merine.rebuild.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.merine.rebuild.auth.AuthenticatedAccount;
import com.merine.rebuild.system.security.PermissionCodes;
import com.merine.rebuild.task.dto.TaskRequests;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

/** 两个真实数据库连接同时创建同一请求，只能提交一项任务。 */
@SpringBootTest
@ActiveProfiles("test")
class TaskIdempotencyConcurrencyTest {
    @Autowired TaskService service;
    @Autowired JdbcTemplate sql;
    @Autowired DataSource dataSource;

    private long userId;
    private String login;
    private Authentication auth;
    private String targetCode;

    @BeforeEach
    void setup() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getCatalog()).isEqualTo("merine_rebuild_test");
        }
        long rootId = sql.queryForObject("SELECT id FROM sys_unit WHERE unit_level = 1 LIMIT 1", Long.class);
        targetCode = sql.queryForObject(
                "SELECT unit_code FROM sys_unit WHERE parent_id = ? ORDER BY id LIMIT 1", String.class, rootId);
        String rootName = sql.queryForObject("SELECT unit_name FROM sys_unit WHERE id = ?", String.class, rootId);
        login = "regr.task.concurrent." + UUID.randomUUID().toString().replace("-", "");
        sql.update("INSERT INTO sys_user (login_name, display_name, password_hash, unit_id) VALUES (?, ?, ?, ?)",
                login, "合成并发账号", "synthetic-test-only", rootId);
        userId = sql.queryForObject("SELECT id FROM sys_user WHERE login_name = ?", Long.class, login);
        List<String> codes = PermissionCodes.all();
        AuthenticatedAccount principal = new AuthenticatedAccount(userId, login, login, rootName,
                List.of(), List.of(), codes, 0);
        auth = new UsernamePasswordAuthenticationToken(principal, null,
                codes.stream().map(SimpleGrantedAuthority::new).toList());
    }

    @AfterEach
    void cleanup() {
        if (userId == 0) return;
        sql.update("DELETE FROM task_command WHERE task_id IN (SELECT id FROM task_order WHERE issuer_user_id = ?)", userId);
        sql.update("DELETE FROM task_action WHERE task_id IN (SELECT id FROM task_order WHERE issuer_user_id = ?)", userId);
        sql.update("DELETE a FROM task_assignment a JOIN task_branch b ON b.id = a.branch_id JOIN task_order o ON o.id = b.task_id WHERE o.issuer_user_id = ?", userId);
        sql.update("DELETE b FROM task_branch b JOIN task_order o ON o.id = b.task_id WHERE o.issuer_user_id = ?", userId);
        sql.update("DELETE FROM task_order WHERE issuer_user_id = ?", userId);
        sql.update("DELETE FROM sys_user WHERE id = ?", userId);
    }

    @Test
    void sameKeyConcurrentCreateReturnsOneCommittedTask() throws Exception {
        String key = UUID.randomUUID().toString();
        String title = "并发合成任务 " + UUID.randomUUID();
        TaskRequests.Create input = new TaskRequests.Create(title, "核查", "反馈",
                Instant.now().plusSeconds(86400), List.of(targetCode), null);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> call = () -> {
            start.await();
            return service.create(auth, key, input).id();
        };
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<String> first = pool.submit(call);
            Future<String> second = pool.submit(call);
            start.countDown();
            assertThat(first.get()).isEqualTo(second.get());
        }
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM task_order WHERE issuer_user_id = ?", Integer.class, userId))
                .isEqualTo(1);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM task_command WHERE actor_unit_id = (SELECT unit_id FROM sys_user WHERE id = ?) AND idempotency_key = ? AND task_id IS NOT NULL", Integer.class, userId, key))
                .isEqualTo(1);
    }
}
