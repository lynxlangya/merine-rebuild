package com.merine.rebuild.task;

import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.common.CommandDigest;
import com.merine.rebuild.common.PageResult;
import com.merine.rebuild.intelligence.IntelligenceTaskAccess;
import com.merine.rebuild.intelligence.dto.AssessmentRequests.RecordAssessment;
import com.merine.rebuild.system.security.PermissionCodes;
import com.merine.rebuild.system.security.PermissionGuard;
import com.merine.rebuild.task.dto.TaskIntelligenceRequests.CreateIntelligenceTask;
import com.merine.rebuild.task.dto.TaskIntelligenceViews.IntelligenceTaskContext;
import com.merine.rebuild.task.dto.TaskIntelligenceViews.LinkedIntelligenceTask;
import com.merine.rebuild.task.dto.TaskIntelligenceViews.TaskIntelligenceSource;
import com.merine.rebuild.task.dto.TaskRequests;
import com.merine.rebuild.task.dto.TaskViews;
import com.merine.rebuild.task.persistence.TaskIntelligenceMapper;
import com.merine.rebuild.task.persistence.TaskMapper;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 任务模块编排来源关联和任务创建，只调用情报公开能力；背景摘要不扩大原文授权。 */
@Service
public class TaskIntelligenceService {
    private final TaskService tasks;
    private final TaskMapper task;
    private final TaskIntelligenceMapper links;
    private final IntelligenceTaskAccess intel;
    private final PermissionGuard guard;

    public TaskIntelligenceService(TaskService tasks, TaskMapper task, TaskIntelligenceMapper links,
                                   IntelligenceTaskAccess intel, PermissionGuard guard) {
        this.tasks = tasks;
        this.task = task;
        this.links = links;
        this.intel = intel;
        this.guard = guard;
    }

    @Transactional(readOnly = true)
    public IntelligenceTaskContext context(Authentication auth, long topicId) {
        createPermissions(auth);
        var source = intel.read(auth, topicId);
        var assessment = intel.context(auth, topicId);
        var ownAssessments = intel.list(auth, topicId, 1, 20).items();
        var targets = source.unitLevel() < 3
                ? tasks.targets(auth, "create", null, null) : List.<TaskViews.UnitOption>of();
        String reason = null;
        if (!"PUBLISHED".equals(source.status())) reason = "请先发出情报";
        else if (!source.sourceMine() && source.signedReceipts().isEmpty()) reason = "请先签收";
        else if (targets.isEmpty()) reason = "没有可下发的直属单位";
        else if (ownAssessments.isEmpty() && !assessment.canAssess()) {
            reason = "本单位没有研判，且当前账号没有记录研判权限";
        }
        return new IntelligenceTaskContext(assessment, ownAssessments, targets,
                str(source.lastSupplementId()), reason == null, reason);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskViews.TaskDetail create(Authentication auth, long topicId, String key, CreateIntelligenceTask raw) {
        createPermissions(auth);
        CommandDigest.key(key);
        if ((raw.assessmentId() == null) == (raw.newAssessment() == null)) {
            throw bad("INVALID_ASSESSMENT", "请选择已有研判或填写新研判，不能同时提交");
        }
        var source = intel.lock(auth, topicId);
        IntelligenceTaskAccess.requireReady(source);
        var codes = raw.targetUnitCodes().stream().map(String::strip).sorted().toList();
        String title = IntelligenceTaskAccess.text(raw.title(), 160);
        String instruction = IntelligenceTaskAccess.text(raw.instruction(), 4000);
        String expected = IntelligenceTaskAccess.text(raw.expectedResult(), 1000);
        String background = IntelligenceTaskAccess.text(raw.backgroundSummary(), 4000);
        RecordAssessment fresh = raw.newAssessment() == null ? null : new RecordAssessment(
                raw.newAssessment().receiptId(),
                IntelligenceTaskAccess.text(raw.newAssessment().analysis(), 4000),
                IntelligenceTaskAccess.text(raw.newAssessment().recommendationCode(), 24));
        if (fresh != null) guard.require(auth, PermissionCodes.INTEL_ASSESS, "没有记录研判权限");
        Long assessmentId = IntelligenceTaskAccess.id(raw.assessmentId());
        Long checkpoint = IntelligenceTaskAccess.id(raw.sourceSupplementCheckpoint());
        String digest = CommandDigest.digest(topicId, title, instruction, expected, raw.dueAt(),
                codes, background, assessmentId, fresh, checkpoint);
        var old = task.commandLocked(source.unitId(), "CREATE_INTEL", key);
        if (old != null) return replay(auth, old, digest);
        if (!Objects.equals(checkpoint, source.lastSupplementId())) {
            throw conflict("SOURCE_CHANGED", "来源情报有新说明，请重新核对后提交");
        }
        if (task.reserveCommand(source.unitId(), "CREATE_INTEL", key, digest, Instant.now()) != 1) {
            return replay(auth, task.commandLocked(source.unitId(), "CREATE_INTEL", key), digest);
        }
        if (fresh != null) {
            assessmentId = Long.parseLong(intel.record(auth, topicId,
                    CommandDigest.digest("task-assessment", key), fresh).id());
        } else {
            intel.requireAssessment(auth, topicId, assessmentId);
        }
        var detail = tasks.create(auth, CommandDigest.digest("intel-task", key),
                new TaskRequests.Create(title, instruction, expected, raw.dueAt(), codes, null));
        long taskId = Long.parseLong(detail.id());
        if (links.background(taskId, background) != 1) {
            throw conflict("TASK_STATE_CHANGED", "任务背景保存失败");
        }
        links.sourceInsert(taskId, topicId, assessmentId, checkpoint, source.unitId(), source.userId(),
                source.unitName(), source.userName(), Instant.now());
        task.finishCommand(source.unitId(), "CREATE_INTEL", key, taskId, null);
        return detail;
    }

    private TaskViews.TaskDetail replay(Authentication auth, TaskMapper.CommandRow old, String digest) {
        if (old == null || old.taskId() == null) {
            throw conflict("IDEMPOTENCY_PENDING", "相同请求正在处理，请重试原提交");
        }
        if (!old.requestDigest().equals(digest)) {
            throw conflict("IDEMPOTENCY_CONFLICT", "同一请求键已用于不同内容");
        }
        return tasks.detail(auth, old.taskId());
    }

    @Transactional(readOnly = true)
    public PageResult<LinkedIntelligenceTask> list(Authentication auth, long topicId, int page, int size) {
        guard.require(auth, PermissionCodes.TASK_READ, "没有查看任务权限");
        var source = intel.read(auth, topicId);
        IntelligenceTaskAccess.page(page, size);
        return new PageResult<>(links.list(topicId, source.unitId(), (long) (page - 1) * size, size),
                links.count(topicId, source.unitId()), page, size);
    }

    @Transactional(readOnly = true)
    public TaskIntelligenceSource source(Authentication auth, long taskId) {
        tasks.detail(auth, taskId);
        var link = links.source(taskId);
        if (link == null) return new TaskIntelligenceSource(false, null, null, null, null, false, null);
        if (!IntelligenceTaskAccess.has(auth, PermissionCodes.INTEL_READ)) return hidden(link.backgroundSummary());
        var source = intel.readIfVisible(auth, link.topicId());
        if (source == null) return hidden(link.backgroundSummary());
        return new TaskIntelligenceSource(true, link.backgroundSummary(), Long.toString(source.topicId()),
                source.topicNo(), source.title(), !Objects.equals(link.lastSupplementId(), source.lastSupplementId()),
                intel.ownAssessment(auth, link.topicId(), link.assessmentId()));
    }

    private static TaskIntelligenceSource hidden(String background) {
        return new TaskIntelligenceSource(true, background, null, null, null, false, null);
    }

    private void createPermissions(Authentication auth) {
        guard.require(auth, PermissionCodes.INTEL_CREATE_TASK, "没有基于情报发任务权限");
        guard.require(auth, PermissionCodes.TASK_READ, "没有查看任务权限");
        guard.require(auth, PermissionCodes.TASK_CREATE, "没有创建任务权限");
    }

    private static String str(Long value) { return value == null ? null : value.toString(); }
    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
