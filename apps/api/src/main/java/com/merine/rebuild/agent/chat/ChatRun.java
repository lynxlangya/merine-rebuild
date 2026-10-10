package com.merine.rebuild.agent.chat;

import com.merine.rebuild.agent.chat.dto.ChatRunView;
import com.merine.rebuild.agent.chat.dto.MessagePart;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一次本地演示执行的内存状态：终态、已定稿部件、待答问题与已确认的合成条件。
 *
 * <p>只存在于进程内：没有持久化，重启即丢失；记录上限与 TTL 由
 * {@link ChatRunRegistry} 维护。所有变更方法同步，读写都很短；快照输出只读。
 */
final class ChatRun {

    enum AnswerResult { ACCEPTED, ALREADY_ANSWERED, MISMATCH }

    record PartState(String partId, String partType, MessagePart part,
                     ChatRunView.MessagePartStatus status) {}

    static final class PendingQuestion {
        private final MessagePart.Question question;
        private volatile boolean answered;
        private volatile List<String> values = List.of();
        private volatile String freeText;

        PendingQuestion(MessagePart.Question question) {
            this.question = question;
        }

        MessagePart.Question question() {
            return question;
        }

        boolean answered() {
            return answered;
        }

        List<String> values() {
            return values;
        }

        String freeText() {
            return freeText;
        }
    }

    private final long userId;
    private final String idempotencyKey;
    private final String digest;
    private final String messageId;
    private final String generationId;
    private final String clientConversationId;
    private final Instant startedAt = Instant.now();
    private final List<PartState> parts = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, String> conditions = Collections.synchronizedMap(new LinkedHashMap<>());

    private volatile ChatRunView.ChatRunState state = ChatRunView.ChatRunState.RUNNING;
    private volatile Instant finishedAt;
    /** 落库用：所属会话、失败原因与上游用量；都在收尾时读取。 */
    private volatile String conversationId = "";
    private volatile String errorCode = "";
    private volatile String errorMessage = "";
    private volatile Integer promptTokens;
    private volatile Integer completionTokens;
    private volatile boolean cancelRequested;
    private volatile PendingQuestion pendingQuestion;
    /** 活动名额只释放一次：写入线程先落终态时，收尾方也要能释放。 */
    private final AtomicBoolean slotHeld = new AtomicBoolean(true);

    ChatRun(long userId, String idempotencyKey, String digest, String messageId, String generationId,
            String clientConversationId) {
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        this.digest = digest;
        this.messageId = messageId;
        this.generationId = generationId;
        this.clientConversationId = clientConversationId;
    }

    long userId() {
        return userId;
    }

    String idempotencyKey() {
        return idempotencyKey;
    }

    String digest() {
        return digest;
    }

    String messageId() {
        return messageId;
    }

    String generationId() {
        return generationId;
    }

    String clientConversationId() {
        return clientConversationId;
    }

    String conversationId() {
        return conversationId;
    }

    void attachConversation(String id) {
        this.conversationId = id;
    }

    String errorCode() {
        return errorCode;
    }

    String errorMessage() {
        return errorMessage;
    }

    /** 记录失败原因：只保留最后一次，用于执行记录与排查。 */
    void markFailure(String code, String message) {
        if (this.errorCode.isEmpty()) {
            this.errorCode = code == null ? "" : code;
            this.errorMessage = message == null ? "" : message;
        }
    }

    Integer promptTokens() {
        return promptTokens;
    }

    Integer completionTokens() {
        return completionTokens;
    }

    void recordUsage(Integer prompt, Integer completion) {
        this.promptTokens = prompt;
        this.completionTokens = completion;
    }

    /** 已定稿的纯文本（按部件顺序拼接）：真实模式只会有 TEXT 部件，落库用。 */
    synchronized String plainText() {
        StringBuilder text = new StringBuilder();
        for (PartState part : parts) {
            if (part.part() instanceof MessagePart.Text current) {
                text.append(current.text());
            }
        }
        return text.toString();
    }

    Instant startedAt() {
        return startedAt;
    }

    Instant finishedAt() {
        return finishedAt;
    }

    ChatRunView.ChatRunState state() {
        return state;
    }

    boolean cancelRequested() {
        return cancelRequested;
    }

    void requestCancel() {
        cancelRequested = true;
    }

    int partCount() {
        return parts.size();
    }

    /** 已累积的文本长度：用于输出上限判定，不依赖具体部件顺序。 */
    int textChars() {
        return parts.stream()
                .filter(part -> part.part() instanceof MessagePart.Text)
                .mapToInt(part -> ((MessagePart.Text) part.part()).text().length())
                .sum();
    }

    synchronized boolean startPart(String partId, String partType) {
        if (parts.stream().anyMatch(part -> part.partId().equals(partId))) {
            return false;
        }
        MessagePart initial = "TEXT".equals(partType) ? new MessagePart.Text("") : null;
        parts.add(new PartState(partId, partType, initial, ChatRunView.MessagePartStatus.STREAMING));
        return true;
    }

    synchronized boolean appendText(String partId, String delta) {
        for (int index = 0; index < parts.size(); index++) {
            PartState current = parts.get(index);
            if (current.partId().equals(partId) && current.part() instanceof MessagePart.Text text) {
                parts.set(index, new PartState(partId, current.partType(),
                        new MessagePart.Text(text.text() + delta), current.status()));
                return true;
            }
        }
        return false;
    }

    synchronized void snapshotPart(String partId, MessagePart part) {
        for (int index = 0; index < parts.size(); index++) {
            if (parts.get(index).partId().equals(partId)) {
                parts.set(index, new PartState(partId, parts.get(index).partType(), part,
                        ChatRunView.MessagePartStatus.STREAMING));
                return;
            }
        }
    }

    synchronized void donePart(String partId) {
        for (int index = 0; index < parts.size(); index++) {
            if (parts.get(index).partId().equals(partId)) {
                PartState current = parts.get(index);
                parts.set(index, new PartState(partId, current.partType(), current.part(),
                        ChatRunView.MessagePartStatus.DONE));
                return;
            }
        }
    }

    /** 幂等：只有 RUNNING 才能落到终态；重复调用不改写已有结果。返回是否仍持有活动名额。 */
    synchronized boolean finish(ChatRunView.ChatRunState next) {
        if (state != ChatRunView.ChatRunState.RUNNING) {
            return false;
        }
        ChatRunView.MessagePartStatus partStatus = switch (next) {
            case SUCCEEDED, AWAITING_INPUT -> ChatRunView.MessagePartStatus.DONE;
            case ABORTED -> ChatRunView.MessagePartStatus.ABORTED;
            case FAILED -> ChatRunView.MessagePartStatus.FAILED;
            case RUNNING -> ChatRunView.MessagePartStatus.STREAMING;
        };
        for (int index = 0; index < parts.size(); index++) {
            PartState current = parts.get(index);
            if (current.status() == ChatRunView.MessagePartStatus.STREAMING) {
                parts.set(index, new PartState(current.partId(), current.partType(), current.part(),
                        partStatus));
            }
        }
        state = next;
        finishedAt = Instant.now();
        return true;
    }

    /** 释放活动名额；无论谁先落终态，这个 CAS 只会成功一次。 */
    boolean releaseSlot() {
        return slotHeld.compareAndSet(true, false);
    }

    synchronized void setPendingQuestion(MessagePart.Question question) {
        pendingQuestion = new PendingQuestion(question);
    }

    PendingQuestion pendingQuestion() {
        return pendingQuestion;
    }

    /** 原子受理一次作答；answered 置位后换任何幂等键都不能再消费。 */
    synchronized AnswerResult answerQuestion(String questionId, List<String> values, String freeText) {
        PendingQuestion pending = pendingQuestion;
        if (pending == null || !pending.question().questionId().equals(questionId)) {
            return AnswerResult.MISMATCH;
        }
        if (pending.answered) {
            return AnswerResult.ALREADY_ANSWERED;
        }
        pending.answered = true;
        pending.values = List.copyOf(values);
        pending.freeText = freeText;
        // 条件摘要用选项标签而不是原始取值：演示回答要能显示“立案案件”而不是 “CASE”。
        conditions.put(questionId, conditionText(labelsOf(pending.question(), values), freeText));
        return AnswerResult.ACCEPTED;
    }

    private static List<String> labelsOf(MessagePart.Question question, List<String> values) {
        if (question.options() == null) {
            return values;
        }
        return values.stream().map(value -> question.options().stream()
                .filter(option -> value.equals(option.value()))
                .map(option -> option.label() == null || option.label().isBlank()
                        ? option.value() : option.label())
                .findFirst().orElse(value)).toList();
    }

    synchronized void inheritConditions(Map<String, String> previous) {
        conditions.putAll(previous);
    }

    Map<String, String> conditions() {
        return Map.copyOf(conditions);
    }

    static String conditionText(List<String> values, String freeText) {
        String joined = String.join("、", values);
        if (freeText == null || freeText.isBlank()) {
            return joined;
        }
        return joined.isBlank() ? freeText : joined + "（" + freeText + "）";
    }

    ChatRunView view() {
        List<ChatRunView.ChatPartView> visible = parts.stream()
                .filter(part -> part.part() != null)
                .map(part -> new ChatRunView.ChatPartView(part.partId(), part.part(), part.status()))
                .toList();
        PendingQuestion pending = pendingQuestion;
        ChatRunView.QuestionAnswerState question = pending == null ? null
                : new ChatRunView.QuestionAnswerState(pending.question().questionId(),
                        pending.answered(), pending.values(), pending.freeText());
        return new ChatRunView(idempotencyKey, generationId, clientConversationId, state,
                cancelRequested, visible, question, startedAt, finishedAt);
    }
}
