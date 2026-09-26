package com.merine.rebuild.task.persistence;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** task 表的唯一 SQL 写入入口；跨模块仅只读用户与单位的当前身份事实。 */
@Mapper
public interface TaskMapper {
    record UnitRow(long id, String code, String name, Long parentId, int level, String status) { }
    record OrderRow(long id, String taskNo, long issuerUnitId, String issuerUnitNameSnapshot,
                    long issuerUserId, String title, String instruction, String expectedResult,
                    Instant initialDueAt, Instant currentDueAt, String status,
                    Instant completedAt, int version, Long sourceResultId, Instant createdAt) { }
    record BranchRow(long id, long taskId, Long parentBranchId, long originFromUnitId,
                     long originToUnitId, String instructionSnapshot, String expectedResultSnapshot,
                     Long currentAssignmentId, String status, Instant completedAt, int version,
                     Instant createdAt) { }
    record AssignmentRow(long id, long branchId, Long previousAssignmentId, long fromUnitId,
                         String fromUnitNameSnapshot, long toUnitId, String toUnitNameSnapshot,
                         String sourceAction, Instant dueAt, String status,
                         Long acceptedByUserId, Instant acceptedAt, Long endedByUserId,
                         Instant endedAt, String endReason, int version, Instant createdAt) { }
    record TransferRow(long id, long branchId, long fromAssignmentId, long targetUnitId,
                       String reason, String workDone, String evidenceSummary, String remainingWork,
                       long requestedByUserId, Instant requestedAt, String status,
                       Long targetRespondedByUserId, Instant targetRespondedAt,
                       String targetResponseReason, Integer targetRequiredDurationMinutes,
                       Instant approvedDueAt, Long issuerDecidedByUserId, Instant issuerDecidedAt,
                       String issuerDecisionReason, int version) { }
    record ResultRow(long id, long branchId, long assignmentId, String outcomeCode,
                     String handlingDetail, String conclusion, Long suggestedUnitId,
                     long submittedByUserId, Instant submittedAt) { }
    record CommandRow(long id, long actorUnitId, String actionCode, String idempotencyKey,
                      String requestDigest, Long taskId, Long branchId, Instant createdAt) { }
    record ListRow(long id, String taskNo, String title, String status, Instant initialDueAt,
                   Instant currentDueAt, String issuerUnitNameSnapshot, Instant createdAt,
                   long openBranchCount, String currentResponsibleUnits, String myStatus,
                   long pendingTransferCount, Instant lastActionAt, long overdueBranchCount) { }
    record ActionRow(long id, long taskId, Long branchId, Long assignmentId,
                     Long transferRequestId, String actionCode, long actorUnitId,
                     long actorUserId, Long targetUnitId, Instant oldDueAt, Instant newDueAt,
                     String note, Instant occurredAt) { }

    UnitRow actorUnit(@Param("userId") long userId);
    UnitRow unitByCode(@Param("code") String code);
    UnitRow lockUnitById(@Param("id") long id);
    OrderRow order(@Param("id") long id);
    OrderRow lockOrder(@Param("id") long id);
    BranchRow branch(@Param("id") long id);
    BranchRow lockBranch(@Param("id") long id);
    AssignmentRow assignment(@Param("id") long id);
    AssignmentRow lockAssignment(@Param("id") long id);
    TransferRow transfer(@Param("id") long id);
    TransferRow lockTransfer(@Param("id") long id);
    ResultRow result(@Param("branchId") long branchId);
    ResultRow resultById(@Param("id") long id);
    List<BranchRow> branches(@Param("taskId") long taskId);
    List<AssignmentRow> assignments(@Param("taskId") long taskId);
    List<TransferRow> transfers(@Param("taskId") long taskId);
    List<TransferRow> transfersByBranch(@Param("branchId") long branchId);
    List<ResultRow> results(@Param("taskId") long taskId);
    List<ActionRow> actions(@Param("taskId") long taskId);
    long listCount(@Param("unitId") long unitId, @Param("tab") String tab);
    List<ListRow> list(@Param("unitId") long unitId, @Param("tab") String tab,
                       @Param("limit") int limit, @Param("offset") int offset);
    List<UnitRow> enabledUnits();
    List<UnitRow> allUnits();
    int openChildren(@Param("branchId") long branchId);
    int openBranches(@Param("taskId") long taskId);
    int unitActiveReferences(@Param("unitId") long unitId);
    int previousDivision(@Param("branchId") long branchId, @Param("unitId") long unitId);
    CommandRow command(@Param("unitId") long unitId, @Param("action") String action,
                       @Param("key") String key);
    CommandRow commandLocked(@Param("unitId") long unitId, @Param("action") String action,
                             @Param("key") String key);
    long lastId();

    int insertOrder(@Param("taskNo") String taskNo, @Param("issuerUnitId") long issuerUnitId,
                    @Param("issuerUnitName") String issuerUnitName, @Param("issuerUserId") long issuerUserId,
                    @Param("title") String title, @Param("instruction") String instruction,
                    @Param("expectedResult") String expectedResult, @Param("dueAt") Instant dueAt,
                    @Param("sourceResultId") Long sourceResultId);
    int insertBranch(@Param("taskId") long taskId, @Param("parentBranchId") Long parentBranchId,
                     @Param("fromUnitId") long fromUnitId, @Param("toUnitId") long toUnitId,
                     @Param("instruction") String instruction,
                     @Param("expectedResult") String expectedResult);
    int insertAssignment(@Param("branchId") long branchId,
                         @Param("previousId") Long previousId,
                         @Param("fromUnitId") long fromUnitId,
                         @Param("fromUnitName") String fromUnitName,
                         @Param("toUnitId") long toUnitId,
                         @Param("toUnitName") String toUnitName,
                         @Param("sourceAction") String sourceAction,
                         @Param("dueAt") Instant dueAt,
                         @Param("status") String status,
                         @Param("acceptedBy") Long acceptedBy,
                         @Param("acceptedAt") Instant acceptedAt);
    int setCurrentAssignment(@Param("branchId") long branchId,
                             @Param("expectedId") Long expectedId,
                             @Param("assignmentId") long assignmentId);
    int acceptAssignment(@Param("id") long id, @Param("version") int version,
                         @Param("userId") long userId, @Param("at") Instant at);
    int endAssignment(@Param("id") long id, @Param("version") int version,
                      @Param("expectedStatus") String expectedStatus,
                      @Param("newStatus") String newStatus,
                      @Param("userId") long userId, @Param("at") Instant at,
                      @Param("reason") String reason);
    int completeBranch(@Param("id") long id, @Param("version") int version,
                       @Param("at") Instant at);
    int completeOrder(@Param("id") long id, @Param("at") Instant at);
    int extendOrder(@Param("id") long id, @Param("oldDue") Instant oldDue,
                    @Param("newDue") Instant newDue);
    int insertTransfer(@Param("branchId") long branchId,
                       @Param("assignmentId") long assignmentId,
                       @Param("targetUnitId") long targetUnitId,
                       @Param("reason") String reason, @Param("workDone") String workDone,
                       @Param("evidence") String evidence, @Param("remaining") String remaining,
                       @Param("userId") long userId, @Param("at") Instant at);
    int respondTransfer(@Param("id") long id, @Param("version") int version,
                        @Param("status") String status, @Param("userId") long userId,
                        @Param("at") Instant at, @Param("reason") String reason,
                        @Param("minutes") Integer minutes);
    int decideTransfer(@Param("id") long id, @Param("version") int version,
                       @Param("status") String status, @Param("userId") long userId,
                       @Param("at") Instant at, @Param("reason") String reason,
                       @Param("dueAt") Instant dueAt);
    int withdrawTransfer(@Param("id") long id, @Param("version") int version);
    int insertResult(@Param("branchId") long branchId,
                     @Param("assignmentId") long assignmentId,
                     @Param("outcome") String outcome,
                     @Param("handling") String handling,
                     @Param("conclusion") String conclusion,
                     @Param("suggestedUnitId") Long suggestedUnitId,
                     @Param("userId") long userId, @Param("at") Instant at);
    int insertAction(@Param("taskId") long taskId, @Param("branchId") Long branchId,
                     @Param("assignmentId") Long assignmentId,
                     @Param("transferId") Long transferId,
                     @Param("action") String action,
                     @Param("unitId") long unitId, @Param("userId") long userId,
                     @Param("targetUnitId") Long targetUnitId,
                     @Param("oldDue") Instant oldDue, @Param("newDue") Instant newDue,
                     @Param("note") String note, @Param("at") Instant at);
    int reserveCommand(@Param("unitId") long unitId, @Param("action") String action,
                      @Param("key") String key, @Param("digest") String digest,
                      @Param("at") Instant at);
    int finishCommand(@Param("unitId") long unitId, @Param("action") String action,
                      @Param("key") String key, @Param("taskId") long taskId,
                      @Param("branchId") Long branchId);
}
