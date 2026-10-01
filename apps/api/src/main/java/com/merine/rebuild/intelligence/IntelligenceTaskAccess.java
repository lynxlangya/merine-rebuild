package com.merine.rebuild.intelligence;

import com.merine.rebuild.auth.AuthenticatedAccount;
import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.common.CommandDigest;
import com.merine.rebuild.common.PageResult;
import com.merine.rebuild.intelligence.dto.AssessmentRequests.RecordAssessment;
import com.merine.rebuild.intelligence.dto.AssessmentViews.Assessment;
import com.merine.rebuild.intelligence.dto.AssessmentViews.AssessmentContext;
import com.merine.rebuild.intelligence.dto.AssessmentViews.SignedReceipt;
import com.merine.rebuild.intelligence.persistence.AssessmentMapper;
import com.merine.rebuild.intelligence.persistence.IntelligenceMapper;
import com.merine.rebuild.system.security.PermissionCodes;
import com.merine.rebuild.system.security.PermissionGuard;
import com.merine.rebuild.system.user.authorization.AdminCoverageGuard;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 任务模块可用的情报能力；不暴露 Mapper 或持久化投射。写入加入调用方事务。 */
@Service
public class IntelligenceTaskAccess {
    private static final Set<String> RECOMMENDATIONS = Set.of("VERIFY", "HANDLE", "WATCH", "NO_ACTION");
    private final IntelligenceMapper intel;
    private final AssessmentMapper assessments;
    private final PermissionGuard guard;
    private final AdminCoverageGuard authorization;

    public IntelligenceTaskAccess(IntelligenceMapper intel, AssessmentMapper assessments, PermissionGuard guard,
                                  AdminCoverageGuard authorization) {
        this.intel = intel;
        this.assessments = assessments;
        this.guard = guard;
        this.authorization = authorization;
    }

    public record Source(long topicId, String topicNo, String title, String status, boolean sourceMine,
                         long unitId, int unitLevel, long userId, String unitName, String userName,
                         Long lastSupplementId, List<SignedReceipt> signedReceipts) { }

    @Transactional(readOnly = true)
    public Source read(Authentication auth, long topicId) { return source(auth, topicId, false); }

    /** 可选原文入口：没有实际数据范围是正常结果，不能将调用方事务标记回滚。 */
    @Transactional(readOnly = true)
    public Source readIfVisible(Authentication auth, long topicId) {
        try {
            return source(auth, topicId, false);
        } catch (ApiException e) {
            if (e.status() == HttpStatus.NOT_FOUND) return null;
            throw e;
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Source lock(Authentication auth, long topicId) { return source(auth, topicId, true); }

    private Source source(Authentication auth, long topicId, boolean write) {
        guard.require(auth, PermissionCodes.INTEL_READ, "没有信息流转查看权限");
        if (!(auth.getPrincipal() instanceof AuthenticatedAccount account)) throw denied();
        if (write) authorization.lock();
        var units = write ? intel.lockUnits() : intel.units();
        var actor = write ? intel.lockActor(account.userId()) : intel.actor(account.userId());
        if (actor == null || actor.authorizationVersion() != account.authorizationVersion()) throw denied();
        var unit = units.stream().filter(u -> u.id() == actor.unitId() && "ENABLED".equals(u.status()))
                .findFirst().orElseThrow(IntelligenceTaskAccess::denied);
        var topic = write ? intel.lockTopic(topicId) : intel.topic(topicId);
        if (topic == null || (topic.sourceUnitId() != unit.id()
                && (!"PUBLISHED".equals(topic.status()) || intel.received(topicId, unit.id()) == 0))) {
            throw notFound();
        }
        var receipts = write ? intel.lockReceipts(topicId) : intel.receipts(topicId);
        var signed = receipts.stream().filter(r -> r.toUnitId() == unit.id() && r.signedAt() != null)
                .sorted(Comparator.comparing(IntelligenceMapper.ReceiptRow::signedAt).reversed()
                        .thenComparing(Comparator.comparingLong(IntelligenceMapper.ReceiptRow::id).reversed()))
                .map(r -> new SignedReceipt(Long.toString(r.id()), r.fromUnitName(), r.signedAt())).toList();
        var supplements = write ? intel.lockSupplements(topicId) : intel.supplements(topicId);
        Long checkpoint = supplements.stream().map(IntelligenceMapper.SupplementRow::id)
                .max(Long::compareTo).orElse(null);
        return new Source(topic.id(), topic.topicNo(), topic.title(), topic.status(), topic.sourceUnitId() == unit.id(),
                unit.id(), unit.level(), actor.userId(), unit.name(), actor.userName(), checkpoint, signed);
    }

    @Transactional(readOnly = true)
    public AssessmentContext context(Authentication auth, long topicId) {
        Source source = read(auth, topicId);
        String code = !"PUBLISHED".equals(source.status()) ? "TOPIC_DRAFT"
                : !source.sourceMine() && source.signedReceipts().isEmpty() ? "SIGN_REQUIRED" : null;
        boolean permission = has(auth, PermissionCodes.INTEL_ASSESS);
        String reason = !permission ? "没有记录研判权限" : "TOPIC_DRAFT".equals(code) ? "请先发出情报"
                : code != null ? "请先签收" : null;
        return new AssessmentContext(source.sourceMine(), permission && code == null,
                permission ? code : "FORBIDDEN", reason, source.signedReceipts());
    }

    @Transactional(readOnly = true)
    public PageResult<Assessment> list(Authentication auth, long topicId, int page, int size) {
        Source source = read(auth, topicId);
        page(page, size);
        var items = assessments.list(topicId, source.unitId(), (long) (page - 1) * size, size).stream()
                .map(IntelligenceTaskAccess::view).toList();
        return new PageResult<>(items, assessments.count(topicId, source.unitId()), page, size);
    }

    @Transactional(readOnly = true)
    public Assessment ownAssessment(Authentication auth, long topicId, long assessmentId) {
        Source source = read(auth, topicId);
        var assessment = assessments.own(assessmentId, topicId, source.unitId());
        return assessment == null ? null : view(assessment);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Assessment record(Authentication auth, long topicId, String key, RecordAssessment input) {
        Source source = lock(auth, topicId);
        guard.require(auth, PermissionCodes.INTEL_ASSESS, "没有记录研判权限");
        requireReady(source);
        CommandDigest.key(key);
        Long receipt = source.sourceMine() ? null : id(input.receiptId());
        if (source.sourceMine() && input.receiptId() != null) {
            throw bad("INVALID_RECEIPT", "源头研判不绑定接收回执");
        }
        requireReceipt(source, receipt);
        String analysis = text(input.analysis(), 4000);
        String recommendation = text(input.recommendationCode(), 24);
        if (!RECOMMENDATIONS.contains(recommendation)) throw bad("INVALID_RECOMMENDATION", "建议行动无效");
        String digest = CommandDigest.digest(topicId, receipt, analysis, recommendation);
        var previous = assessments.replay(source.unitId(), key);
        if (previous != null) {
            if (!previous.requestDigest().equals(digest)) {
                throw conflict("IDEMPOTENCY_CONFLICT", "同一请求键已用于不同内容");
            }
            return view(previous);
        }
        // 字典决定新记录可选性，固定 code 决定业务语义；重放不受后来停用影响。
        if (!intel.recommendationEnabled(recommendation)) {
            throw conflict("RECOMMENDATION_DISABLED", "建议行动已停用，请重新选择");
        }
        assessments.insert(topicId, receipt, analysis, recommendation, source.unitId(), source.userId(),
                source.unitName(), source.userName(), key, digest, Instant.now());
        return view(assessments.locked(intel.lastId()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Assessment requireAssessment(Authentication auth, long topicId, long assessmentId) {
        Source source = lock(auth, topicId);
        requireReady(source);
        var assessment = assessments.locked(assessmentId);
        if (assessment == null || assessment.topicId() != topicId || assessment.unitId() != source.unitId()) {
            throw notFound();
        }
        requireReceipt(source, assessment.receiptId());
        return view(assessment);
    }

    public static void requireReady(Source source) {
        if (!"PUBLISHED".equals(source.status())) throw conflict("TOPIC_DRAFT", "请先发出情报");
        if (!source.sourceMine() && source.signedReceipts().isEmpty()) throw conflict("SIGN_REQUIRED", "请先签收");
    }

    private static void requireReceipt(Source source, Long receipt) {
        if (source.sourceMine() && receipt == null) return;
        if (receipt == null || source.signedReceipts().stream().noneMatch(r -> r.id().equals(receipt.toString()))) {
            throw conflict("SIGN_REQUIRED", "请选择本单位已签收的回执");
        }
    }

    private static Assessment view(AssessmentMapper.Row assessment) {
        return new Assessment(Long.toString(assessment.id()),
                assessment.receiptId() == null ? null : assessment.receiptId().toString(), assessment.analysis(),
                assessment.recommendationCode(), assessment.unitName(), assessment.userName(), assessment.createdAt());
    }

    public static boolean has(Authentication auth, String code) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(code));
    }
    public static Long id(String value) { return value == null ? null : IntelligenceService.parseId(value); }
    public static String text(String input, int max) {
        String value = input == null ? "" : input.strip();
        if (value.isEmpty() || value.length() > max) throw bad("INVALID_CONTENT", "内容不能为空且须符合长度限制");
        return value;
    }
    public static void page(int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw bad("INVALID_PAGE", "页码从1开始，每页1–100条");
    }
    private static ApiException denied() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "权限或当前身份已变化");
    }
    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TOPIC_NOT_FOUND", "情报或研判不存在或不可访问");
    }
    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }
    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
