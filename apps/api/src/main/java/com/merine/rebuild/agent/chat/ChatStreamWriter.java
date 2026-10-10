package com.merine.rebuild.agent.chat;

import com.merine.rebuild.agent.chat.dto.ChatEvent;
import com.merine.rebuild.agent.chat.dto.ChatErrorCode;
import com.merine.rebuild.agent.chat.dto.ChatRunView;
import com.merine.rebuild.agent.chat.dto.MessagePart;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/**
 * SSE 事件写出与运行记录同步的唯一入口：每个事件先落到 {@link ChatRun}，再写浏览器。
 * 终态由 {@link AtomicBoolean} 保证只出现一次；写失败只标记 broken，不抛出、不重复 complete。
 */
final class ChatStreamWriter {

    private final ChatRun run;
    private final SseEmitter emitter;
    private final ObjectMapper json;
    private final ChatProperties props;
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicBoolean terminal = new AtomicBoolean();
    private final AtomicBoolean completed = new AtomicBoolean();
    private volatile boolean broken;

    ChatStreamWriter(ChatRun run, SseEmitter emitter, ObjectMapper json, ChatProperties props) {
        this.run = run;
        this.emitter = emitter;
        this.json = json;
        this.props = props;
    }

    ChatRun run() {
        return run;
    }

    boolean broken() {
        return broken;
    }

    boolean terminal() {
        return terminal.get();
    }

    ChatRunView.ChatRunState runState() {
        return run.state();
    }

    boolean streamStart() {
        return send(new ChatEvent.StreamStart(ChatEvent.PROTOCOL_VERSION, run.messageId(),
                run.generationId(), run.clientConversationId(), run.conversationId(),
                ChatEvent.ExecutionMode.LOCAL_STUB, null));
    }

    boolean startPart(String partId, String partType) {
        if (run.partCount() >= props.maxPartsPerMessage()) {
            fail(ChatErrorCode.INTERNAL, "单条消息的部件数超过本地演示上限", false);
            return false;
        }
        if (!run.startPart(partId, partType)) {
            return false;
        }
        return send(new ChatEvent.PartStart(run.messageId(), partId, partType));
    }

    boolean textDelta(String partId, String delta) {
        if (delta == null || delta.isEmpty()) {
            return true;
        }
        if (run.textChars() + delta.length() > props.maxTotalChars()) {
            fail(ChatErrorCode.INTERNAL, "输出超过本地演示上限", false);
            return false;
        }
        if (!run.appendText(partId, delta)) {
            return false;
        }
        return send(new ChatEvent.TextDelta(run.messageId(), partId, delta));
    }

    boolean snapshotPart(String partId, MessagePart part) {
        if (!withinLimits(part)) {
            fail(ChatErrorCode.INTERNAL, "部件超过本地演示上限", false);
            return false;
        }
        run.snapshotPart(partId, part);
        if (part instanceof MessagePart.Question question) {
            run.setPendingQuestion(question);
        }
        return send(new ChatEvent.PartSnapshot(run.messageId(), partId, part));
    }

    /** 冻结上限在写出前校验：表格行列/单元格与追问选项数都不能超过配置。 */
    private boolean withinLimits(MessagePart part) {
        if (part instanceof MessagePart.Table table) {
            if (table.columns() != null && table.columns().size() > props.maxTableColumns()) {
                return false;
            }
            if (table.rows() != null) {
                if (table.rows().size() > props.maxTableRows()) {
                    return false;
                }
                for (var row : table.rows()) {
                    if (row == null) {
                        continue;
                    }
                    for (String cell : row) {
                        if (cell != null && cell.length() > props.maxTableCellChars()) {
                            return false;
                        }
                    }
                }
            }
        }
        return !(part instanceof MessagePart.Question question)
                || question.options() == null
                || question.options().size() <= props.maxQuestionOptions();
    }

    boolean donePart(String partId) {
        run.donePart(partId);
        return send(new ChatEvent.PartDone(run.messageId(), partId));
    }

    /** 记录上游返回的 token 用量；缺失时不写，落库为 NULL。 */
    void recordUsage(Integer promptTokens, Integer completionTokens) {
        run.recordUsage(promptTokens, completionTokens);
    }

    boolean cancelRequested() {
        return send(new ChatEvent.CancelRequested(run.messageId()));
    }

    void messageDone(ChatEvent.MessageStatus status) {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        sendInternal(new ChatEvent.MessageDone(run.messageId(), status, null));
        run.finish(switch (status) {
            case SUCCEEDED -> ChatRunView.ChatRunState.SUCCEEDED;
            case AWAITING_INPUT -> ChatRunView.ChatRunState.AWAITING_INPUT;
            case ABORTED -> ChatRunView.ChatRunState.ABORTED;
        });
        completeQuietly();
    }

    void fail(ChatErrorCode code, String message, boolean retryable) {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        run.markFailure(code.name(), message);
        sendInternal(new ChatEvent.ChatError(run.messageId(), code, message, retryable));
        run.finish(ChatRunView.ChatRunState.FAILED);
        completeQuietly();
    }

    /** 客户端已断开或无法写回：不尝试发事件，只把记录收成 ABORTED。 */
    void markAborted() {
        if (!terminal.compareAndSet(false, true)) {
            return;
        }
        run.finish(ChatRunView.ChatRunState.ABORTED);
        completeQuietly();
    }

    /** 心跳注释：不进事件流；失败即认为连接已断。 */
    void heartbeat() {
        if (terminal.get() || broken) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().comment("ping"));
        } catch (Exception error) {
            broken = true;
        }
    }

    void completeQuietly() {
        if (!completed.compareAndSet(false, true)) {
            return;
        }
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // 客户端已经断开时 complete 也可能失败；这里只收尾，不再向上抛。
        }
    }

    private boolean send(ChatEvent event) {
        if (terminal.get() || broken) {
            return false;
        }
        return sendInternal(event);
    }

    private boolean sendInternal(ChatEvent event) {
        if (broken) {
            return false;
        }
        try {
            String payload = json.writeValueAsString(event);
            emitter.send(SseEmitter.event().id(Long.toString(sequence.incrementAndGet())).data(payload));
            return true;
        } catch (Exception error) {
            broken = true;
            return false;
        }
    }
}
