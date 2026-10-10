package com.merine.rebuild.agent.chat;

import com.merine.rebuild.agent.chat.dto.ChatEvent;
import com.merine.rebuild.agent.chat.dto.ChatErrorCode;
import com.merine.rebuild.agent.chat.dto.ChatRequest;
import com.merine.rebuild.agent.chat.dto.ChatRequest.ChatAnswer;
import com.merine.rebuild.agent.chat.dto.ChatRunView;
import com.merine.rebuild.agent.chat.dto.MessagePart;
import com.merine.rebuild.agent.provider.ProviderTarget;
import com.merine.rebuild.agent.provider.ProviderTargetLookup;
import com.merine.rebuild.auth.AuthenticatedAccount;
import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.common.CommandDigest;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/**
 * 对话编排：校验 → 准入 → 追问消费 → 确定会话 → 流式执行 → 收尾落库并释放名额。
 *
 * <p>运行中的状态仍在 {@link ChatRunRegistry}（有界内存、重启即丢并如实返回未知）；
 * 一轮结束后由 {@link ChatHistoryService} 落库：提问、回答与执行记录在同一事务写入。
 * 落库失败只记日志，不影响已经结束的响应。
 */
@Service
final class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatRunRegistry registry;
    private final ChatHistoryService history;
    private final ChatProperties props;
    private final ScriptedChatStub stub;
    private final ProviderChatClient client;
    private final ProviderTargetLookup providers;
    private final ObjectMapper json;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService executor;

    ChatService(ChatRunRegistry registry,
                ChatHistoryService history,
                ChatProperties props,
                ObjectProvider<ScriptedChatStub> stubProvider,
                ProviderChatClient client,
                ProviderTargetLookup providers,
                ObjectMapper json,
                @Qualifier("chatRunScheduler") ScheduledExecutorService scheduler,
                @Qualifier("chatRunExecutor") ExecutorService executor) {
        this.registry = registry;
        this.history = history;
        this.props = props;
        this.stub = stubProvider.getIfAvailable();
        this.client = client;
        this.providers = providers;
        this.json = json;
        this.scheduler = scheduler;
        this.executor = executor;
        if (props.mode() == ChatProperties.ChatMode.DEMO && this.stub == null) {
            throw new IllegalStateException(
                    "演示模式需要 dev/test 环境并开启 merine.agent.chat.demo-enabled");
        }
    }

    ChatStart start(ChatRequest request, AuthenticatedAccount account) {
        validateRequest(request);
        ProviderTarget target = props.mode() == ChatProperties.ChatMode.PROVIDER
                ? resolveTarget(request)
                : null;
        String digest = digestOf(request);
        log.info("Chat run requested: userId={} mode={} providerId={} modelId={} reasoningEffort={}",
                account.userId(), props.mode(), target == null ? null : target.providerId(),
                target == null ? null : target.modelId(),
                target == null ? null : target.reasoningEffort());
        boolean answering = request.generationId() != null;
        ChatRun previous = null;
        ChatAnswer answer = null;
        if (answering) {
            previous = registry.findOwnedGeneration(account.userId(), request.generationId());
            if (previous == null) {
                throw new ApiException(HttpStatus.CONFLICT, "CHAT_QUESTION_EXPIRED",
                        "原执行不存在或已过期，请重新提问");
            }
            if (previous.state() != ChatRunView.ChatRunState.AWAITING_INPUT) {
                throw new ApiException(HttpStatus.CONFLICT, "CHAT_QUESTION_NOT_PENDING",
                        "原执行不在等待作答状态");
            }
            answer = validateAnswers(previous, request);
        }
        ChatRunRegistry.Admission admission = registry.admit(account.userId(), request, digest);
        if (!admission.created()) {
            return new ChatStart.Snapshot(admission.run().view());
        }
        ChatRun run = admission.run();
        if (answering) {
            try {
                consumeAnswer(previous, run, answer);
            } catch (ApiException error) {
                // 作答未受理：不留下一条失败记录，让用户能用原键重试。
                registry.discard(run);
                throw error;
            }
        }
        // 会话只在新建执行时确定，且放在作答受理之后：被拒绝的作答不会留下空会话。
        // 同幂等键重放已经在上面直接返回快照，不会再建会话。
        ChatHistoryService.TargetSnapshot snapshot = snapshotOf(target);
        run.attachConversation(history.ensureConversation(account.userId(),
                request.conversationId(), lastUserText(request), snapshot));
        SseEmitter emitter = new SseEmitter(props.runTimeout().toMillis() + 10_000L);
        ChatStreamWriter writer = new ChatStreamWriter(run, emitter, json, props);
        try {
            executor.execute(() -> stream(run, writer, request, answering, target));
        } catch (RuntimeException error) {
            registry.discard(run);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_EXECUTOR_UNAVAILABLE",
                    "演示执行器不可用，请稍后重试");
        }
        return new ChatStart.Stream(emitter);
    }

    /**
     * 删除会话前的守卫：该会话还有未结束的轮次（正在生成或等待作答）时拒绝，
     * 避免后续回答写进已经删除的会话。
     */
    void requireNoActiveRun(long userId, String conversationId) {
        if (registry.hasUnfinishedRunInConversation(userId, conversationId)) {
            throw new ApiException(HttpStatus.CONFLICT, "CHAT_CONVERSATION_BUSY",
                    "该会话还有正在生成的回答，请停止或等待结束后再删除");
        }
    }

    ChatRun findOwned(long userId, String idempotencyKey) {
        return registry.findOwned(userId, CommandDigest.key(idempotencyKey));
    }

    /** 取可调用的连接与密钥；连接/模型未启用或密钥不可用时在开流前失败。 */
    private ProviderTarget resolveTarget(ChatRequest request) {
        if (request.providerId() == null || request.providerId().isBlank()
                || request.modelId() == null || request.modelId().isBlank()) {
            throw invalid("modelId", "请先选择供应商配置与模型");
        }
        return providers.requireEnabled(request.providerId().trim(), request.modelId().trim(),
                request.reasoningEffort());
    }

    private void stream(ChatRun run, ChatStreamWriter writer, ChatRequest request, boolean answering,
                        ProviderTarget target) {
        long heartbeatMillis = Math.max(1_000L, props.heartbeatInterval().toMillis());
        ScheduledFuture<?> heartbeat = scheduler.scheduleAtFixedRate(writer::heartbeat,
                heartbeatMillis, heartbeatMillis, TimeUnit.MILLISECONDS);
        try {
            if (!writer.streamStart()) {
                writer.markAborted();
                return;
            }
            ChatPacing pacing = new ChatPacing(run, writer, Instant.now().plus(props.runTimeout()));
            try {
                if (props.mode() == ChatProperties.ChatMode.DEMO) {
                    stub.run(writer, pacing, request, answering);
                } else {
                    client.stream(writer, pacing, request, target);
                }
            } catch (ChatUpstreamException upstream) {
                // 上游已明确失败：不写成功终态，只发 ERROR 事件。
                writer.fail(upstream.code(), upstream.getMessage(), upstream.retryable());
                return;
            }
            writer.messageDone(ChatEvent.MessageStatus.SUCCEEDED);
        } catch (ChatPacing.Stopped stopped) {
            switch (stopped.reason()) {
                case CANCELLED -> {
                    writer.cancelRequested();
                    writer.messageDone(ChatEvent.MessageStatus.ABORTED);
                }
                case DISCONNECTED -> writer.markAborted();
                case TIMEOUT -> writer.fail(ChatErrorCode.MODEL_TIMEOUT,
                        "本地演示执行超过时限", true);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            writer.markAborted();
        } catch (RuntimeException error) {
            writer.fail(ChatErrorCode.INTERNAL, "本地演示执行中断", true);
        } finally {
            heartbeat.cancel(false);
            writer.completeQuietly();
            registry.finish(run, writer.runState());
            persistTurn(run, request, target);
        }
    }

    /**
     * 落一轮执行：失败只记日志，不影响已经结束的响应——会话缺失时用户可以继续提问，
     * 但这时历史里会少一轮，日志里能查到原因。
     */
    private void persistTurn(ChatRun run, ChatRequest request, ProviderTarget target) {
        try {
            boolean stored = history.recordTurn(run.userId(), run.conversationId(),
                    props.mode() == ChatProperties.ChatMode.DEMO ? "DEMO" : "PROVIDER",
                    snapshotOf(target), run, lastUserText(request), run.plainText(),
                    inputChars(request));
            if (!stored) {
                log.warn("Chat turn skipped: conversation missing userId={} conversationId={}",
                        run.userId(), run.conversationId());
            }
        } catch (RuntimeException error) {
            log.error("Chat turn persist failed: userId={} conversationId={} type={}",
                    run.userId(), run.conversationId(), error.getClass().getName());
        }
    }

    private static ChatHistoryService.TargetSnapshot snapshotOf(ProviderTarget target) {
        return target == null
                ? new ChatHistoryService.TargetSnapshot("", "", "", null)
                : new ChatHistoryService.TargetSnapshot(target.providerId(), target.providerName(),
                        target.modelId(), target.reasoningEffort());
    }

    /** 本轮提问：客户端把历史与最新一问一起发来，取最后一条用户消息。 */
    private static String lastUserText(ChatRequest request) {
        List<ChatRequest.ChatTurn> turns = request.messages();
        for (int index = turns.size() - 1; index >= 0; index--) {
            if (turns.get(index).role() == ChatRequest.ChatRole.USER) {
                return turns.get(index).text();
            }
        }
        return "";
    }

    private static int inputChars(ChatRequest request) {
        return request.messages().stream().mapToInt(turn -> turn.text().length()).sum();
    }

    private void consumeAnswer(ChatRun previous, ChatRun run, ChatAnswer answer) {
        List<String> values = answer.values() == null ? List.of() : answer.values();
        String freeText = blankToNull(answer.freeText());
        ChatRun.AnswerResult result = previous.answerQuestion(answer.questionId(), values, freeText);
        switch (result) {
            case ALREADY_ANSWERED -> throw new ApiException(HttpStatus.CONFLICT,
                    "CHAT_QUESTION_ALREADY_ANSWERED", "该问题已经作答，不能重复提交");
            case MISMATCH -> throw new ApiException(HttpStatus.BAD_REQUEST, "CHAT_ANSWER_MISMATCH",
                    "答案与待处理的问题不一致");
            case ACCEPTED -> run.inheritConditions(previous.conditions());
        }
    }

    private ChatAnswer validateAnswers(ChatRun previous, ChatRequest request) {
        List<ChatAnswer> answers = request.answers();
        if (answers == null || answers.size() != 1) {
            throw invalid("answers", "请提交且只提交一个待处理问题的答案");
        }
        ChatAnswer answer = answers.getFirst();
        ChatRun.PendingQuestion pending = previous.pendingQuestion();
        if (pending == null) {
            throw new ApiException(HttpStatus.CONFLICT, "CHAT_QUESTION_NOT_PENDING",
                    "原执行没有待处理的问题");
        }
        MessagePart.Question question = pending.question();
        if (!question.questionId().equals(answer.questionId())) {
            throw invalid("answers", "答案的问题标识与待处理问题不一致");
        }
        List<String> values = answer.values() == null ? List.of() : List.copyOf(answer.values());
        String freeText = blankToNull(answer.freeText());
        boolean skipped = Boolean.TRUE.equals(answer.skipped());
        if (skipped) {
            if (question.required()) {
                throw invalid("answers", "该问题必答，不能跳过");
            }
            if (!values.isEmpty() || freeText != null) {
                throw invalid("answers", "跳过时不能同时提交选项或文本");
            }
            return new ChatAnswer(answer.questionId(), List.of(), null, true);
        }
        if (values.size() > props.maxAnswerValues()) {
            throw invalid("answers", "一次提交的选项数超过上限");
        }
        switch (question.mode()) {
            case SINGLE -> {
                if (values.size() != 1) {
                    throw invalid("answers", "单选题必须且只能选择一个选项");
                }
            }
            case MULTIPLE -> {
                int min = question.minSelections() == null ? 1 : question.minSelections();
                int max = question.maxSelections() == null
                        ? (question.options() == null ? values.size() : question.options().size())
                        : question.maxSelections();
                if (values.size() < min || values.size() > max) {
                    throw invalid("answers", "多选题的选择数量需要在 " + min + "–" + max + " 之间");
                }
            }
            case TEXT -> {
                if (!values.isEmpty()) {
                    throw invalid("answers", "文本题只接受 freeText");
                }
                if (freeText == null) {
                    throw invalid("answers", "文本题必须提供 freeText");
                }
            }
        }
        if (question.options() != null && !question.options().isEmpty()) {
            List<String> allowed = question.options().stream().map(MessagePart.QuestionOption::value).toList();
            for (String value : values) {
                if (!allowed.contains(value)) {
                    throw invalid("answers", "提交了不在选项集合内的取值");
                }
            }
        }
        int maxLength = question.maxLength() == null
                ? props.maxAnswerTextChars()
                : Math.min(question.maxLength(), props.maxAnswerTextChars());
        if (freeText != null && freeText.length() > maxLength) {
            throw invalid("answers", "文本长度超过该问题的上限");
        }
        return new ChatAnswer(answer.questionId(), values, freeText, false);
    }

    private void validateRequest(ChatRequest request) {
        CommandDigest.key(request.idempotencyKey());
        if (request.generationId() != null) {
            CommandDigest.key(request.generationId());
        }
        if (!request.clientConversationId().matches("[A-Za-z0-9._:-]{8,80}")) {
            throw invalid("clientConversationId", "会话标识需为 8–80 位字母、数字或 . _ : -");
        }
        if (request.providerId() != null && !request.providerId().isBlank()
                && !request.providerId().matches("[A-Za-z0-9-]{8,64}")) {
            throw invalid("providerId", "供应商标识格式不正确");
        }
        if (request.modelId() != null && !request.modelId().isBlank()
                && !request.modelId().matches("[A-Za-z0-9._:/-]{1,120}")) {
            throw invalid("modelId", "模型标识格式不正确");
        }
        if (request.messages().size() > props.maxMessages()) {
            throw invalid("messages", "消息条数超过本地演示上限");
        }
        int total = 0;
        for (ChatRequest.ChatTurn turn : request.messages()) {
            int length = turn.text().length();
            if (length > props.maxMessageChars()) {
                throw invalid("messages", "单条消息长度超过本地演示上限");
            }
            total += length;
        }
        if (total > props.maxTotalChars()) {
            throw invalid("messages", "消息总长度超过本地演示上限");
        }
        if (request.answers() != null && request.answers().size() > 16) {
            throw invalid("answers", "一次提交的答案数量超过上限");
        }
    }

    static String digestOf(ChatRequest request) {
        Object[] parts = new Object[7 + request.messages().size() * 2
                + (request.answers() == null ? 0 : request.answers().size() * 4)];
        int index = 0;
        parts[index++] = request.clientConversationId();
        parts[index++] = request.generationId();
        parts[index++] = request.providerId();
        parts[index++] = request.modelId();
        parts[index++] = request.reasoningEffort();
        for (ChatRequest.ChatTurn turn : request.messages()) {
            parts[index++] = turn.role().name();
            parts[index++] = turn.text();
        }
        if (request.answers() != null) {
            for (ChatAnswer answer : request.answers()) {
                parts[index++] = answer.questionId();
                List<String> values = answer.values() == null ? List.of() : answer.values();
                parts[index++] = String.join("\u001f", values.stream().sorted().toList());
                parts[index++] = answer.freeText();
                parts[index++] = Boolean.TRUE.equals(answer.skipped());
            }
        }
        return CommandDigest.digest(parts);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static ApiException invalid(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message,
                List.of(new ApiResponse.FieldError(field, message)));
    }
}
