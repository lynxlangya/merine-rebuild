package com.merine.rebuild.task.dto;

import java.time.Instant;
import java.util.List;

/** API 视图：技术 ID 均以字符串返回，历史事实不暴露内部行对象。 */
public final class TaskViews {
    private TaskViews() { }

    public record TaskListItem(String id, String taskNo, String title, String status,
                               Instant initialDueAt, Instant currentDueAt,
                               String issuerUnitName, Instant createdAt, long openBranchCount,
                               String currentResponsibleUnits, String myStatus,
                               long pendingTransferCount, Instant lastActionAt,
                               long overdueBranchCount) { }
    public record AllowedAction(String code, boolean enabled, String reasonCode, String reason,
                                String transferId) { }
    public record UnitOption(String code, String name, int level, String parentCode) { }
    public record Assignment(String id, String branchId, String fromUnitName, String toUnitName,
                             String sourceAction, String status, Instant dueAt,
                             Instant acceptedAt, Instant endedAt, String endReason, String endReasonCode) { }
    public record Result(String id, String branchId, String outcomeCode,
                         String handlingDetail, String conclusion, String suggestedUnitCode,
                         String suggestedUnitName,
                         Instant submittedAt, String submittedByName) { }
    public record Transfer(String id, String branchId, String targetUnitCode,
                           String targetUnitName, String status,
                           String reason, String workDone, String evidenceSummary,
                           String remainingWork, Instant requestedAt,
                           Integer requiredDurationMinutes, Instant targetRespondedAt,
                           Instant approvedDueAt, Instant issuerDecidedAt,
                           String targetResponseReason, String issuerDecisionReason,
                           boolean targetMine, String fromUnitName) { }
    public record Branch(String id, String parentBranchId, String instruction,
                         String expectedResult, String status, String currentAssignmentId,
                         boolean currentMine, boolean canDispatchDownward,
                         boolean canTransferPeer,
                         Instant completedAt, List<Assignment> assignments,
                         Result result, List<Transfer> transfers, List<AllowedAction> allowedActions) { }
    public record Action(String code, String branchId, String actorUnitName,
                         String targetUnitName, String note,
                         Instant oldDueAt, Instant newDueAt, Instant occurredAt, String actorUserName, String reasonCode) { }
    public record TaskDetail(String id, String taskNo, String title, String instruction,
                             String expectedResult, String issuerUnitName, boolean issuerMine, String status,
                             Instant initialDueAt, Instant currentDueAt, Instant completedAt,
                             String sourceResultId, List<Branch> branches, List<Action> actions,
                             String issuerUserName, Instant createdAt, String conclusion, String closedByName,
                             List<AllowedAction> allowedActions) { }
}
