package com.merine.rebuild.intelligence;

import com.merine.rebuild.auth.AuthenticatedAccount;
import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.common.PageResult;
import com.merine.rebuild.intelligence.dto.IntelligenceRequests.*;
import com.merine.rebuild.intelligence.dto.IntelligenceViews.*;
import com.merine.rebuild.intelligence.persistence.IntelligenceMapper;
import com.merine.rebuild.intelligence.persistence.IntelligenceMapper.*;
import com.merine.rebuild.system.security.PermissionGuard;
import com.merine.rebuild.system.security.PermissionCodes;
import com.merine.rebuild.system.user.authorization.AdminCoverageGuard;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 情报共享用例：冻结传播范围，按实际送达及具体发送链授权，不产生办理义务。 */
@Service
public class IntelligenceService {

    private final IntelligenceMapper mapper;
    private final PermissionGuard guard;
    private final AdminCoverageGuard authorization;
    private final ObjectMapper json;
    private final IntelligenceTaskAccess taskAccess;

    public IntelligenceService(IntelligenceMapper mapper, PermissionGuard guard,
            AdminCoverageGuard authorization, ObjectMapper json, IntelligenceTaskAccess taskAccess) {
        this.mapper = mapper;
        this.guard = guard;
        this.authorization = authorization;
        this.json = json;
        this.taskAccess = taskAccess;
    }

    private record Actor(ActorRow row, UnitRow unit, List<UnitRow> units, Set<String> permissions) {
        long unitId() {
            return unit.id();
        }
        boolean can(String code) {
            return permissions.contains(code);
        }
    }

    @Transactional(readOnly = true)
    public PageResult<IntelligenceListItem> list(Authentication auth, String view, String status,
            String keyword, int page, int size) {
        Actor actor = actor(auth, false);
        if (!Set.of("received", "sent").contains(view) ||
                !(view.equals("sent") ? Set.of("all", "DRAFT", "PUBLISHED")
                        : Set.of("all", "pending", "signed")).contains(status)) {
            throw bad("INVALID_FILTER", "列表筛选无效");
        }
        if (page < 1 || size < 1 || size > 100) throw bad("INVALID_PAGE", "页码从 1 开始，每页 1–100 条");
        String pattern = keyword == null || keyword.isBlank() ? null
                : "%" + keyword.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        long total = mapper.count(actor.unitId(), view, status, pattern);
        List<IntelligenceListItem> items = mapper.list(actor.unitId(), view, status, pattern, (long) (page - 1) * size, size).stream()
                .map(row -> new IntelligenceListItem(id(row.id()), row.topicNo(), row.title(), row.status(),
                        row.sourceUnitName(), row.createdAt(), row.publishedAt(),
                        row.pendingReceiptCount(), row.myReceiptCount())).toList();
        return new PageResult<>(items, total, page, size);
    }

    @Transactional(readOnly = true)
    public IntelligenceDetail detail(Authentication auth, long topicId) {
        Actor actor = actor(auth, false);
        return detailFor(actor, visible(actor, topicId, false));
    }

    @Transactional(readOnly = true)
    public List<IntelligenceUnitOption> options(Authentication auth, String action, Long topicId, Long receiptId) {
        Actor actor = actor(auth, false);
        String permission = switch (action) {
            case "create" -> PermissionCodes.INTEL_CREATE;
            case "update" -> PermissionCodes.INTEL_UPDATE;
            case "send" -> PermissionCodes.INTEL_SEND;
            case "forward" -> PermissionCodes.INTEL_FORWARD;
            default -> throw bad("INVALID_ACTION", "单位候选动作无效");
        };
        require(actor, permission);
        List<UnitRow> candidates = actor.units();
        if (!action.equals("create")) {
            if (topicId == null) throw bad("MISSING_TOPIC", "需要指定情报");
            TopicRow topic = visible(actor, topicId, false);
            FlowState state = flowState(topic.id());
            ReceiptRow receipt = action.equals("forward") && receiptId != null ? ownReceipt(actor, state, receiptId) : null;
            if (action.equals("forward") && receipt == null) throw bad("MISSING_RECEIPT", "需要指定接收回执");
            enforce(precondition(actor, topic, receipt, action, state));
            if (action.equals("send") || action.equals("forward")) {
                candidates = eligibleTargets(actor, state, receipt);
            }
        }
        Map<Long, String> codes = actor.units().stream().collect(Collectors.toMap(UnitRow :: id, UnitRow :: code));
        return candidates.stream()
                .map(u -> new IntelligenceUnitOption(u.code(), u.name(),
                        u.parentId() == null ? null : codes.get(u.parentId()),
                        u.level(), enabled(u), enabled(u) && legal(actor.unit(), u))).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public IntelligenceDetail create(Authentication auth, String key, IntelligenceDraftRequest raw) {
        Actor actor = actor(auth, true);
        require(actor, PermissionCodes.INTEL_CREATE);
        IntelligenceDraftRequest input = draft(raw);
        Long replay = reserve(actor, "create", key, input);
        if (replay != null) return detailFor(actor, visible(actor, replay, true));
        List<UnitRow> scope = resolve(actor, input.scopeUnitCodes());
        List<UnitRow> targets = resolve(actor, input.targetUnitCodes());
        validateTargets(actor.unit(), targets, scope.stream().map(UnitRow :: id).collect(Collectors.toSet()));
        Instant now = Instant.now();
        LocalDate day = now.atZone(ZoneId.of("Asia/Shanghai")).toLocalDate();
        mapper.reserveNumber(day);
        int next = mapper.nextNumber(day);
        if (next > 999999) throw conflict("NUMBER_EXHAUSTED", "当天情报编号已用尽");
        mapper.incrementNumber(day);
        String number = "QB-" + day.format(DateTimeFormatter.BASIC_ISO_DATE) + "-%06d".formatted(next);
        mapper.insertTopic(number, actor.row(), actor.unit(), input.title(), input.body(), input.note(), now);
        long topicId = mapper.lastId();
        saveChoices(topicId, scope, targets);
        mapper.completeCommand(actor.unitId(), "create", key, topicId);
        return detailFor(actor, mapper.topic(topicId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public IntelligenceDetail update(Authentication auth, long topicId, String key, IntelligenceDraftRequest raw) {
        Actor actor = actor(auth, true);
        require(actor, PermissionCodes.INTEL_UPDATE);
        TopicRow topic = visible(actor, topicId, true);
        IntelligenceDraftRequest input = draft(raw);
        String action = "update:" + topicId;
        Long replay = reserve(actor, action, key, input);
        if (replay != null) return detailFor(actor, visible(actor, replay, true));
        enforce(precondition(actor, topic, null, "update", flowState(topicId)));
        List<UnitRow> scope = resolve(actor, input.scopeUnitCodes());
        List<UnitRow> targets = resolve(actor, input.targetUnitCodes());
        validateTargets(actor.unit(), targets, scope.stream().map(UnitRow :: id).collect(Collectors.toSet()));
        if (mapper.updateDraft(topicId, input.title(), input.body(), input.note(), input.version()) != 1) {
            throw conflict("DRAFT_VERSION_CHANGED", "草稿已被修改，请刷新后核对");
        }
        mapper.clearScope(topicId);
        mapper.clearTargets(topicId);
        saveChoices(topicId, scope, targets);
        mapper.completeCommand(actor.unitId(), action, key, topicId);
        return detailFor(actor, mapper.topic(topicId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public IntelligenceDetail send(Authentication auth, long topicId, Long receiptId, String key, IntelligenceSendRequest raw) {
        Actor actor = actor(auth, true);
        String op = receiptId == null ? "send" : "forward";
        require(actor, receiptId == null ? PermissionCodes.INTEL_SEND : PermissionCodes.INTEL_FORWARD);
        TopicRow topic = visible(actor, topicId, true);
        FlowState state = flowState(topicId);
        ReceiptRow receipt = receiptId == null ? null : ownReceipt(actor, state, receiptId);
        IntelligenceSendRequest input = new IntelligenceSendRequest(codes(raw.targetUnitCodes()), text(raw.note(), 1000, false), raw.assessmentId(), raw.assessmentSummary()==null ? null : text(raw.assessmentSummary(),4000,true));
        if ((input.assessmentId()==null)!=(input.assessmentSummary()==null)) throw bad("INVALID_ASSESSMENT_SHARE","研判引用和共享摘要须同时填写");
        if(input.assessmentId()!=null) taskAccess.requireAssessment(auth,topicId,parseId(input.assessmentId()));
        String action = op + ":" + topicId + (receiptId == null ? "" : ":" + receiptId);
        Long replay = reserve(actor, action, key, input);
        if (replay != null) return detailFor(actor, visible(actor, replay, true));
        enforce(precondition(actor, topic, receipt, op, state));
        List<UnitRow> targets = resolve(actor, input.targetUnitCodes());
        validateTargets(actor.unit(), targets, state.scopeIds());
        Set<Long> eligible = eligibleTargets(actor, state, receipt).stream().map(UnitRow::id).collect(Collectors.toSet());
        if (targets.stream().anyMatch(target -> !eligible.contains(target.id()))) {
            throw conflict(receipt == null ? "UNIT_ALREADY_RECEIVED" : "PATH_ALREADY_SHARED",
                    receipt == null ? "所选单位已收到该情报，请选择其他单位" : "该次接收已向所选单位共享，请选择其他单位");
        }
        Instant now = Instant.now();
        mapper.insertSend(topicId, receiptId, actor.row(), actor.unit(), input.note(), now);
        long sendId = mapper.lastId();
        if(input.assessmentId()!=null) mapper.attachAssessment(sendId,parseId(input.assessmentId()),input.assessmentSummary());
        for (UnitRow target : targets) mapper.insertReceipt(sendId, target);
        if (topic.status().equals("DRAFT")) {
            mapper.freezeScopeNames(topicId);
            mapper.publish(topicId, now);
        }
        mapper.completeCommand(actor.unitId(), action, key, topicId);
        return detailFor(actor, mapper.topic(topicId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public IntelligenceDetail receiptAction(Authentication auth, long topicId, long receiptId, String op, String key, IntelligenceFeedbackRequest raw) {
        String permission = switch (op) {
            case "sign" -> PermissionCodes.INTEL_SIGN;
            case "feedbacks" -> PermissionCodes.INTEL_FEEDBACK;
            default -> throw bad("INVALID_ACTION", "接收动作无效");
        };
        Actor actor = actor(auth, true);
        require(actor, permission);
        TopicRow topic = visible(actor, topicId, true);
        FlowState state = flowState(topicId);
        ReceiptRow receipt = ownReceipt(actor, state, receiptId);
        String body = op.equals("feedbacks") ? text(raw.body(), 4000, true) : "";
        String action = op + ":" + topicId + ":" + receiptId;
        Long replay = reserve(actor, action, key, body);
        if (replay != null) return detailFor(actor, visible(actor, replay, true));
        enforce(precondition(actor, topic, receipt, op, state));
        Instant now = Instant.now();
        switch (op) {
            case "sign" -> mapper.sign(receiptId, actor.row(), now);
            case "feedbacks" -> mapper.insertFeedback(receiptId, actor.row(), actor.unit(), body, now);
            default -> throw new IllegalStateException();
        }
        mapper.completeCommand(actor.unitId(), action, key, topicId);
        return detailFor(actor, mapper.topic(topicId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public IntelligenceDetail supplement(Authentication auth, long topicId, String key, IntelligenceSupplementRequest raw) {
        Actor actor = actor(auth, true);
        require(actor, PermissionCodes.INTEL_SUPPLEMENT);
        TopicRow topic = visible(actor, topicId, true);
        IntelligenceSupplementRequest input = new IntelligenceSupplementRequest(raw.kind(), text(raw.body(), 4000, true));
        if (!Set.of("SUPPLEMENT", "CORRECTION").contains(input.kind())) throw bad("INVALID_SUPPLEMENT_KIND", "说明类型无效");
        String action = "supplement:" + topicId;
        Long replay = reserve(actor, action, key, input);
        if (replay != null) return detailFor(actor, visible(actor, replay, true));
        enforce(precondition(actor, topic, null, "supplement", flowState(topicId)));
        mapper.insertSupplement(topicId, input.kind(), actor.row(), actor.unit(), input.body(), Instant.now());
        mapper.completeCommand(actor.unitId(), action, key, topicId);
        return detailFor(actor, mapper.topic(topicId));
    }

    private Actor actor(Authentication auth, boolean write) {
        // 与系统授权写入共用锚点；取得它后再校验身份版本，防止并发停用/换单位继续写。
        if (write) authorization.lock();
        guard.require(auth, PermissionCodes.INTEL_READ, "没有信息流转查看权限");
        if (!(auth.getPrincipal() instanceof AuthenticatedAccount account)) throw forbidden();
        List<UnitRow> units = write ? mapper.lockUnits() : mapper.units();
        ActorRow row = mapper.actor(account.userId());
        if (row == null || row.authorizationVersion() != account.authorizationVersion()) throw forbidden();
        UnitRow unit = units.stream().filter(u -> u.id() == row.unitId() && enabled(u))
                .findFirst().orElseThrow(IntelligenceService::forbidden);
        return new Actor(row, unit, units, auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet()));
    }

    private TopicRow visible(Actor actor, long id, boolean lock) {
        TopicRow topic = lock ? mapper.lockTopic(id) : mapper.topic(id);
        if (topic == null || (topic.sourceUnitId() != actor.unitId()
                && (!topic.status().equals("PUBLISHED") || mapper.received(id, actor.unitId()) == 0))) {
            throw notFound();
        }
        return topic;
    }

    private ReceiptRow ownReceipt(Actor actor, FlowState state, long receiptId) {
        return state.receipts().stream()
                .filter(receipt -> receipt.id() == receiptId && receipt.toUnitId() == actor.unitId())
                .findFirst().orElseThrow(IntelligenceService::notFound);
    }

    private IntelligenceDetail detailFor(Actor actor, TopicRow topic) {
        FlowState state = flowState(topic.id());
        List<ReceiptRow> all = state.receipts();
        Map<Long, ReceiptRow> byId = all.stream().collect(Collectors.toMap(ReceiptRow::id, receipt -> receipt));
        boolean source = topic.sourceUnitId() == actor.unitId();
        Set<Long> myAncestors = new HashSet<>();
        for (ReceiptRow receipt : all) {
            if (receipt.toUnitId() == actor.unitId()) {
                ReceiptRow current = receipt;
                while (current != null && myAncestors.add(current.id())) {
                    current = current.parentReceiptId() == null ? null : byId.get(current.parentReceiptId());
                }
            }
        }
        Map<Long, List<FeedbackRow>> feedbacks = mapper.feedbacks(topic.id()).stream()
                .collect(Collectors.groupingBy(FeedbackRow::receiptId));
        // Mapper 按回执 ID 升序返回，父回执先于子回执；链路归属只计算一次。
        Set<Long> upstreamReceipts = new HashSet<>();
        List<IntelligenceReceipt> receipts = new ArrayList<>();
        for (ReceiptRow receipt : all) {
            boolean upstream = receipt.fromUnitId() == actor.unitId() || receipt.toUnitId() == actor.unitId()
                    || upstreamReceipts.contains(receipt.parentReceiptId());
            if (upstream) upstreamReceipts.add(receipt.id());
            if (!source && !upstream && !myAncestors.contains(receipt.id())) continue;
            boolean feedbackVisible = source || upstream;
            List<IntelligenceAction> actions = new ArrayList<>();
            if (receipt.toUnitId() == actor.unitId()) {
                if (receipt.signedAt() == null) action(actions, actor, topic, receipt, "sign", PermissionCodes.INTEL_SIGN, state);
                action(actions, actor, topic, receipt, "feedbacks", PermissionCodes.INTEL_FEEDBACK, state);
                if (receipt.signedAt() == null || hasTarget(actor, state, receipt)) {
                    action(actions, actor, topic, receipt, "forward", PermissionCodes.INTEL_FORWARD, state);
                }
            }
            receipts.add(new IntelligenceReceipt(id(receipt.id()), id(receipt.sendId()),
                    receipt.parentReceiptId() == null ? null : id(receipt.parentReceiptId()),
                    receipt.fromUnitName(), receipt.toUnitName(), receipt.senderName(), receipt.note(), receipt.assessmentSummary(),
                    receipt.sentAt(), receipt.toUnitId() == actor.unitId(), receipt.signedByName(), receipt.signedAt(),
                    feedbackVisible ? feedbacks.getOrDefault(receipt.id(), List.of()).stream()
                            .map(f -> new IntelligenceFeedback(id(f.id()), f.body(), f.unitName(), f.userName(), f.createdAt()))
                            .toList() : List.of(), actions));
        }
        List<IntelligenceAction> actions = new ArrayList<>();
        if (source) {
            if (topic.status().equals("DRAFT")) {
                action(actions, actor, topic, null, "update", PermissionCodes.INTEL_UPDATE, state);
                action(actions, actor, topic, null, "send", PermissionCodes.INTEL_SEND, state);
            } else {
                if (hasTarget(actor, state, null)) action(actions, actor, topic, null, "send", PermissionCodes.INTEL_SEND, state);
                action(actions, actor, topic, null, "supplement", PermissionCodes.INTEL_SUPPLEMENT, state);
            }
        }
        return new IntelligenceDetail(id(topic.id()), topic.topicNo(), topic.title(), topic.body(), topic.status(),
                topic.sourceUnitName(), topic.sourceUserName(), source, topic.version(), topic.createdAt(), topic.publishedAt(),
                source ? state.scope().stream().map(ScopeRow::unitCode).toList() : List.of(),
                source ? mapper.draftTargets(topic.id()).stream().map(ScopeRow::unitCode).toList() : List.of(),
                source ? topic.draftNote() : null, receipts,
                mapper.supplements(topic.id()).stream()
                        .map(s -> new IntelligenceSupplement(id(s.id()), s.kind(), s.body(),
                                s.unitName(), s.userName(), s.createdAt())).toList(), actions);
    }

    private void action(List<IntelligenceAction> out, Actor actor, TopicRow topic, ReceiptRow receipt,
            String code, String permission, FlowState state) {
        if (!actor.can(permission)) return;
        out.add(precondition(actor, topic, receipt, code, state));
    }

    /** 状态原因与写接口共用；目标合法性在候选查询和提交时独立二次校验。 */
    private IntelligenceAction precondition(Actor actor, TopicRow topic, ReceiptRow receipt, String code, FlowState state) {
        String reason = null, reasonCode = null;
        if (Set.of("update", "send", "supplement").contains(code) && topic.sourceUnitId() != actor.unitId()) throw forbidden();
        if (code.equals("update") && !topic.status().equals("DRAFT")) {
            reasonCode = "TOPIC_PUBLISHED";
            reason = "情报已发出，原文不能覆盖，请追加补充或更正";
        } else if (code.equals("supplement") && !topic.status().equals("PUBLISHED")) {
            reasonCode = "TOPIC_DRAFT";
            reason = "草稿尚未发出，请直接修改草稿";
        } else if (receipt != null && code.equals("sign") && receipt.signedAt() != null) {
            reasonCode = "RECEIPT_SIGNED";
            reason = "该次送达已由本单位签收";
        } else if (receipt != null && Set.of("feedbacks", "forward").contains(code) && receipt.signedAt() == null) {
            reasonCode = "SIGN_REQUIRED";
            reason = "请先签收";
        }
        if (reason == null && code.equals("send") && topic.status().equals("DRAFT")) {
            try {
                Set<Long> scope = state.scopeIds();
                List<UnitRow> rows = actor.units().stream().filter(u -> scope.contains(u.id())).toList();
                if (rows.size() != scope.size() || rows.stream().anyMatch(u -> !enabled(u))) {
                    throw conflict("SCOPE_UNIT_DISABLED", "传播范围中有不存在或停用的单位，请修改草稿");
                }
                reachable(actor.unit(), rows);
            } catch (ApiException problem) {
                reasonCode = problem.code();
                reason = problem.getMessage();
            }
        }
        if (reason == null && Set.of("send", "forward").contains(code) && !hasTarget(actor, state, receipt)) {
            reasonCode = "NO_TARGET_UNIT";
            reason = "没有可共享的其他单位";
        }
        return new IntelligenceAction(code, reason == null, reasonCode, reason);
    }

    /** 一次加载当前事实；目标查询、动作提示和详情共用索引，避免逐回执重读重扫。 */
    private record FlowState(List<ReceiptRow> receipts, List<ScopeRow> scope, Set<Long> scopeIds,
            Set<Long> deliveredTo, Map<Long, Set<Long>> forwardedTo) { }

    private FlowState flowState(long topicId) {
        List<ReceiptRow> receipts = mapper.receipts(topicId);
        List<ScopeRow> scope = mapper.scope(topicId);
        Set<Long> delivered = new HashSet<>();
        Map<Long, Set<Long>> forwarded = new HashMap<>();
        for (ReceiptRow receipt : receipts) {
            delivered.add(receipt.toUnitId());
            if (receipt.parentReceiptId() != null) {
                forwarded.computeIfAbsent(receipt.parentReceiptId(), ignored -> new HashSet<>())
                        .add(receipt.toUnitId());
            }
        }
        return new FlowState(receipts, scope, scope.stream().map(ScopeRow::unitId).collect(Collectors.toSet()),
                delivered, forwarded);
    }

    /** 源头仅面向未收到的单位；同一父回执到同一目标最多共享一次。 */
    private List<UnitRow> eligibleTargets(Actor actor, FlowState state, ReceiptRow receipt) {
        Set<Long> delivered = receipt == null ? state.deliveredTo()
                : state.forwardedTo().getOrDefault(receipt.id(), Set.of());
        return actor.units().stream()
                .filter(unit -> enabled(unit) && state.scopeIds().contains(unit.id()) && legal(actor.unit(), unit)
                        && !delivered.contains(unit.id())).toList();
    }

    private boolean hasTarget(Actor actor, FlowState state, ReceiptRow receipt) {
        return !eligibleTargets(actor, state, receipt).isEmpty();
    }

    private static void enforce(IntelligenceAction state) {
        if (!state.enabled()) throw conflict(state.reasonCode(), state.reason());
    }

    private static boolean enabled(UnitRow u) {
        return "ENABLED".equals(u.status());
    }

    static boolean legal(UnitRow from, UnitRow to) {
        if (from.id() == to.id()) return false;
        return (Objects.equals(to.parentId(), from.id()) && to.level() == from.level() + 1)
                || (Objects.equals(from.parentId(), to.id()) && from.level() == to.level() + 1)
                || (from.level() == 2 && to.level() == 2 && from.parentId() != null
                        && Objects.equals(from.parentId(), to.parentId()));
    }

    private static void reachable(UnitRow source, List<UnitRow> scope) {
        Set<Long> reached = new HashSet<>();
        reached.add(source.id());
        List<UnitRow> reachedRows = new ArrayList<>();
        reachedRows.add(source);
        for (int index = 0; index < reachedRows.size(); index++) {
            for (UnitRow u : scope) {
                if (enabled(u) && !reached.contains(u.id()) && legal(reachedRows.get(index), u)) {
                    reached.add(u.id());
                    reachedRows.add(u);
                }
            }
        }
        List<String> missing = scope.stream().filter(u -> !reached.contains(u.id())).map(UnitRow::name).toList();
        if (!missing.isEmpty()) {
            throw bad("MISSING_TRANSIT_SCOPE", "以下单位无法在传播范围内到达，请显式补齐中转支队等必经单位：" + String.join("、", missing));
        }
    }

    private static void validateTargets(UnitRow from, List<UnitRow> targets, Set<Long> scope) {
        for (UnitRow u : targets) {
            if (!scope.contains(u.id())) throw bad("OUTSIDE_SCOPE", "接收单位超出允许传播范围");
            if (!legal(from, u)) throw bad("ILLEGAL_FLOW_RELATION", "大队仅可向直属支队上报；其他单位仅可向直属上下级或同总队支队共享");
        }
    }

    private List<UnitRow> resolve(Actor actor, List<String> codes) {
        Map<String, UnitRow> units = actor.units().stream().collect(Collectors.toMap(UnitRow::code, u -> u));
        return codes.stream().map(c -> {
            UnitRow u = units.get(c);
            if (u == null || !enabled(u)) throw bad("INVALID_UNIT", "所选单位不存在或已停用");
            return u;
        }).toList();
    }

    private void saveChoices(long id, List<UnitRow> scope, List<UnitRow> targets) {
        for (UnitRow u : scope) mapper.insertScope(id, u);
        for (UnitRow u : targets) mapper.insertDraftTarget(id, u);
    }

    private static List<String> codes(List<String> values) {
        if (values == null || values.isEmpty() || values.size() > 2000) throw bad("INVALID_UNITS", "需要选择单位");
        return values.stream().map(v -> text(v, 64, true)).distinct().sorted().toList();
    }

    private static IntelligenceDraftRequest draft(IntelligenceDraftRequest request) {
        if (request.version() == null || request.version() < 0) throw bad("INVALID_VERSION", "草稿版本无效");
        return new IntelligenceDraftRequest(text(request.title(), 160, true), text(request.body(), 4000, true),
                codes(request.scopeUnitCodes()), codes(request.targetUnitCodes()),
                text(request.note(), 1000, false), request.version());
    }

    private static String text(String raw, int max, boolean required) {
        String value = raw == null ? "" : raw.strip();
        if ((required && value.isEmpty()) || value.length() > max) throw bad("INVALID_CONTENT", "内容不能为空且须符合长度限制");
        return value;
    }

    private Long reserve(Actor actor, String action, String key, Object command) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,80}")) throw bad("INVALID_IDEMPOTENCY_KEY", "请求键需为 8–80 位字母、数字或 . _ : -");
        String digest;
        try {
            digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(command)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        if (mapper.reserveCommand(actor.unitId(), action, key, digest, Instant.now()) == 1) return null;
        CommandRow existing = mapper.command(actor.unitId(), action, key);
        if (existing == null || existing.topicId() == null) throw conflict("IDEMPOTENCY_PENDING", "相同请求正在处理，请稍后重试");
        if (!existing.requestDigest().equals(digest)) throw conflict("IDEMPOTENCY_CONFLICT", "同一请求键已用于不同内容");
        visible(actor, existing.topicId(), true);
        return existing.topicId();
    }

    private static void require(Actor actor, String permission) {
        if (!actor.can(permission)) throw forbidden();
    }

    public static long parseId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException e) {
            throw bad("INVALID_ID", "编号无效");
        }
    }

    private static String id(long value) {
        return Long.toString(value);
    }

    private static ApiException bad(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "没有该信息流转操作权限或身份已变化");
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "TOPIC_NOT_FOUND", "情报不存在或不可见");
    }
}
