package com.merine.rebuild.task;

import com.merine.rebuild.auth.AuthenticatedAccount;
import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.common.PageResult;
import com.merine.rebuild.system.security.PermissionGuard;
import com.merine.rebuild.system.security.PermissionCodes;
import com.merine.rebuild.system.dictionary.DictionaryLookup;
import com.merine.rebuild.task.dto.TaskRequests;
import com.merine.rebuild.task.dto.TaskViews;
import com.merine.rebuild.task.persistence.TaskMapper;
import com.merine.rebuild.task.persistence.TaskMapper.ActionRow;
import com.merine.rebuild.task.persistence.TaskMapper.AssignmentRow;
import com.merine.rebuild.task.persistence.TaskMapper.BranchRow;
import com.merine.rebuild.task.persistence.TaskMapper.CommandRow;
import com.merine.rebuild.task.persistence.TaskMapper.OrderRow;
import com.merine.rebuild.task.persistence.TaskMapper.ResultRow;
import com.merine.rebuild.task.persistence.TaskMapper.TransferRow;
import com.merine.rebuild.task.persistence.TaskMapper.UnitRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 任务的授权、数据范围、责任状态和事务用例。所有业务写入都在这里完成。 */
@Service
public class TaskService {
    private static final Set<String> OUTCOMES = Set.of(
            "FULFILLED", "PARTIAL", "OUT_OF_JURISDICTION", "UNABLE_TO_VERIFY");
    private static final Set<String> TABS = Set.of(
            "all", "inbox", "doing", "issued", "transfers", "decisions", "completed");

    private final TaskMapper mapper;
    private final PermissionGuard guard;
    private final DictionaryLookup dictionaries;

    public TaskService(TaskMapper mapper, PermissionGuard guard, DictionaryLookup dictionaries) {
        this.mapper = mapper;
        this.guard = guard;
        this.dictionaries = dictionaries;
    }

    private record Actor(long userId, UnitRow unit) { }
    private record Context(OrderRow order, BranchRow branch, AssignmentRow assignment) { }

    private Context locked(Actor actor, long taskId, long branchId) {
        OrderRow order = mapper.lockOrder(taskId);
        BranchRow branch = mapper.lockBranch(branchId);
        if (order == null || branch == null || branch.taskId() != taskId || !canSeeBranch(actor, branchId)) throw notFound();
        AssignmentRow assignment = branch.currentAssignmentId() == null ? null : mapper.lockAssignment(branch.currentAssignmentId());
        if (!"OPEN".equals(order.status()) || !"OPEN".equals(branch.status()) || assignment == null) {
            throw conflict("TASK_CLOSED", "任务或分支已经办结");
        }
        return new Context(order, branch, assignment);
    }

    private static void requireCurrent(Actor actor, Context context, String status) {
        if (context.assignment().toUnitId() != actor.unit().id()) throw forbidden();
        if (!status.equals(context.assignment().status())) throw conflict("TASK_STATE_CHANGED", "当前承办状态已变化");
    }

    private void requireNoTransfer(long branchId) {
        if (mapper.transfersByBranch(branchId).stream().anyMatch(t ->
                "AWAITING_TARGET".equals(t.status()) || "AWAITING_ISSUER".equals(t.status()))) {
            throw conflict("TRANSFER_PENDING", "请先完成或撤回交接申请");
        }
    }

    private static void requireNoChildren(TaskMapper mapper, long branchId) {
        if (mapper.openChildren(branchId) != 0) throw conflict("CHILD_BRANCH_OPEN", "下级分支尚未全部办结");
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskViews.TaskDetail accept(Authentication auth, long taskId, long branchId, String key) {
        guard.require(auth, PermissionCodes.TASK_ACCEPT, "没有承接权限");
        Actor actor = actor(auth);
        String body = digest(taskId, branchId);
        TaskViews.TaskDetail prior = replay(actor, "ACCEPT", key, body);
        if (prior != null) return prior;
        lockUnits(actor.unit().id(), List.of());
        prior = reserve(actor, "ACCEPT", key, body);
        if (prior != null) return prior;
        Context c = locked(actor, taskId, branchId);
        requireCurrent(actor, c, "PENDING_ACCEPT");
        Instant now = Instant.now();
        changed(mapper.acceptAssignment(c.assignment().id(), c.assignment().version(), actor.userId(), now));
        action(taskId, branchId, c.assignment().id(), null, "ACCEPT", actor, null, null, null, null, now);
        recordCommand(actor, "ACCEPT", key, body, taskId, branchId, now);
        return detailFor(actor, taskId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskViews.TaskDetail progress(Authentication auth, long taskId, long branchId,
                                          String key, TaskRequests.Progress input) {
        guard.require(auth, PermissionCodes.TASK_PROGRESS, "没有记录进展权限");
        Actor actor = actor(auth);
        String note = input.note().strip();
        String body = digest(taskId, branchId, note);
        TaskViews.TaskDetail prior = replay(actor, "PROGRESS", key, body);
        if (prior != null) return prior;
        lockUnits(actor.unit().id(), List.of());
        prior = reserve(actor, "PROGRESS", key, body);
        if (prior != null) return prior;
        Context c = locked(actor, taskId, branchId);
        requireCurrent(actor, c, "IN_PROGRESS");
        Instant now = Instant.now();
        action(taskId, branchId, c.assignment().id(), null, "PROGRESS", actor, null, null, null, note, now);
        recordCommand(actor, "PROGRESS", key, body, taskId, branchId, now);
        return detailFor(actor, taskId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskViews.TaskDetail returnTask(Authentication auth, long taskId, long branchId,
                                            String key, TaskRequests.ReturnTask input) {
        guard.require(auth, PermissionCodes.TASK_RETURN, "没有退回权限");
        Actor actor = actor(auth);
        String reason = input.reason().strip();
        String body = digest(taskId, branchId, reason);
        TaskViews.TaskDetail prior = replay(actor, "RETURN", key, body);
        if (prior != null) return prior;
        BranchRow before = mapper.branch(branchId);
        AssignmentRow previous = before == null || before.currentAssignmentId() == null
                ? null : mapper.assignment(before.currentAssignmentId());
        if (previous == null) throw notFound();
        lockUnits(actor.unit().id(), List.of(previous.fromUnitId()));
        prior = reserve(actor, "RETURN", key, body);
        if (prior != null) return prior;
        Context c = locked(actor, taskId, branchId);
        requireCurrent(actor, c, "PENDING_ACCEPT");
        UnitRow sender = mapper.lockUnitById(c.assignment().fromUnitId());
        if (sender == null || !"ENABLED".equals(sender.status())) throw conflict("UNIT_CHANGED", "发送单位已变化");
        Instant now = Instant.now();
        changed(mapper.endAssignment(c.assignment().id(), c.assignment().version(), "PENDING_ACCEPT",
                "RETURNED", actor.userId(), now, reason));
        mapper.insertAssignment(branchId, c.assignment().id(), actor.unit().id(), actor.unit().name(),
                sender.id(), sender.name(), "RETURN", c.assignment().dueAt(), "IN_PROGRESS", null, now);
        long newId = mapper.lastId();
        changed(mapper.setCurrentAssignment(branchId, c.assignment().id(), newId));
        action(taskId, branchId, newId, null, "RETURN", actor, sender.id(), c.assignment().dueAt(),
                c.assignment().dueAt(), reason, now);
        recordCommand(actor, "RETURN", key, body, taskId, branchId, now);
        return detailFor(actor, taskId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskViews.TaskDetail dispatch(Authentication auth, long taskId, long branchId,
                                          String key, TaskRequests.Dispatch input, boolean reassign) {
        guard.require(auth, PermissionCodes.TASK_DISPATCH,
                "没有下发权限");
        Actor actor = actor(auth);
        List<String> codes = distinctCodes(input.targetUnitCodes());
        String instruction = input.instruction().strip();
        String expectedResult = input.expectedResult().strip();
        String code = reassign ? "REASSIGN" : "DISPATCH";
        String body = digest(taskId, branchId, instruction, expectedResult, input.dueAt(), codes);
        TaskViews.TaskDetail prior = replay(actor, code, key, body);
        if (prior != null) return prior;
        List<UnitRow> candidates = resolveTargets(codes);
        Map<Long, UnitRow> lockedUnits = lockUnits(actor.unit().id(), candidates.stream().map(UnitRow::id).toList());
        actor = new Actor(actor.userId(), lockedUnits.get(actor.unit().id()));
        List<UnitRow> targets = candidates.stream().map(target -> lockedUnits.get(target.id())).toList();
        prior = reserve(actor, code, key, body);
        if (prior != null) return prior;
        requireCanDispatch(actor.unit());
        for (UnitRow target : targets) requireDirect(actor.unit(), target);
        Context c = locked(actor, taskId, branchId);
        requireCurrent(actor, c, "IN_PROGRESS");
        requireNoTransfer(branchId);
        Instant now = Instant.now();
        requireFuture(input.dueAt(), now);
        if (input.dueAt().isAfter(c.assignment().dueAt())) throw bad("DUE_EXCEEDS_PARENT", "下级期限不能晚于当前承办期限");
        if (reassign) {
            if (targets.size() != 1 || !"RETURN".equals(c.assignment().sourceAction())) {
                throw bad("INVALID_REASSIGN", "只有退回接回的分支可重新派给一个直属下级");
            }
            changed(mapper.endAssignment(c.assignment().id(), c.assignment().version(), "IN_PROGRESS",
                    "REASSIGNED", actor.userId(), now, "重新派发"));
            UnitRow target = targets.get(0);
            mapper.insertAssignment(branchId, c.assignment().id(), actor.unit().id(), actor.unit().name(),
                    target.id(), target.name(), "REASSIGN", input.dueAt(), "PENDING_ACCEPT", null, null);
            long newId = mapper.lastId();
            changed(mapper.setCurrentAssignment(branchId, c.assignment().id(), newId));
            action(taskId, branchId, newId, null, "REASSIGN", actor, target.id(), c.assignment().dueAt(), input.dueAt(), null, now);
        } else {
            for (UnitRow target : targets) createBranch(taskId, branchId, actor, target,
                    instruction, expectedResult, input.dueAt(), now);
        }
        recordCommand(actor, code, key, body, taskId, branchId, now);
        return detailFor(actor, taskId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskViews.TaskDetail submitResult(Authentication auth, long taskId, long branchId,
                                              String key, TaskRequests.SubmitResult input) {
        guard.require(auth, PermissionCodes.TASK_SUBMIT_RESULT, "没有提交结果权限");
        Actor actor = actor(auth);
        String handlingDetail = input.handlingDetail().strip();
        String conclusion = input.conclusion().strip();
        String suggestedCode = blankToNull(input.suggestedUnitCode());
        String body = digest(taskId, branchId, input.outcomeCode(), handlingDetail,
                conclusion, suggestedCode);
        TaskViews.TaskDetail prior = replay(actor, "RESULT", key, body);
        if (prior != null) return prior;
        UnitRow suggested = suggestedCode == null ? null : mapper.unitByCode(suggestedCode);
        if (suggested != null) lockUnits(actor.unit().id(), List.of(suggested.id()));
        else lockUnits(actor.unit().id(), List.of());
        prior = reserve(actor, "RESULT", key, body);
        if (prior != null) return prior;
        if (!OUTCOMES.contains(input.outcomeCode())) throw bad("INVALID_OUTCOME", "结果类型不合法");
        var outcomeDictionary = dictionaries.find(List.of("task.result.outcome")).getFirst();
        if (!"ENABLED".equals(outcomeDictionary.status()) || outcomeDictionary.items().stream()
                .noneMatch(item -> input.outcomeCode().equals(item.value()) && "ENABLED".equals(item.status()))) {
            throw bad("OUTCOME_DISABLED", "该结果类型已停用，请刷新后重选");
        }
        if (suggestedCode != null
                && (suggested == null || !"ENABLED".equals(suggested.status()))) throw bad("INVALID_TARGET_UNIT", "建议单位不存在或不可用");
        Context c = locked(actor, taskId, branchId);
        requireCurrent(actor, c, "IN_PROGRESS");
        requireNoTransfer(branchId);
        requireNoChildren(mapper, branchId);
        Instant now = Instant.now();
        mapper.insertResult(branchId, c.assignment().id(), input.outcomeCode(),
                handlingDetail, conclusion,
                suggested == null ? null : suggested.id(), actor.userId(), now);
        changed(mapper.endAssignment(c.assignment().id(), c.assignment().version(), "IN_PROGRESS",
                "COMPLETED", actor.userId(), now, null));
        changed(mapper.completeBranch(branchId, c.branch().version(), now));
        action(taskId, branchId, c.assignment().id(), null, "RESULT", actor, null, null, null,
                input.outcomeCode(), now);
        if (mapper.openBranches(taskId) == 0) changed(mapper.completeOrder(taskId, now));
        recordCommand(actor, "RESULT", key, body, taskId, branchId, now);
        return detailFor(actor, taskId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskViews.TaskDetail requestTransfer(Authentication auth, long taskId, long branchId,
                                                 String key, TaskRequests.TransferRequest input) {
        guard.require(auth, PermissionCodes.TASK_TRANSFER_REQUEST, "没有申请交接权限");
        Actor actor = actor(auth);
        String targetCode = input.targetUnitCode().strip();
        String reason = input.reason().strip();
        String workDone = input.workDone().strip();
        String evidence = input.evidenceSummary().strip();
        String remaining = input.remainingWork().strip();
        String body = digest(taskId, branchId, targetCode, reason, workDone, evidence, remaining);
        TaskViews.TaskDetail prior = replay(actor, "TRANSFER_REQUEST", key, body);
        if (prior != null) return prior;
        UnitRow target = mapper.unitByCode(targetCode);
        if (target == null) throw bad("INVALID_TARGET_UNIT", "目标支队不存在");
        Map<Long, UnitRow> lockedUnits = lockUnits(actor.unit().id(), List.of(target.id()));
        actor = new Actor(actor.userId(), lockedUnits.get(actor.unit().id()));
        target = lockedUnits.get(target.id());
        prior = reserve(actor, "TRANSFER_REQUEST", key, body);
        if (prior != null) return prior;
        Context c = locked(actor, taskId, branchId);
        requireCurrent(actor, c, "IN_PROGRESS");
        if (actor.unit().level() != 2 || target.level() != 2
                || target.id() == actor.unit().id() || target.parentId() == null
                || !target.parentId().equals(actor.unit().parentId())
                || c.order().issuerUnitId() != actor.unit().parentId()
                || c.branch().parentBranchId() != null
                || c.branch().originFromUnitId() != c.order().issuerUnitId()) throw forbidden();
        if (mapper.previousDivision(branchId, target.id()) > 0) {
            throw bad("TRANSFER_CYCLE", "该支队已承担过此分支，不能转回");
        }
        requireNoTransfer(branchId);
        requireNoChildren(mapper, branchId);
        Instant now = Instant.now();
        mapper.insertTransfer(branchId, c.assignment().id(), target.id(), reason,
                workDone, evidence, remaining,
                actor.userId(), now);
        long transferId = mapper.lastId();
        action(taskId, branchId, c.assignment().id(), transferId, "TRANSFER_REQUEST", actor,
                target.id(), c.assignment().dueAt(), null, reason, now);
        recordCommand(actor, "TRANSFER_REQUEST", key, body, taskId, branchId, now);
        return detailFor(actor, taskId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskViews.TaskDetail respondTransfer(Authentication auth, long taskId, long branchId,
                                                 long transferId, String key, TaskRequests.TransferResponse input) {
        guard.require(auth, PermissionCodes.TASK_TRANSFER_RESPOND, "没有回应交接权限");
        Actor actor = actor(auth);
        String body = digest(taskId, branchId, transferId, input.accept(), input.reason(),
                input.accept() ? input.requiredDurationMinutes() : null);
        TaskViews.TaskDetail prior = replay(actor, "TRANSFER_RESPOND", key, body);
        if (prior != null) return prior;
        lockUnits(actor.unit().id(), List.of());
        prior = reserve(actor, "TRANSFER_RESPOND", key, body);
        if (prior != null) return prior;
        OrderRow order = mapper.lockOrder(taskId);
        BranchRow branch = mapper.lockBranch(branchId);
        TransferRow transfer = mapper.lockTransfer(transferId);
        if (order == null || branch == null || branch.taskId() != taskId || transfer == null
                || transfer.branchId() != branchId || transfer.targetUnitId() != actor.unit().id()) throw notFound();
        if (!"OPEN".equals(order.status()) || !"OPEN".equals(branch.status())
                || !"AWAITING_TARGET".equals(transfer.status())
                || branch.currentAssignmentId() != transfer.fromAssignmentId()) {
            throw conflict("TASK_STATE_CHANGED", "交接申请已变化");
        }
        if (input.accept() && (input.requiredDurationMinutes() == null || input.requiredDurationMinutes() <= 0)) {
            throw bad("INVALID_DURATION", "同意承接时必须填写所需办理分钟数");
        }
        if (!input.accept() && (input.reason() == null || input.reason().isBlank())) {
            throw bad("REASON_REQUIRED", "拒绝承接时必须填写理由");
        }
        Instant now = Instant.now();
        changed(mapper.respondTransfer(transferId, transfer.version(),
                input.accept() ? "AWAITING_ISSUER" : "TARGET_DECLINED", actor.userId(), now,
                input.reason(), input.accept() ? input.requiredDurationMinutes() : null));
        action(taskId, branchId, transfer.fromAssignmentId(), transferId,
                input.accept() ? "TRANSFER_ACCEPT" : "TRANSFER_DECLINE", actor, null,
                null, null, input.reason(), now);
        recordCommand(actor, "TRANSFER_RESPOND", key, body, taskId, branchId, now);
        return detailFor(actor, taskId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskViews.TaskDetail decideTransfer(Authentication auth, long taskId, long branchId,
                                                long transferId, String key, TaskRequests.TransferDecision input) {
        guard.require(auth, PermissionCodes.TASK_TRANSFER_DECIDE, "没有审批交接权限");
        Actor actor = actor(auth);
        String body = digest(taskId, branchId, transferId, input.approve(), input.reason(),
                input.approve() ? input.dueAt() : null);
        TaskViews.TaskDetail prior = replay(actor, "TRANSFER_DECIDE", key, body);
        if (prior != null) return prior;
        TransferRow before = mapper.transfer(transferId);
        if (before == null) throw notFound();
        lockUnits(actor.unit().id(), List.of(before.targetUnitId()));
        prior = reserve(actor, "TRANSFER_DECIDE", key, body);
        if (prior != null) return prior;
        Context c = locked(actor, taskId, branchId);
        if (c.order().issuerUnitId() != actor.unit().id() || actor.unit().level() != 1) throw forbidden();
        TransferRow transfer = mapper.lockTransfer(transferId);
        if (transfer == null || transfer.branchId() != branchId || !"AWAITING_ISSUER".equals(transfer.status())
                || transfer.fromAssignmentId() != c.assignment().id()
                || !"IN_PROGRESS".equals(c.assignment().status())) {
            throw conflict("TASK_STATE_CHANGED", "交接申请或责任主体已变化");
        }
        Instant now = Instant.now();
        if (!input.approve() && (input.reason() == null || input.reason().isBlank())) {
            throw bad("REASON_REQUIRED", "拒绝申请时必须填写理由");
        }
        if (input.approve()) {
            requireNoChildren(mapper, branchId);
            requireFuture(input.dueAt(), now);
            if (transfer.targetRequiredDurationMinutes() == null ||
                    input.dueAt().isBefore(now.plusSeconds(transfer.targetRequiredDurationMinutes() * 60L))) {
                throw bad("DUE_TOO_SHORT", "新期限不能短于目标支队确认的办理时长");
            }
            if (input.dueAt().isAfter(c.order().currentDueAt())
                    && (input.reason() == null || input.reason().isBlank())) {
                throw bad("EXTENSION_REASON_REQUIRED", "延长整单期限必须填写理由");
            }
            UnitRow target = mapper.lockUnitById(transfer.targetUnitId());
            if (target == null || !"ENABLED".equals(target.status()) || target.level() != 2
                    || target.parentId() == null || target.parentId() != actor.unit().id()) {
                throw conflict("UNIT_CHANGED", "目标支队已变化");
            }
            changed(mapper.endAssignment(c.assignment().id(), c.assignment().version(), "IN_PROGRESS",
                    "TRANSFERRED", actor.userId(), now, transfer.reason()));
            mapper.insertAssignment(branchId, c.assignment().id(), c.assignment().toUnitId(),
                    c.assignment().toUnitNameSnapshot(), target.id(), target.name(),
                    "PEER_TRANSFER", input.dueAt(), "IN_PROGRESS", transfer.targetRespondedByUserId(), now);
            long newId = mapper.lastId();
            changed(mapper.setCurrentAssignment(branchId, c.assignment().id(), newId));
            if (input.dueAt().isAfter(c.order().currentDueAt())) {
                changed(mapper.extendOrder(taskId, c.order().currentDueAt(), input.dueAt()));
                action(taskId, null, null, transferId, "EXTEND_DUE", actor, null,
                        c.order().currentDueAt(), input.dueAt(), input.reason().strip(), now);
            }
        }
        changed(mapper.decideTransfer(transferId, transfer.version(),
                input.approve() ? "APPROVED" : "ISSUER_REJECTED", actor.userId(), now,
                input.reason(), input.approve() ? input.dueAt() : null));
        action(taskId, branchId, transfer.fromAssignmentId(), transferId,
                input.approve() ? "TRANSFER_APPROVE" : "TRANSFER_REJECT", actor, transfer.targetUnitId(),
                c.assignment().dueAt(), input.approve() ? input.dueAt() : null, input.reason(), now);
        recordCommand(actor, "TRANSFER_DECIDE", key, body, taskId, branchId, now);
        return detailFor(actor, taskId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskViews.TaskDetail withdrawTransfer(Authentication auth, long taskId, long branchId,
                                                  long transferId, String key) {
        guard.require(auth, PermissionCodes.TASK_TRANSFER_REQUEST, "没有撤回交接权限");
        Actor actor = actor(auth);
        String body = digest(taskId, branchId, transferId);
        TaskViews.TaskDetail prior = replay(actor, "TRANSFER_WITHDRAW", key, body);
        if (prior != null) return prior;
        lockUnits(actor.unit().id(), List.of());
        prior = reserve(actor, "TRANSFER_WITHDRAW", key, body);
        if (prior != null) return prior;
        Context c = locked(actor, taskId, branchId);
        requireCurrent(actor, c, "IN_PROGRESS");
        TransferRow transfer = mapper.lockTransfer(transferId);
        if (transfer == null || transfer.branchId() != branchId || transfer.fromAssignmentId() != c.assignment().id()) throw notFound();
        Instant now = Instant.now();
        changed(mapper.withdrawTransfer(transferId, transfer.version()));
        action(taskId, branchId, c.assignment().id(), transferId, "TRANSFER_WITHDRAW", actor,
                transfer.targetUnitId(), null, null, null, now);
        recordCommand(actor, "TRANSFER_WITHDRAW", key, body, taskId, branchId, now);
        return detailFor(actor, taskId);
    }

    @Transactional(readOnly = true)
    public PageResult<TaskViews.TaskListItem> list(Authentication auth, String tab, int page, int pageSize) {
        guard.require(auth, PermissionCodes.TASK_READ, "没有查看任务的权限");
        Actor actor = actor(auth);
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw bad("INVALID_PAGE", "页码或每页条数不合法");
        }
        String selected = tab == null || tab.isBlank() ? "all" : tab;
        if (!TABS.contains(selected)) {
            throw bad("INVALID_TASK_TAB", "任务列表分类不合法");
        }
        long total = mapper.listCount(actor.unit().id(), selected);
        int offset = Math.toIntExact(Math.min((long) (page - 1) * pageSize, Integer.MAX_VALUE));
        List<TaskViews.TaskListItem> items = mapper.list(actor.unit().id(), selected, pageSize, offset)
                .stream().map(row -> new TaskViews.TaskListItem(
                        id(row.id()), row.taskNo(), row.title(), row.status(),
                        row.initialDueAt(), row.currentDueAt(), row.issuerUnitNameSnapshot(),
                        row.createdAt(), row.openBranchCount(), row.currentResponsibleUnits(),
                        row.myStatus(), row.pendingTransferCount(), row.lastActionAt(),
                        row.overdueBranchCount())).toList();
        return new PageResult<>(items, total, page, pageSize);
    }

    @Transactional(readOnly = true)
    public TaskViews.TaskDetail detail(Authentication auth, long taskId) {
        guard.require(auth, PermissionCodes.TASK_READ, "没有查看任务的权限");
        return detailFor(actor(auth), taskId);
    }

    @Transactional(readOnly = true)
    public List<TaskViews.UnitOption> targets(Authentication auth, String action, Long taskId,
                                               Long branchId) {
        String permission = "transfer".equals(action) ? PermissionCodes.TASK_TRANSFER_REQUEST
                : "dispatch".equals(action) || "reassign".equals(action) ? PermissionCodes.TASK_DISPATCH
                : PermissionCodes.TASK_CREATE;
        guard.require(auth, permission, "没有选择目标单位的权限");
        Actor actor = actor(auth);
        if (!Set.of("create", "dispatch", "transfer", "reassign").contains(action)) {
            throw bad("INVALID_TASK_ACTION", "目标单位查询动作不合法");
        }
        if ("dispatch".equals(action) || "reassign".equals(action)) {
            if (taskId == null || branchId == null) {
                throw bad("INVALID_TASK_BRANCH", "缺少当前任务或分支");
            }
            Context context = visibleContext(actor, taskId, branchId);
            if (context.assignment().toUnitId() != actor.unit().id()) {
                throw forbidden();
            }
        }
        Map<Long, UnitRow> all = new HashMap<>();
        mapper.allUnits().forEach(unit -> all.put(unit.id(), unit));
        return mapper.enabledUnits().stream()
                .filter(unit -> "transfer".equals(action)
                        ? actor.unit().level() == 2 && unit.level() == 2
                          && unit.id() != actor.unit().id()
                          && unit.parentId() != null
                          && unit.parentId().equals(actor.unit().parentId())
                        : unit.parentId() != null && unit.parentId() == actor.unit().id()
                          && unit.level() == actor.unit().level() + 1)
                .map(unit -> new TaskViews.UnitOption(unit.code(), unit.name(), unit.level(),
                        unit.parentId() == null ? null : all.get(unit.parentId()).code()))
                .toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskViews.TaskDetail create(Authentication auth, String key, TaskRequests.Create input) {
        guard.require(auth, PermissionCodes.TASK_CREATE, "没有新建任务的权限");
        Actor actor = actor(auth);
        List<String> codes = distinctCodes(input.targetUnitCodes());
        String title = input.title().strip();
        String instruction = input.instruction().strip();
        String expectedResult = input.expectedResult().strip();
        String sourceResultId = blankToNull(input.sourceResultId());
        Long sourceId = sourceResultId == null ? null : parseId(sourceResultId);
        String digest = digest(title, instruction, expectedResult,
                input.dueAt(), codes, sourceId);
        TaskViews.TaskDetail replay = replay(actor, "CREATE", key, digest);
        if (replay != null) return replay;

        Instant now = Instant.now();
        requireFuture(input.dueAt(), now);
        List<UnitRow> candidates = resolveTargets(codes);
        Map<Long, UnitRow> lockedUnits = lockUnits(actor.unit().id(), candidates.stream().map(UnitRow::id).toList());
        actor = new Actor(actor.userId(), lockedUnits.get(actor.unit().id()));
        List<UnitRow> targets = candidates.stream().map(target -> lockedUnits.get(target.id())).toList();
        replay = reserve(actor, "CREATE", key, digest);
        if (replay != null) return replay;
        requireCanDispatch(actor.unit());
        for (UnitRow target : targets) requireDirect(actor.unit(), target);

        if (sourceId != null) {
            ResultRow source = mapper.resultById(sourceId);
            if (source == null || !canSeeBranch(actor, source.branchId())) throw notFound();
        }

        String taskNo = "TASK-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        mapper.insertOrder(taskNo, actor.unit().id(), actor.unit().name(), actor.userId(),
                title, instruction, expectedResult, input.dueAt(), sourceId);
        long taskId = mapper.lastId();
        for (UnitRow target : targets) {
            createBranch(taskId, null, actor, target, instruction,
                    expectedResult, input.dueAt(), now);
        }
        action(taskId, null, null, null, "CREATE", actor, null, null, input.dueAt(),
                "创建并下发给 " + targets.size() + " 个直属单位", now);
        recordCommand(actor, "CREATE", key, digest, taskId, null, now);
        return detailFor(actor, taskId);
    }

    private TaskViews.TaskDetail detailFor(Actor actor, long taskId) {
        OrderRow order = mapper.order(taskId);
        if (order == null) throw notFound();
        List<BranchRow> allBranches = mapper.branches(taskId);
        List<AssignmentRow> allAssignments = mapper.assignments(taskId);
        List<TransferRow> allTransfers = mapper.transfers(taskId);
        boolean issuer = order.issuerUnitId() == actor.unit().id();
        Set<Long> owned = new HashSet<>();
        Set<Long> pending = new HashSet<>();
        for (AssignmentRow assignment : allAssignments) {
            if (assignment.toUnitId() == actor.unit().id()) owned.add(assignment.branchId());
        }
        for (TransferRow transfer : allTransfers) {
            if (transfer.targetUnitId() == actor.unit().id()
                    && ("AWAITING_TARGET".equals(transfer.status())
                        || "AWAITING_ISSUER".equals(transfer.status()))) {
                pending.add(transfer.branchId());
            }
        }
        if (!issuer && owned.isEmpty() && pending.isEmpty()) throw notFound();
        Set<Long> visible = new HashSet<>(owned);
        visible.addAll(pending);
        if (issuer) allBranches.forEach(branch -> visible.add(branch.id()));
        else visible.addAll(currentBranchScope(actor.unit().id(), allBranches, allAssignments));
        Map<Long, UnitRow> units = new HashMap<>();
        mapper.allUnits().forEach(unit -> units.put(unit.id(), unit));
        Map<Long, ResultRow> results = new HashMap<>();
        mapper.results(taskId).forEach(result -> results.put(result.branchId(), result));
        List<TaskViews.Branch> branches = allBranches.stream()
                .filter(branch -> visible.contains(branch.id()))
                .map(branch -> {
                    boolean limited = !issuer && pending.contains(branch.id())
                            && !owned.contains(branch.id());
                    List<TaskViews.Assignment> assignments = limited ? List.of() : allAssignments.stream()
                            .filter(a -> a.branchId() == branch.id())
                            .map(a -> new TaskViews.Assignment(id(a.id()), id(a.branchId()),
                                    a.fromUnitNameSnapshot(), a.toUnitNameSnapshot(),
                                    a.sourceAction(), a.status(), a.dueAt(), a.acceptedAt(),
                                    a.endedAt(), a.endReason()))
                            .toList();
                    ResultRow row = limited ? null : results.get(branch.id());
                    TaskViews.Result result = row == null ? null : new TaskViews.Result(
                            id(row.id()), id(row.branchId()), row.outcomeCode(),
                            row.handlingDetail(), row.conclusion(),
                            row.suggestedUnitId() == null ? null
                                    : units.get(row.suggestedUnitId()).code(),
                            row.suggestedUnitId() == null ? null
                                    : units.get(row.suggestedUnitId()).name(), row.submittedAt());
                    List<TaskViews.Transfer> transfers = allTransfers.stream()
                            .filter(t -> t.branchId() == branch.id()
                                    && (!limited || t.targetUnitId() == actor.unit().id()))
                            .map(t -> new TaskViews.Transfer(id(t.id()), id(t.branchId()),
                                    units.get(t.targetUnitId()).code(),
                                    units.get(t.targetUnitId()).name(), t.status(), t.reason(),
                                    t.workDone(), t.evidenceSummary(), t.remainingWork(),
                                    t.requestedAt(), t.targetRequiredDurationMinutes(),
                                    t.targetRespondedAt(), t.approvedDueAt(), t.issuerDecidedAt(),
                                    t.targetResponseReason(), t.issuerDecisionReason(),
                                    t.targetUnitId() == actor.unit().id()))
                            .toList();
                    return new TaskViews.Branch(id(branch.id()), id(branch.parentBranchId()),
                            branch.instructionSnapshot(), branch.expectedResultSnapshot(),
                            branch.status(), limited ? null : id(branch.currentAssignmentId()),
                            !limited && allAssignments.stream().anyMatch(a -> branch.currentAssignmentId() != null
                                    && a.id() == branch.currentAssignmentId()
                                    && a.toUnitId() == actor.unit().id()),
                            actor.unit().level() < 3,
                            actor.unit().level() == 2 && actor.unit().parentId() != null
                                    && order.issuerUnitId() == actor.unit().parentId()
                                    && branch.parentBranchId() == null
                                    && branch.originFromUnitId() == order.issuerUnitId(),
                            branch.completedAt(), assignments, result, transfers);
                }).toList();
        List<TaskViews.Action> actions = mapper.actions(taskId).stream()
                .filter(a -> issuer || (a.branchId() != null && visible.contains(a.branchId())
                        && !pending.contains(a.branchId())))
                .map(a -> toAction(a, units)).toList();
        return new TaskViews.TaskDetail(id(order.id()), order.taskNo(), order.title(),
                order.instruction(), order.expectedResult(), order.issuerUnitNameSnapshot(), issuer,
                order.status(), order.initialDueAt(), order.currentDueAt(), order.completedAt(),
                id(order.sourceResultId()), branches, actions);
    }

    private static TaskViews.Action toAction(ActionRow row, Map<Long, UnitRow> units) {
        UnitRow actor = units.get(row.actorUnitId());
        UnitRow target = row.targetUnitId() == null ? null : units.get(row.targetUnitId());
        return new TaskViews.Action(row.actionCode(), id(row.branchId()),
                actor == null ? "历史单位" : actor.name(), target == null ? null : target.name(),
                row.note(), row.oldDueAt(), row.newDueAt(), row.occurredAt());
    }

    private boolean canSeeBranch(Actor actor, long branchId) {
        BranchRow branch = mapper.branch(branchId);
        if (branch == null) return false;
        OrderRow order = mapper.order(branch.taskId());
        if (order == null) return false;
        if (order.issuerUnitId() == actor.unit().id()) return true;
        List<AssignmentRow> assignments = mapper.assignments(order.id());
        if (assignments.stream().anyMatch(a -> a.branchId() == branchId
                && a.toUnitId() == actor.unit().id())) return true;
        return currentBranchScope(actor.unit().id(), mapper.branches(order.id()), assignments).contains(branchId);
    }

    /** 只有仍由本单位负责的分支才能把下级办理范围带入当前视图。 */
    private static Set<Long> currentBranchScope(long unitId, List<BranchRow> branches,
                                                List<AssignmentRow> assignments) {
        Map<Long, AssignmentRow> byId = new HashMap<>();
        assignments.forEach(assignment -> byId.put(assignment.id(), assignment));
        Set<Long> visible = new HashSet<>();
        for (BranchRow branch : branches) {
            AssignmentRow current = branch.currentAssignmentId() == null
                    ? null : byId.get(branch.currentAssignmentId());
            if (current != null && current.toUnitId() == unitId) visible.add(branch.id());
        }
        boolean added;
        do {
            added = false;
            for (BranchRow branch : branches) {
                if (branch.parentBranchId() != null && visible.contains(branch.parentBranchId())) {
                    added |= visible.add(branch.id());
                }
            }
        } while (added);
        return visible;
    }

    private Context visibleContext(Actor actor, long taskId, long branchId) {
        OrderRow order = mapper.order(taskId);
        BranchRow branch = mapper.branch(branchId);
        if (order == null || branch == null || branch.taskId() != taskId || !canSeeBranch(actor, branchId)) {
            throw notFound();
        }
        AssignmentRow assignment = branch.currentAssignmentId() == null ? null
                : mapper.assignment(branch.currentAssignmentId());
        return new Context(order, branch, assignment);
    }

    private Actor actor(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedAccount account)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "请先登录");
        }
        UnitRow unit = mapper.actorUnit(account.userId());
        if (unit == null || !"ENABLED".equals(unit.status())) throw forbidden();
        return new Actor(account.userId(), unit);
    }

    private List<UnitRow> resolveTargets(List<String> codes) {
        List<UnitRow> result = new ArrayList<>();
        for (String code : codes) {
            UnitRow target = mapper.unitByCode(code);
            if (target == null || !"ENABLED".equals(target.status())) {
                throw bad("INVALID_TARGET_UNIT", "目标单位不存在或已停用");
            }
            result.add(target);
        }
        return result;
    }

    private Map<Long, UnitRow> lockUnits(long actorUnitId, List<Long> otherIds) {
        Set<Long> ids = new HashSet<>(otherIds);
        ids.add(actorUnitId);
        Map<Long, UnitRow> locked = new HashMap<>();
        ids.stream().sorted().forEach(id -> {
            UnitRow current = mapper.lockUnitById(id);
            if (current == null || !"ENABLED".equals(current.status())) {
                throw conflict("UNIT_CHANGED", "相关单位已经变化，请刷新后重试");
            }
            locked.put(id, current);
        });
        return locked;
    }

    private static void requireCanDispatch(UnitRow from) {
        if (from.level() >= 3) throw forbidden();
    }

    private static void requireDirect(UnitRow from, UnitRow to) {
        if (to.parentId() == null || to.parentId() != from.id()
                || to.level() != from.level() + 1) {
            throw bad("NOT_DIRECT_SUBORDINATE", "只能向直属下级下发任务");
        }
    }

    private long createBranch(long taskId, Long parentBranchId, Actor actor,
                              UnitRow target, String instruction, String expected,
                              Instant dueAt, Instant now) {
        mapper.insertBranch(taskId, parentBranchId, actor.unit().id(), target.id(),
                instruction, expected);
        long branchId = mapper.lastId();
        mapper.insertAssignment(branchId, null, actor.unit().id(), actor.unit().name(),
                target.id(), target.name(), "DOWNWARD", dueAt, "PENDING_ACCEPT", null, null);
        long assignmentId = mapper.lastId();
        changed(mapper.setCurrentAssignment(branchId, null, assignmentId));
        action(taskId, branchId, assignmentId, null, "DISPATCH", actor, target.id(),
                null, dueAt, null, now);
        return branchId;
    }

    private void action(long taskId, Long branchId, Long assignmentId, Long transferId,
                        String code, Actor actor, Long targetUnitId, Instant oldDue,
                        Instant newDue, String note, Instant at) {
        mapper.insertAction(taskId, branchId, assignmentId, transferId, code,
                actor.unit().id(), actor.userId(), targetUnitId, oldDue, newDue, note, at);
    }

    private TaskViews.TaskDetail replay(Actor actor, String action, String key, String digest) {
        requireKey(key);
        CommandRow existing = mapper.command(actor.unit().id(), action, key);
        if (existing == null) return null;
        return replayed(actor, existing, digest);
    }

    private TaskViews.TaskDetail replayed(Actor actor, CommandRow existing, String digest) {
        if (!existing.requestDigest().equals(digest)) {
            throw conflict("IDEMPOTENCY_CONFLICT", "同一请求键已用于不同内容");
        }
        if (existing.taskId() == null) {
            throw conflict("IDEMPOTENCY_PENDING", "相同请求正在处理，请稍后重试");
        }
        return detailFor(actor, existing.taskId());
    }

    private TaskViews.TaskDetail reserve(Actor actor, String action, String key, String digest) {
        int inserted = mapper.reserveCommand(actor.unit().id(), action, key, digest, Instant.now());
        if (inserted == 1) return null;
        CommandRow existing = mapper.commandLocked(actor.unit().id(), action, key);
        if (existing == null) throw conflict("IDEMPOTENCY_PENDING", "相同请求正在处理，请稍后重试");
        return replayed(actor, existing, digest);
    }

    private void recordCommand(Actor actor, String action, String key, String digest,
                               long taskId, Long branchId, Instant now) {
        changed(mapper.finishCommand(actor.unit().id(), action, key, taskId, branchId));
    }

    private static String digest(Object... values) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                String part = value == null ? "<NULL>" : value.toString();
                byte[] data = part.getBytes(StandardCharsets.UTF_8);
                hash.update(Integer.toString(data.length).getBytes(StandardCharsets.US_ASCII));
                hash.update((byte) ':');
                hash.update(data);
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private static List<String> distinctCodes(List<String> input) {
        List<String> codes = input.stream().map(String::strip).sorted().toList();
        if (codes.isEmpty() || codes.stream().anyMatch(String::isBlank)
                || new HashSet<>(codes).size() != codes.size()) {
            throw bad("INVALID_TARGET_UNITS", "目标单位不能为空或重复");
        }
        return codes;
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private static void requireFuture(Instant due, Instant now) {
        if (due == null || !due.isAfter(now)) {
            throw bad("INVALID_TASK_DUE", "截止时间必须晚于当前时间");
        }
    }

    public static long parseId(String text) {
        try {
            long value = Long.parseLong(text);
            if (value > 0) return value;
        } catch (NumberFormatException ignored) { }
        throw bad("INVALID_ID", "资源标识不合法");
    }

    private static String id(Long value) { return value == null ? null : Long.toString(value); }
    private static String id(long value) { return Long.toString(value); }
    private static void changed(int count) {
        if (count != 1) throw conflict("TASK_STATE_CHANGED", "任务状态已变化，请刷新后重试");
    }
    private static void requireKey(String key) {
        if (key == null || key.isBlank() || key.length() > 80
                || !key.matches("[A-Za-z0-9._:-]+")) {
            throw bad("INVALID_IDEMPOTENCY_KEY", "请求键格式不合法");
        }
    }
    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "没有操作该任务的权限");
    }
    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在或不可访问");
    }
}
