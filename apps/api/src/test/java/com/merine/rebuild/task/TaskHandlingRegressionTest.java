package com.merine.rebuild.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.merine.rebuild.auth.AuthenticatedAccount;
import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.system.security.PermissionCodes;
import com.merine.rebuild.task.dto.TaskRequests;
import com.merine.rebuild.task.dto.TaskViews;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 在隔离 MySQL 中执行真实状态转换；每个用例回滚合成账号和任务。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskHandlingRegressionTest {
    @Autowired TaskService service;
    @Autowired JdbcTemplate sql;
    @Autowired DataSource dataSource;
    @Autowired org.mybatis.spring.SqlSessionTemplate session;

    record Unit(long id, String code, String name) { }
    private Unit root;
    private Unit divisionA;
    private Unit divisionB;
    private Unit divisionC;
    private Unit brigadeA;
    private Unit brigadeB;
    private Authentication hq;
    private Authentication a;
    private Authentication b;
    private Authentication c;
    private Authentication aa;
    private Authentication bb;

    @BeforeEach
    void setup() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getCatalog()).isEqualTo("merine_rebuild_test");
        }
        root = sql.queryForObject("SELECT id, unit_code, unit_name FROM sys_unit WHERE unit_level = 1 LIMIT 1",
                (rs, n) -> new Unit(rs.getLong(1), rs.getString(2), rs.getString(3)));
        List<Unit> divisions = sql.query("SELECT id, unit_code, unit_name FROM sys_unit WHERE parent_id = ? ORDER BY id LIMIT 3",
                (rs, n) -> new Unit(rs.getLong(1), rs.getString(2), rs.getString(3)), root.id());
        assertThat(divisions).hasSize(3);
        divisionA = divisions.get(0); divisionB = divisions.get(1); divisionC = divisions.get(2);
        brigadeA = child(divisionA);
        brigadeB = child(divisionB);
        hq = principal(root); a = principal(divisionA); b = principal(divisionB);
        c = principal(divisionC); aa = principal(brigadeA); bb = principal(brigadeB);
    }

    private Unit child(Unit parent) {
        return sql.queryForObject("SELECT id, unit_code, unit_name FROM sys_unit WHERE parent_id = ? ORDER BY id LIMIT 1",
                (rs, n) -> new Unit(rs.getLong(1), rs.getString(2), rs.getString(3)), parent.id());
    }

    private Authentication principal(Unit unit) {
        return principal(unit, "", new ArrayList<>(PermissionCodes.all()));
    }

    private Authentication principal(Unit unit, String suffix, List<String> permissions) {
        String login = "regr.task." + unit.id() + suffix;
        sql.update("INSERT INTO sys_user (login_name, display_name, password_hash, unit_id) VALUES (?, ?, ?, ?)",
                login, login, "synthetic-test-only", unit.id());
        long userId = sql.queryForObject("SELECT id FROM sys_user WHERE login_name = ?", Long.class, login);
        AuthenticatedAccount account = new AuthenticatedAccount(userId, login, login, unit.name(),
                List.of(), List.of(), permissions, 0);
        return new UsernamePasswordAuthenticationToken(account, null,
                permissions.stream().map(SimpleGrantedAuthority::new).toList());
    }

    private TaskViews.TaskDetail create(Authentication owner, List<String> targets) {
        return service.create(owner, java.util.UUID.randomUUID().toString(),
                new TaskRequests.Create("合成核查任务", "核查并反馈", "明确核查结果",
                        Instant.now().plusSeconds(86400), targets, null));
    }

    private static String key() { return java.util.UUID.randomUUID().toString(); }
    private static long number(String text) { return Long.parseLong(text); }

    private static TaskViews.AllowedAction allowed(TaskViews.TaskDetail detail, long branchId, String code) {
        return detail.branches().stream().filter(b -> number(b.id()) == branchId).findFirst().orElseThrow()
                .allowedActions().stream().filter(action -> code.equals(action.code())).findFirst().orElseThrow();
    }

    private static void assertConsistent(TaskViews.TaskDetail detail, long branchId, String code, Runnable call) {
        TaskViews.AllowedAction action = allowed(detail, branchId, code);
        if (action.enabled()) call.run();
        else assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.code()).isEqualTo(action.reasonCode());
            assertThat(error.getMessage()).isEqualTo(action.reason());
        });
    }

    private TaskRequests.Dispatch dispatchTo(Unit unit) {
        return new TaskRequests.Dispatch("核查辖区", "反馈结果", Instant.now().plusSeconds(3600), List.of(unit.code()));
    }

    private TaskRequests.TransferRequest transferTo(Unit unit) {
        return new TaskRequests.TransferRequest(unit.code(), "辖区变化", "已初查", "轨迹依据", "后续排查");
    }

    private TaskRequests.SubmitResult result() {
        return new TaskRequests.SubmitResult("FULFILLED", "已核查", "完成", null);
    }

    @Test
    void pendingActionsFollowAuthoritiesAndSenderAvailability() {
        TaskViews.TaskDetail task = create(hq, List.of(divisionA.code()));
        long taskId = number(task.id()), branchId = number(task.branches().getFirst().id());
        TaskViews.TaskDetail pending = service.detail(a, taskId);
        assertThat(allowed(pending, branchId, "accept").enabled()).isTrue();
        assertThat(allowed(pending, branchId, "return").enabled()).isTrue();
        Authentication limited = principal(divisionA, ".limited", List.of(PermissionCodes.TASK_READ, PermissionCodes.TASK_ACCEPT));
        assertThat(service.detail(limited, taskId).branches().getFirst().allowedActions())
                .extracting(TaskViews.AllowedAction::code).containsExactly("accept");
        sql.update("UPDATE sys_unit SET status = 'DISABLED' WHERE id = ?", root.id());
        session.clearCache();
        TaskViews.TaskDetail disabled = service.detail(a, taskId);
        assertThat(allowed(disabled, branchId, "return").reasonCode()).isEqualTo("UNIT_CHANGED");
        assertConsistent(disabled, branchId, "return", () -> service.returnTask(a, taskId, branchId, key(),
                new TaskRequests.ReturnTask("WRONG_TARGET", "错派")));
        sql.update("UPDATE sys_unit SET status = 'ENABLED' WHERE id = ?", root.id());
        session.clearCache();
        assertConsistent(service.detail(a, taskId), branchId, "accept", () -> service.accept(a, taskId, branchId, key()));
    }

    @Test
    void activeActionsMatchChildAndTransferPreconditions() {
        TaskViews.TaskDetail task = create(hq, List.of(divisionA.code()));
        long taskId = number(task.id()), branchId = number(task.branches().getFirst().id());
        service.accept(a, taskId, branchId, key());
        TaskViews.TaskDetail active = service.detail(a, taskId);
        assertThat(active.branches().getFirst().allowedActions()).extracting(TaskViews.AllowedAction::code)
                .containsExactly("progress", "dispatch", "results", "transfer-requests");
        assertThat(active.branches().getFirst().allowedActions()).allMatch(TaskViews.AllowedAction::enabled);
        assertConsistent(active, branchId, "progress", () -> service.progress(a, taskId, branchId, key(), new TaskRequests.Progress("初查")));
        assertConsistent(active, branchId, "dispatch", () -> service.dispatch(a, taskId, branchId, key(), dispatchTo(brigadeA), false));
        TaskViews.TaskDetail childOpen = service.detail(a, taskId);
        assertThat(allowed(childOpen, branchId, "results").reasonCode()).isEqualTo("CHILD_BRANCH_OPEN");
        assertConsistent(childOpen, branchId, "results", () -> service.submitResult(a, taskId, branchId, key(), result()));
        assertConsistent(childOpen, branchId, "transfer-requests", () -> service.requestTransfer(a, taskId, branchId, key(), transferTo(divisionB)));
    }

    @Test
    void transferActionsWorkForLimitedTargetAndDisabledDecisionTarget() {
        TaskViews.TaskDetail task = create(hq, List.of(divisionA.code()));
        long taskId = number(task.id()), branchId = number(task.branches().getFirst().id());
        service.accept(a, taskId, branchId, key());
        assertConsistent(service.detail(a, taskId), branchId, "transfer-requests",
                () -> service.requestTransfer(a, taskId, branchId, key(), transferTo(divisionB)));
        TaskViews.TaskDetail pending = service.detail(a, taskId);
        long transferId = number(pending.branches().getFirst().transfers().getFirst().id());
        assertThat(allowed(pending, branchId, "dispatch").reasonCode()).isEqualTo("TRANSFER_PENDING");
        assertConsistent(pending, branchId, "dispatch", () -> service.dispatch(a, taskId, branchId, key(), dispatchTo(brigadeA), false));
        assertConsistent(pending, branchId, "results", () -> service.submitResult(a, taskId, branchId, key(), result()));
        assertThat(allowed(pending, branchId, "withdraw").transferId()).isEqualTo(Long.toString(transferId));
        TaskViews.TaskDetail limited = service.detail(b, taskId);
        assertThat(limited.branches().getFirst().assignments()).isEmpty();
        assertThat(allowed(limited, branchId, "respond").transferId()).isEqualTo(Long.toString(transferId));
        assertConsistent(limited, branchId, "respond", () -> service.respondTransfer(b, taskId, branchId, transferId, key(),
                new TaskRequests.TransferResponse(true, "可以接手", 60)));
        assertThat(allowed(service.detail(hq, taskId), branchId, "decide").enabled()).isTrue();
        sql.update("UPDATE sys_unit SET status = 'DISABLED' WHERE id = ?", divisionB.id());
        session.clearCache();
        TaskViews.TaskDetail disabled = service.detail(hq, taskId);
        assertThat(allowed(disabled, branchId, "decide").reasonCode()).isEqualTo("UNIT_CHANGED");
        assertConsistent(disabled, branchId, "decide", () -> service.decideTransfer(hq, taskId, branchId, transferId, key(),
                new TaskRequests.TransferDecision(false, "暂不批准", null)));
        sql.update("UPDATE sys_unit SET status = 'ENABLED' WHERE id = ?", divisionB.id());
        session.clearCache();
        assertConsistent(service.detail(a, taskId), branchId, "withdraw", () -> service.withdrawTransfer(a, taskId, branchId, transferId, key()));
    }

    @Test
    void elapsedAssignmentDueBlocksDispatchWithSameReason() {
        TaskViews.TaskDetail task = create(hq, List.of(divisionA.code()));
        long taskId = number(task.id()), branchId = number(task.branches().getFirst().id());
        service.accept(a, taskId, branchId, key());
        sql.update("UPDATE task_assignment SET due_at = ? WHERE branch_id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(60)), branchId);
        session.clearCache();
        TaskViews.TaskDetail overdue = service.detail(a, taskId);
        assertThat(allowed(overdue, branchId, "dispatch").reasonCode()).isEqualTo("DUE_PASSED");
        assertConsistent(overdue, branchId, "dispatch", () -> service.dispatch(a, taskId, branchId, key(), dispatchTo(brigadeA), false));
    }

    @Test
    void displayNamesHistoryScopeAndTransferCandidatesUseActualParticipants() {
        assertThatThrownBy(() -> service.targets(a, "transfer", null, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("INVALID_TASK_BRANCH"));
        TaskViews.TaskDetail task = create(hq, List.of(divisionA.code(), divisionC.code()));
        long taskId = number(task.id());
        long branchId = task.branches().stream().filter(x -> x.assignments().getFirst().toUnitName().equals(divisionA.name()))
                .mapToLong(x -> number(x.id())).findFirst().orElseThrow();
        assertThat(task.issuerUserName()).isEqualTo("regr.task." + root.id());
        assertThat(task.createdAt()).isNotNull();
        assertThat(task.actions().getFirst().code()).isEqualTo("CREATE");
        assertThat(task.actions()).allMatch(action -> action.actorUserName().equals("regr.task." + root.id()));
        service.accept(a, taskId, branchId, key());
        TaskViews.TaskDetail requested = service.requestTransfer(a, taskId, branchId, key(), transferTo(divisionB));
        TaskViews.Transfer transfer = requested.branches().getFirst().transfers().getFirst();
        assertThat(transfer.fromUnitName()).isEqualTo(divisionA.name());
        long transferId = number(transfer.id());
        service.respondTransfer(b, taskId, branchId, transferId, key(), new TaskRequests.TransferResponse(true, "同意", 60));
        assertConsistent(service.detail(hq, taskId), branchId, "decide", () -> service.decideTransfer(hq, taskId, branchId, transferId, key(),
                new TaskRequests.TransferDecision(true, "整单延期", Instant.now().plusSeconds(172800))));
        assertThat(service.targets(b, "transfer", taskId, branchId)).extracting(TaskViews.UnitOption::code)
                .doesNotContain(divisionA.code(), divisionB.code()).contains(divisionC.code());
        assertThatThrownBy(() -> service.targets(a, "transfer", taskId, branchId)).isInstanceOf(ApiException.class);
        TaskViews.TaskDetail other = service.detail(c, taskId);
        assertThat(other.actions()).filteredOn(action -> List.of("CREATE", "EXTEND_DUE").contains(action.code()))
                .hasSize(2).allMatch(action -> action.note() == null);
        assertThat(other.actions()).noneMatch(action -> action.code().startsWith("TRANSFER_"));
        assertConsistent(service.detail(b, taskId), branchId, "results", () -> service.submitResult(b, taskId, branchId, key(), result()));
        TaskViews.Result result = service.detail(b, taskId).branches().getFirst().result();
        assertThat(result.submittedByName()).isEqualTo("regr.task." + divisionB.id());
    }

    @Test
    void issuerClosesExplicitlyAndEveryParticipantSeesConclusion() {
        TaskViews.TaskDetail task = create(hq, List.of(divisionA.code()));
        long taskId = number(task.id()), branchId = number(task.branches().getFirst().id());
        TaskViews.AllowedAction close = task.allowedActions().getFirst();
        assertThat(close.enabled()).isFalse();
        assertThatThrownBy(() -> service.close(hq, taskId, key(), new TaskRequests.Close("不能提前办结")))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(close.reasonCode()).isEqualTo("BRANCHES_OPEN");
                    assertThat(e.getMessage()).isEqualTo(close.reason());
                });
        assertThatThrownBy(() -> service.close(a, taskId, key(), new TaskRequests.Close("越权")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("FORBIDDEN"));
        assertThat(service.detail(a, taskId).allowedActions()).isEmpty();
        Authentication readOnly = principal(root, ".read", List.of(PermissionCodes.TASK_READ));
        assertThat(service.detail(readOnly, taskId).allowedActions()).isEmpty();
        service.accept(a, taskId, branchId, key());
        service.submitResult(a, taskId, branchId, key(), result());
        assertThat(service.detail(hq, taskId).status()).isEqualTo("AWAITING_CLOSE");
        assertThat(service.detail(hq, taskId).completedAt()).isNull();
        assertThat(service.detail(hq, taskId).allowedActions().getFirst().enabled()).isTrue();
        assertThat(service.list(hq, "closing", 1, 100).items()).extracting(TaskViews.TaskListItem::id).contains(task.id());
        assertThat(service.list(a, "closing", 1, 100).items()).extracting(TaskViews.TaskListItem::id).doesNotContain(task.id());
        String closeKey = key();
        TaskRequests.Close input = new TaskRequests.Close("核查结束，总体目标达成");
        TaskViews.TaskDetail completed = service.close(hq, taskId, closeKey, input);
        assertThat(service.close(hq, taskId, closeKey, input)).isEqualTo(completed);
        assertThatThrownBy(() -> service.close(hq, taskId, key(), input))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("TASK_CLOSED"));
        TaskViews.TaskDetail participant = service.detail(a, taskId);
        assertThat(participant.conclusion()).isEqualTo(input.conclusion());
        assertThat(participant.closedByName()).isEqualTo("regr.task." + root.id());
        assertThat(participant.completedAt()).isNotNull();
        assertThat(participant.status()).isEqualTo("COMPLETED");
        assertThat(participant.actions()).filteredOn(action -> action.code().equals("CLOSE")).singleElement()
                .satisfies(action -> assertThat(action.note()).isNull());
        assertThat(completed.allowedActions()).isEmpty();
    }

    @Test
    void returnedBranchMustBeReassignedOrRecalledWithoutInventingAResult() {
        TaskViews.TaskDetail task = create(hq, List.of(divisionA.code(), divisionC.code()));
        long taskId = number(task.id());
        long branchId = task.branches().stream().filter(x -> x.assignments().getFirst().toUnitName().equals(divisionA.name()))
                .mapToLong(x -> number(x.id())).findFirst().orElseThrow();
        long otherId = task.branches().stream().filter(x -> number(x.id()) != branchId).mapToLong(x -> number(x.id())).findFirst().orElseThrow();
        service.returnTask(a, taskId, branchId, key(), new TaskRequests.ReturnTask("NOT_OUR_DUTY", "不属职责"));
        TaskViews.TaskDetail returned = service.detail(hq, taskId);
        assertThat(returned.branches().getFirst().allowedActions()).extracting(TaskViews.AllowedAction::code)
                .containsExactly("progress", "reassign", "recall");
        assertThatThrownBy(() -> service.submitResult(hq, taskId, branchId, key(), result()))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RETURN_PENDING"));
        assertThatThrownBy(() -> service.dispatch(hq, taskId, branchId, key(), dispatchTo(divisionB), false))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RETURN_PENDING"));
        assertThat(service.targets(hq, "reassign", taskId, branchId)).extracting(TaskViews.UnitOption::code)
                .doesNotContain(divisionA.code()).contains(divisionB.code());
        assertThatThrownBy(() -> service.dispatch(hq, taskId, branchId, key(), dispatchTo(divisionA), true))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("REASSIGN_TO_RETURNER"));
        assertConsistent(returned, branchId, "reassign", () -> service.dispatch(hq, taskId, branchId, key(), dispatchTo(divisionB), true));
        service.returnTask(b, taskId, branchId, key(), new TaskRequests.ReturnTask("UNCLEAR_REQUIREMENT", "要求待澄清"));
        assertThat(service.targets(hq, "reassign", taskId, branchId)).extracting(TaskViews.UnitOption::code)
                .doesNotContain(divisionA.code(), divisionB.code());
        String recallKey = key();
        TaskRequests.Recall reason = new TaskRequests.Recall("经确认撤回此项要求");
        assertConsistent(service.detail(hq, taskId), branchId, "recall", () -> service.recall(hq, taskId, branchId, recallKey, reason));
        TaskViews.TaskDetail recalled = service.recall(hq, taskId, branchId, recallKey, reason);
        TaskViews.Branch branch = recalled.branches().getFirst();
        assertThat(branch.status()).isEqualTo("RECALLED");
        assertThat(branch.completedAt()).isNull();
        assertThat(branch.result()).isNull();
        assertThat(branch.assignments().getLast().status()).isEqualTo("RECALLED");
        assertThat(branch.assignments().getLast().endReason()).isEqualTo(reason.reason());
        assertThat(branch.allowedActions()).isEmpty();
        assertThat(recalled.status()).isEqualTo("OPEN");
        service.accept(c, taskId, otherId, key());
        assertThat(service.submitResult(c, taskId, otherId, key(), result()).status()).isEqualTo("AWAITING_CLOSE");
    }

    @Test
    void recallLastBranchAndRejectRecallOfOrdinaryAssignment() {
        TaskViews.TaskDetail task = create(hq, List.of(divisionA.code()));
        long taskId = number(task.id()), branchId = number(task.branches().getFirst().id());
        service.accept(a, taskId, branchId, key());
        assertThatThrownBy(() -> service.recall(a, taskId, branchId, key(), new TaskRequests.Recall("不能撤回")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("NOT_RETURNED"));
        TaskViews.TaskDetail second = create(hq, List.of(divisionB.code()));
        long secondId = number(second.id()), secondBranchId = number(second.branches().getFirst().id());
        service.returnTask(b, secondId, secondBranchId, key(), new TaskRequests.ReturnTask("WRONG_TARGET", "错派"));
        sql.update("UPDATE task_assignment SET due_at = ? WHERE id = (SELECT current_assignment_id FROM task_branch WHERE id = ?)",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)), secondBranchId);
        session.clearCache();
        TaskViews.TaskDetail expired = service.detail(hq, secondId);
        assertThat(allowed(expired, secondBranchId, "reassign").reasonCode()).isEqualTo("DUE_PASSED");
        assertConsistent(expired, secondBranchId, "reassign", () -> service.dispatch(hq, secondId, secondBranchId, key(), dispatchTo(divisionC), true));
        assertThat(allowed(expired, secondBranchId, "recall").enabled()).isTrue();
        assertThat(service.recall(hq, secondId, secondBranchId, key(), new TaskRequests.Recall("撤回")).status()).isEqualTo("AWAITING_CLOSE");
    }

    @Test
    void outcomeAndSuggestedUnitValidationAndNotFoundResult() {
        TaskViews.TaskDetail task = create(hq, List.of(divisionA.code()));
        long taskId = number(task.id()), branchId = number(task.branches().getFirst().id());
        service.accept(a, taskId, branchId, key());
        assertThatThrownBy(() -> service.submitResult(a, taskId, branchId, key(),
                new TaskRequests.SubmitResult("OUT_OF_JURISDICTION", "核查", "转出", null)))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("SUGGESTED_UNIT_REQUIRED"));
        assertThatThrownBy(() -> service.submitResult(a, taskId, branchId, key(),
                new TaskRequests.SubmitResult("OUT_OF_JURISDICTION", "核查", "转出", divisionA.code())))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("INVALID_TARGET_UNIT"));
        Authentication resultsOnly = principal(divisionA, ".results", List.of(PermissionCodes.TASK_READ, PermissionCodes.TASK_SUBMIT_RESULT));
        assertThat(service.targets(resultsOnly, "suggest", taskId, branchId)).extracting(TaskViews.UnitOption::code)
                .doesNotContain(divisionA.code()).contains(divisionB.code(), root.code());
        assertThatThrownBy(() -> service.targets(resultsOnly, "suggest", null, null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("INVALID_TASK_BRANCH"));
        assertThatThrownBy(() -> service.targets(hq, "suggest", taskId, branchId)).isInstanceOf(ApiException.class);
        // 显示用状态项停用不阻止流程迁移。
        sql.update("UPDATE sys_dict_item i JOIN sys_dict_type t ON t.id=i.dict_type_id SET i.status='DISABLED' WHERE t.dict_code='task.order.status' AND i.item_value='AWAITING_CLOSE'");
        session.clearCache();
        TaskViews.TaskDetail completed = service.submitResult(resultsOnly, taskId, branchId, key(),
                new TaskRequests.SubmitResult("NOT_FOUND", "已逐项核查", "经核查未发现", null));
        assertThat(completed.status()).isEqualTo("AWAITING_CLOSE");
        assertThat(completed.branches().getFirst().result().outcomeCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    void returnReasonsAreValidatedAndRecordedInAssignmentAndHistory() {
        TaskViews.TaskDetail task = create(hq, List.of(divisionA.code()));
        long taskId = number(task.id()), branchId = number(task.branches().getFirst().id());
        assertThatThrownBy(() -> service.returnTask(a, taskId, branchId, key(), new TaskRequests.ReturnTask("OTHER", "说明")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("INVALID_RETURN_REASON"));
        sql.update("UPDATE sys_dict_item i JOIN sys_dict_type t ON t.id=i.dict_type_id SET i.status='DISABLED' WHERE t.dict_code='task.return.reason' AND i.item_value='WRONG_TARGET'");
        session.clearCache();
        assertThatThrownBy(() -> service.returnTask(a, taskId, branchId, key(), new TaskRequests.ReturnTask("WRONG_TARGET", "错派")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("RETURN_REASON_DISABLED"));
        String returnKey = key();
        TaskViews.TaskDetail returned = service.returnTask(a, taskId, branchId, returnKey,
                new TaskRequests.ReturnTask("UNCLEAR_REQUIREMENT", "请明确要求"));
        assertThat(returned.branches().getFirst().assignments().getFirst().endReasonCode()).isEqualTo("UNCLEAR_REQUIREMENT");
        assertThat(returned.actions()).filteredOn(action -> action.code().equals("RETURN")).singleElement()
                .satisfies(action -> assertThat(action.reasonCode()).isEqualTo("UNCLEAR_REQUIREMENT"));
        assertThatThrownBy(() -> service.returnTask(a, taskId, branchId, returnKey,
                new TaskRequests.ReturnTask("NOT_OUR_DUTY", "请明确要求")))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void newTaskNumbersUseShanghaiDayAndIncreaseOncePerNewIntent() {
        TaskRequests.Create input = new TaskRequests.Create("新编号", "核查", "答复", Instant.now().plusSeconds(86400), List.of(divisionA.code()), null);
        String intent = key();
        TaskViews.TaskDetail first = service.create(hq, intent, input);
        assertThat(service.create(hq, intent, input).taskNo()).isEqualTo(first.taskNo());
        TaskViews.TaskDetail second = service.create(hq, key(), input);
        String today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        assertThat(first.taskNo()).matches("RW-" + today + "-[0-9]{4,}");
        assertThat(Long.parseLong(second.taskNo().substring(12))).isEqualTo(Long.parseLong(first.taskNo().substring(12)) + 1);
    }

    @Test
    void multiTargetHierarchyAndAllResults() {
        TaskViews.TaskDetail task = create(hq, List.of(divisionA.code(), divisionB.code()));
        assertThat(task.branches()).hasSize(2);
        assertThat(service.list(hq, "all", 1, 20).items()).anySatisfy(item -> {
            assertThat(item.id()).isEqualTo(task.id());
            assertThat(item.currentResponsibleUnits()).contains(divisionA.name(), divisionB.name());
            assertThat(item.openBranchCount()).isEqualTo(2);
        });
        long taskId = number(task.id());
        assertThat(service.list(c, "all", 1, 20).items()).noneMatch(item -> task.id().equals(item.id()));
        assertThatThrownBy(() -> service.detail(c, taskId)).isInstanceOf(ApiException.class)
                .hasMessageContaining("任务不存在");
        assertThatThrownBy(() -> create(hq, List.of(brigadeA.code())))
                .isInstanceOf(ApiException.class).hasMessageContaining("直属下级");
        String aBranch = task.branches().get(0).assignments().get(0).toUnitName().equals(divisionA.name())
                ? task.branches().get(0).id() : task.branches().get(1).id();
        String bBranch = task.branches().get(0).id().equals(aBranch)
                ? task.branches().get(1).id() : task.branches().get(0).id();
        service.accept(a, taskId, number(aBranch), key());
        TaskViews.TaskDetail withChild = service.dispatch(a, taskId, number(aBranch), key(),
                new TaskRequests.Dispatch("核查辖区", "说明情况", Instant.now().plusSeconds(3600),
                        List.of(brigadeA.code())), false);
        String child = withChild.branches().stream().filter(x -> aBranch.equals(x.parentBranchId()))
                .findFirst().orElseThrow().id();
        assertThatThrownBy(() -> service.submitResult(a, taskId, number(aBranch), key(),
                new TaskRequests.SubmitResult("FULFILLED", "已核查", "完成", null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("下级分支");
        service.accept(aa, taskId, number(child), key());
        TaskViews.TaskDetail childCompleted = service.submitResult(aa, taskId, number(child), key(),
                new TaskRequests.SubmitResult("OUT_OF_JURISDICTION", "发现跨辖区", "建议后续核查", brigadeB.code()));
        String sourceResultId = childCompleted.branches().stream()
                .filter(x -> child.equals(x.id())).findFirst().orElseThrow().result().id();
        TaskViews.TaskDetail followup = service.create(a, key(),
                new TaskRequests.Create("独立后续任务", "继续核查", "新结果",
                        Instant.now().plusSeconds(7200), List.of(brigadeA.code()), sourceResultId));
        assertThat(followup.sourceResultId()).isEqualTo(sourceResultId);
        assertThat(followup.initialDueAt()).isNotEqualTo(task.initialDueAt());
        service.submitResult(a, taskId, number(aBranch), key(),
                new TaskRequests.SubmitResult("PARTIAL", "已督办", "部分完成", null));
        assertThat(service.detail(hq, taskId).status()).isEqualTo("OPEN");
        service.accept(b, taskId, number(bBranch), key());
        service.submitResult(b, taskId, number(bBranch), key(),
                new TaskRequests.SubmitResult("FULFILLED", "已核查", "完成", null));
        assertThat(service.detail(hq, taskId).status()).isEqualTo("AWAITING_CLOSE");
        service.close(hq, taskId, key(), new TaskRequests.Close("汇总完成"));
        assertThat(service.detail(hq, taskId).status()).isEqualTo("COMPLETED");
    }

    @Test
    void divisionTransferNeedsTargetAndHeadquartersDecision() {
        TaskViews.TaskDetail task = create(hq, List.of(divisionA.code()));
        long taskId = number(task.id()); long branchId = number(task.branches().get(0).id());
        service.accept(a, taskId, branchId, key());
        TaskViews.TaskDetail requested = service.requestTransfer(a, taskId, branchId, key(),
                new TaskRequests.TransferRequest(divisionB.code(), "辖区变化", "已初查", "轨迹依据", "后续排查"));
        String transferId = requested.branches().get(0).transfers().get(0).id();
        assertThat(requested.branches().get(0).currentMine()).isTrue();
        assertThatThrownBy(() -> service.submitResult(a, taskId, branchId, key(),
                new TaskRequests.SubmitResult("PARTIAL", "初查", "待续", null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("交接申请");
        service.respondTransfer(b, taskId, branchId, number(transferId), key(),
                new TaskRequests.TransferResponse(true, "可以接手", 120));
        assertThatThrownBy(() -> service.decideTransfer(hq, taskId, branchId, number(transferId), key(),
                new TaskRequests.TransferDecision(true, "时限过短", Instant.now().plusSeconds(60))))
                .isInstanceOf(ApiException.class).hasMessageContaining("办理时长");
        TaskViews.TaskDetail approved = service.decideTransfer(hq, taskId, branchId, number(transferId), key(),
                new TaskRequests.TransferDecision(true, "同意延期", Instant.now().plusSeconds(172800)));
        TaskViews.Branch branch = approved.branches().get(0);
        assertThat(branch.assignments()).hasSize(2);
        assertThat(branch.assignments().get(0).status()).isEqualTo("TRANSFERRED");
        assertThat(branch.assignments().get(1).toUnitName()).isEqualTo(divisionB.name());
        assertThat(branch.assignments().get(1).status()).isEqualTo("IN_PROGRESS");
        assertThat(approved.initialDueAt()).isBefore(approved.currentDueAt());
        assertThatThrownBy(() -> service.submitResult(a, taskId, branchId, key(),
                new TaskRequests.SubmitResult("FULFILLED", "已查", "完成", null)))
                .isInstanceOf(ApiException.class);
        service.submitResult(b, taskId, branchId, key(),
                new TaskRequests.SubmitResult("FULFILLED", "已查", "完成", null));
        assertThat(service.detail(hq, taskId).status()).isEqualTo("AWAITING_CLOSE");
        service.close(hq, taskId, key(), new TaskRequests.Close("汇总完成"));
        assertThat(service.detail(hq, taskId).status()).isEqualTo("COMPLETED");
    }

    @Test
    void returnedChildBranchCannotBeTransferredToPeerDivision() {
        TaskViews.TaskDetail task = create(hq, List.of(divisionA.code()));
        long taskId = number(task.id());
        long parentId = number(task.branches().get(0).id());
        service.accept(a, taskId, parentId, key());
        TaskViews.TaskDetail dispatched = service.dispatch(a, taskId, parentId, key(),
                new TaskRequests.Dispatch("核查下级辖区", "反馈结果", Instant.now().plusSeconds(3600),
                        List.of(brigadeA.code())), false);
        long childId = dispatched.branches().stream()
                .filter(branch -> task.branches().get(0).id().equals(branch.parentBranchId()))
                .mapToLong(branch -> number(branch.id())).findFirst().orElseThrow();

        TaskViews.TaskDetail returned = service.returnTask(aa, taskId, childId, key(),
                new TaskRequests.ReturnTask("WRONG_TARGET", "明显错派"));
        TaskViews.Branch child = returned.branches().stream()
                .filter(branch -> number(branch.id()) == childId).findFirst().orElseThrow();
        assertThat(child.canTransferPeer()).isFalse();
        assertThatThrownBy(() -> service.requestTransfer(a, taskId, childId, key(),
                new TaskRequests.TransferRequest(divisionB.code(), "辖区变化", "已初查", "轨迹依据", "后续排查")))
                .isInstanceOf(ApiException.class).hasMessageContaining("没有操作该任务的权限");
        service.recall(a, taskId, childId, key(), new TaskRequests.Recall("已确认无需继续下发"));
        assertThat(service.submitResult(a, taskId, parentId, key(), result()).status()).isEqualTo("AWAITING_CLOSE");
    }

    @Test
    void formerDivisionCannotSeeChildrenCreatedAfterPeerTransfer() {
        TaskViews.TaskDetail task = create(hq, List.of(divisionA.code()));
        long taskId = number(task.id());
        long parentId = number(task.branches().get(0).id());
        service.accept(a, taskId, parentId, key());
        TaskViews.TaskDetail requested = service.requestTransfer(a, taskId, parentId, key(),
                new TaskRequests.TransferRequest(divisionB.code(), "辖区变化", "已初查", "轨迹依据", "后续排查"));
        long transferId = number(requested.branches().get(0).transfers().get(0).id());
        service.respondTransfer(b, taskId, parentId, transferId, key(),
                new TaskRequests.TransferResponse(true, "可以接手", 120));
        service.decideTransfer(hq, taskId, parentId, transferId, key(),
                new TaskRequests.TransferDecision(true, "同意延期", Instant.now().plusSeconds(172800)));

        TaskViews.TaskDetail dispatched = service.dispatch(b, taskId, parentId, key(),
                new TaskRequests.Dispatch("核查新辖区", "反馈结果", Instant.now().plusSeconds(3600),
                        List.of(brigadeB.code())), false);
        String childId = dispatched.branches().stream()
                .filter(branch -> task.branches().get(0).id().equals(branch.parentBranchId()))
                .findFirst().orElseThrow().id();
        assertThat(service.detail(b, taskId).branches()).hasSize(2);
        assertThat(service.detail(a, taskId).branches()).extracting(TaskViews.Branch::id)
                .containsExactly(task.branches().get(0).id());

        service.accept(bb, taskId, number(childId), key());
        TaskViews.TaskDetail completedChild = service.submitResult(bb, taskId, number(childId), key(),
                new TaskRequests.SubmitResult("FULFILLED", "已核查新辖区", "已完成", null));
        String childResultId = completedChild.branches().stream()
                .filter(branch -> childId.equals(branch.id())).findFirst().orElseThrow().result().id();
        assertThatThrownBy(() -> service.create(a, key(), new TaskRequests.Create(
                "错误引用", "继续核查", "反馈结果", Instant.now().plusSeconds(7200),
                List.of(brigadeA.code()), childResultId)))
                .isInstanceOf(ApiException.class).hasMessageContaining("任务不存在");
        service.submitResult(b, taskId, parentId, key(),
                new TaskRequests.SubmitResult("FULFILLED", "已汇总", "全部完成", null));
        TaskViews.TaskDetail historical = service.detail(a, taskId);
        assertThat(historical.branches()).hasSize(1);
        assertThat(historical.branches().get(0).result().conclusion()).isEqualTo("全部完成");
    }

    @Test
    void issuerIdentityDoesNotDependOnUnitNameSnapshot() {
        TaskViews.TaskDetail task = create(hq, List.of(divisionA.code()));
        sql.update("UPDATE sys_unit SET unit_name = ? WHERE id = ?", "演示更名总队", root.id());

        TaskViews.TaskDetail renamed = service.detail(hq, number(task.id()));
        assertThat(renamed.issuerUnitName()).isEqualTo(root.name());
        assertThat(renamed.issuerMine()).isTrue();
        assertThat(service.detail(a, number(task.id())).issuerMine()).isFalse();
    }

    @Test
    void returnReassignKeepsOneBranchAndIdempotentCreate() {
        Instant due = Instant.now().plusSeconds(86400);
        TaskRequests.Create input = new TaskRequests.Create("合成错派核查", "核查辖区", "反馈结果",
                due, List.of(divisionA.code()), null);
        String createKey = key();
        TaskViews.TaskDetail created = service.create(hq, createKey, input);
        assertThat(service.create(hq, createKey, input).id()).isEqualTo(created.id());
        assertThat(service.create(hq, createKey, new TaskRequests.Create(
                " 合成错派核查 ", "核查辖区  ", " 反馈结果", due,
                List.of(" " + divisionA.code() + " "), null)).id()).isEqualTo(created.id());
        assertThatThrownBy(() -> service.create(hq, createKey,
                new TaskRequests.Create("另一项任务", "核查辖区", "反馈结果", due,
                        List.of(divisionA.code()), null)))
                .isInstanceOf(ApiException.class).hasMessageContaining("同一请求键");
        long taskId = number(created.id());
        long branchId = number(created.branches().get(0).id());
        TaskViews.TaskDetail returned = service.returnTask(a, taskId, branchId, key(),
                new TaskRequests.ReturnTask("WRONG_TARGET", "明显错派，应由另一支队核查"));
        assertThat(returned.branches()).hasSize(1);
        assertThat(returned.branches().get(0).assignments()).hasSize(2);
        assertThat(returned.branches().get(0).assignments().get(0).status()).isEqualTo("RETURNED");
        TaskViews.TaskDetail reassigned = service.dispatch(hq, taskId, branchId, key(),
                new TaskRequests.Dispatch("核查辖区", "反馈结果", due.minusSeconds(60),
                        List.of(divisionB.code())), true);
        assertThat(reassigned.branches()).hasSize(1);
        assertThat(reassigned.branches().get(0).assignments()).hasSize(3);
        assertThat(reassigned.branches().get(0).assignments().get(2).toUnitName())
                .isEqualTo(divisionB.name());
        service.accept(b, taskId, branchId, key());
        service.submitResult(b, taskId, branchId, key(),
                new TaskRequests.SubmitResult("FULFILLED", "核查完成", "已反馈", null));
        assertThat(service.detail(hq, taskId).status()).isEqualTo("AWAITING_CLOSE");
        service.close(hq, taskId, key(), new TaskRequests.Close("汇总完成"));
        assertThat(service.detail(hq, taskId).status()).isEqualTo("COMPLETED");
    }
}
