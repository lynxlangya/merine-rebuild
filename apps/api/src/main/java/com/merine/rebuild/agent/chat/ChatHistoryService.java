package com.merine.rebuild.agent.chat;

import com.merine.rebuild.agent.chat.dto.ChatRunRecordView;
import com.merine.rebuild.agent.chat.dto.ChatRunStats;
import com.merine.rebuild.agent.chat.dto.ChatRunView;
import com.merine.rebuild.agent.chat.dto.ConversationMessageView;
import com.merine.rebuild.agent.chat.dto.ConversationView;
import com.merine.rebuild.agent.provider.dto.ProviderRequests.ReasoningEffort;
import com.merine.rebuild.agent.chat.persistence.ChatHistoryMapper;
import com.merine.rebuild.agent.chat.persistence.ChatHistoryMapper.MessageRow;
import com.merine.rebuild.agent.chat.persistence.ChatRunRow;
import com.merine.rebuild.agent.chat.persistence.ConversationRow;
import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.common.PageResult;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会话与执行记录的读写。
 *
 * <p>可见性只有一条规则：会话归属用户本人。所有按 id 的读写都带 `user_id` 条件，
 * 不属于本人时统一返回 404（不暴露「这个会话存在但不属于你」）。
 * 写入在流结束后进行一次：提问 + 回答 + 执行记录在同一事务里，靠唯一键兜住重复落库。
 */
@Service
public class ChatHistoryService {
    private final ChatHistoryMapper mapper;

    ChatHistoryService(ChatHistoryMapper mapper) {
        this.mapper = mapper;
    }

    /** 记录一轮执行时要落库的目标快照：连接可能被改名或删除，这里留快照便于追溯。 */
    record TargetSnapshot(String providerId, String providerName, String modelId,
                          ReasoningEffort reasoningEffort) {}

    /**
     * 取得本轮要写入的会话：传了 id 就校验归属（不存在或不属于本人 → 404），
     * 没传就按首条提问建一个新会话。标题取首条提问的前缀，空白提问回退为「新会话」。
     */
    @Transactional
    public String ensureConversation(long userId, String requestedId, String firstUserText,
                                     TargetSnapshot target) {
        if (requestedId != null && !requestedId.isBlank()) {
            ConversationRow existing = mapper.findConversation(requestedId.trim());
            if (existing == null || existing.userId() != userId) throw notFound();
            return existing.id();
        }
        String id = UUID.randomUUID().toString();
        TargetSnapshot snapshot = target == null
                ? new TargetSnapshot("", "", "", null)
                : target;
        mapper.insertConversation(id, userId, titleOf(firstUserText),
                blankToEmpty(snapshot.providerId()), blankToEmpty(snapshot.providerName()),
                blankToEmpty(snapshot.modelId()), effortCode(snapshot.reasoningEffort()));
        return id;
    }

    /**
     * 落一轮执行：用户消息、助手消息与执行记录。
     *
     * <p>先锁定会话行：与「删除会话」互斥，并在此之后复核会话仍存在且属于该用户——
     * 否则回答可能写进已经删除的会话（悬空引用）。会话已不存在时返回 false，由调用方记日志。
     * 序号也在同一把锁内取，两个标签页同时收尾不会拿到同一个 seq。
     * 重复落库（同一幂等键）由 `(user_id, idempotency_key)` 与消息唯一键挡住。
     */
    @Transactional
    public boolean recordTurn(long userId, String conversationId, String mode, TargetSnapshot target,
                              ChatRun run, String userText, String assistantText, int inputChars) {
        ConversationRow locked = mapper.lockConversation(conversationId);
        if (locked == null || locked.userId() != userId) return false;
        int seq = mapper.nextSeq(conversationId);
        String runId = UUID.randomUUID().toString();
        mapper.insertMessage(UUID.randomUUID().toString(), conversationId, runId, seq,
                "USER", userText, "SUCCEEDED");
        mapper.insertMessage(run.messageId(), conversationId, runId, seq + 1,
                "ASSISTANT", assistantText, run.state().name());
        Instant finishedAt = run.finishedAt() == null ? Instant.now() : run.finishedAt();
        int durationMs = (int) Math.min(Integer.MAX_VALUE,
                Duration.between(run.startedAt(), finishedAt).toMillis());
        mapper.insertRun(new ChatRunRow(runId, userId, conversationId,
                run.idempotencyKey(), run.messageId(), blankToEmpty(run.generationId()), mode,
                blankToEmpty(target.providerId()), blankToEmpty(target.providerName()),
                blankToEmpty(target.modelId()), effortCode(target.reasoningEffort()),
                run.state().name(), blankToEmpty(run.errorCode()), truncate(run.errorMessage(), 300),
                run.startedAt(), finishedAt, durationMs, inputChars, assistantText.length(),
                run.promptTokens(), run.completionTokens()));
        mapper.touchConversation(conversationId, blankToEmpty(target.providerId()),
                blankToEmpty(target.providerName()), blankToEmpty(target.modelId()),
                effortCode(target.reasoningEffort()), 2);
        return true;
    }

    @Transactional(readOnly = true)
    public PageResult<ConversationView> list(long userId, int page, int pageSize) {
        List<ConversationView> items = mapper
                .listConversations(userId, pageSize, (long) (page - 1) * pageSize).stream()
                .map(ChatHistoryService::toView)
                .toList();
        return new PageResult<>(items, mapper.countConversations(userId), page, pageSize);
    }

    @Transactional(readOnly = true)
    public ConversationView find(long userId, String id) {
        ConversationRow row = mapper.findConversation(id);
        if (row == null || row.userId() != userId) throw notFound();
        return toView(row);
    }

    @Transactional(readOnly = true)
    public List<ConversationMessageView> messages(long userId, String id) {
        find(userId, id);
        return mapper.listMessages(id).stream()
                .map(row -> new ConversationMessageView(row.id(), stateOf(row.status()),
                        row.role(), row.text(), row.createdAt()))
                .toList();
    }

    @Transactional
    public ConversationView rename(long userId, String id, String title, int version) {
        ConversationRow row = mapper.findConversation(id);
        if (row == null || row.userId() != userId) throw notFound();
        if (mapper.renameConversation(id, userId, title.trim(), version) != 1) throw conflict();
        return find(userId, id);
    }

    @Transactional
    public void delete(long userId, String id, int version) {
        // 先锁会话行，再删子表：与「落一轮」互斥，避免删完又被写入。
        ConversationRow row = mapper.lockConversation(id);
        if (row == null || row.userId() != userId) throw notFound();
        mapper.deleteMessagesOfConversation(id);
        mapper.deleteRunsOfConversation(id);
        if (mapper.deleteConversation(id, userId, version) != 1) throw conflict();
    }

    /**
     * 执行记录分页；筛选维度与聚合完全一致（时间范围 + 状态 + 连接 + 模型）。
     * `days` 为空表示不限时间，便于其它调用方复用同一个接口。
     */
    @Transactional(readOnly = true)
    public PageResult<ChatRunRecordView> runs(long userId, String state, String providerId,
                                             String modelId, Integer days, int page, int pageSize) {
        String provider = blankToEmpty(providerId);
        String model = blankToEmpty(modelId);
        Instant since = days == null ? null : Instant.now().minus(Duration.ofDays(days));
        List<ChatRunRecordView> items = mapper
                .listRuns(userId, state, provider, model, since, pageSize, (long) (page - 1) * pageSize)
                .stream()
                .map(ChatHistoryService::toRunView)
                .toList();
        return new PageResult<>(items, mapper.countRuns(userId, state, provider, model, since), page,
                pageSize);
    }

    /**
     * 用量聚合：时间范围按「近 N 天」折算，分桶按客户端本地偏移换算成本地日期。
     * 偏移只用于分桶与展示，库里始终是 UTC；范围起点用 UTC 当前时间减 N 天，边界落在那一天起始附近。
     */
    @Transactional(readOnly = true)
    public ChatRunStats stats(long userId, int days, String providerId, String modelId,
                              int offsetMinutes) {
        if (offsetMinutes < -720 || offsetMinutes > 840) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
                    "时区偏移超出允许范围（-720…840 分钟）");
        }
        Instant since = Instant.now().minus(Duration.ofDays(days));
        String offset = offsetString(offsetMinutes);
        String provider = blankToEmpty(providerId);
        String model = blankToEmpty(modelId);
        ChatHistoryMapper.ChatRunStatsRow totals = mapper.totals(userId, since, offset, provider, model);
        List<ChatRunStats.DailyPoint> series = mapper.daily(userId, since, offset, provider, model).stream()
                .map(row -> new ChatRunStats.DailyPoint(row.date(), row.runs(), row.failed(),
                        row.promptTokens(), row.completionTokens(), row.inputChars(),
                        row.outputChars(), row.durationMs()))
                .toList();
        List<ChatRunStats.ModelSlice> models = mapper.byModel(userId, since, provider, model, 8).stream()
                .map(row -> new ChatRunStats.ModelSlice(row.providerId(), row.providerName(),
                        row.modelId(), row.runs(), row.failed(), row.promptTokens(),
                        row.completionTokens(), row.durationMs()))
                .toList();
        return new ChatRunStats(new ChatRunStats.Totals(totals.runs(), totals.succeeded(),
                totals.failed(), totals.aborted(), totals.runsWithoutUsage(), totals.promptTokens(),
                totals.completionTokens(), totals.inputChars(), totals.outputChars(),
                totals.durationMs()), series, models, offsetMinutes);
    }

    /** 分钟偏移转 MySQL 能接受的偏移串，例如 +08:00 / -05:30 / +00:00。 */
    private static String offsetString(int offsetMinutes) {
        String sign = offsetMinutes < 0 ? "-" : "+";
        int absolute = Math.abs(offsetMinutes);
        return String.format("%s%02d:%02d", sign, absolute / 60, absolute % 60);
    }

    private static ConversationView toView(ConversationRow row) {
        return new ConversationView(row.id(), row.title(), row.lastProviderId(),
                row.lastProviderName(), row.lastModelId(),
                effortOf(row.lastReasoningEffort()), row.messageCount(), row.version(),
                row.createdAt(), row.updatedAt());
    }

    private static ChatRunRecordView toRunView(ChatRunRow row) {
        return new ChatRunRecordView(row.id(), row.conversationId(), row.providerName(), row.modelId(),
                stateOf(row.state()), row.errorCode(), row.errorMessage(), row.startedAt(),
                row.finishedAt(), row.durationMs(), row.inputChars(), row.outputChars(),
                row.promptTokens(), row.completionTokens());
    }

    /** 标题取首条提问的可见前缀；换行与空白折叠，避免列表里出现多行标题。 */
    private static String titleOf(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) return "新会话";
        return truncate(normalized, 40);
    }

    private static ChatRunView.ChatRunState stateOf(String value) {
        return value == null || value.isEmpty()
                ? ChatRunView.ChatRunState.SUCCEEDED
                : ChatRunView.ChatRunState.valueOf(value);
    }

    private static ReasoningEffort effortOf(String value) {
        return value == null || value.isEmpty() ? null : ReasoningEffort.valueOf(value);
    }

    private static String effortCode(ReasoningEffort effort) {
        return effort == null ? "" : effort.name();
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /** 截断到上限，且不把代理对（emoji 等）劈成半个字符。 */
    private static String truncate(String value, int max) {
        if (value == null) return "";
        if (value.length() <= max) return value;
        int end = max;
        if (Character.isHighSurrogate(value.charAt(end - 1))) end -= 1;
        return value.substring(0, end);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "CHAT_CONVERSATION_NOT_FOUND", "会话不存在");
    }

    private static ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT, "CHAT_CONVERSATION_CONFLICT",
                "会话已被其他操作修改，请刷新后重试");
    }
}
