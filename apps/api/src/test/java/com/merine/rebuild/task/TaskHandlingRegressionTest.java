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
        String login = "regr.task." + unit.id();
        sql.update("INSERT INTO sys_user (login_name, display_name, password_hash, unit_id) VALUES (?, ?, ?, ?)",
                login, login, "synthetic-test-only", unit.id());
        long userId = sql.queryForObject("SELECT id FROM sys_user WHERE login_name = ?", Long.class, login);
        List<String> permissions = new ArrayList<>(PermissionCodes.all());
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
                new TaskRequests.ReturnTask("明显错派"));
        TaskViews.Branch child = returned.branches().stream()
                .filter(branch -> number(branch.id()) == childId).findFirst().orElseThrow();
        assertThat(child.canTransferPeer()).isFalse();
        assertThatThrownBy(() -> service.requestTransfer(a, taskId, childId, key(),
                new TaskRequests.TransferRequest(divisionB.code(), "辖区变化", "已初查", "轨迹依据", "后续排查")))
                .isInstanceOf(ApiException.class).hasMessageContaining("没有操作该任务的权限");
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
                new TaskRequests.ReturnTask("明显错派，应由另一支队核查"));
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
        assertThat(service.detail(hq, taskId).status()).isEqualTo("COMPLETED");
    }
}
