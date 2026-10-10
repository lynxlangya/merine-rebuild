package com.merine.rebuild.agent.chat;

import java.time.Instant;

/**
 * 脚本化替身的分步节奏：每一步之间检查停止、断连与执行期限。
 * 任一条件命中就抛出 {@link Stopped}，由 {@link ChatService} 决定如何收尾。
 */
final class ChatPacing {

    enum Reason { CANCELLED, DISCONNECTED, TIMEOUT }

    static final class Stopped extends RuntimeException {
        private final Reason reason;

        Stopped(Reason reason) {
            super(reason.name(), null, false, false);
            this.reason = reason;
        }

        Reason reason() {
            return reason;
        }
    }

    private final ChatRun run;
    private final ChatStreamWriter writer;
    private final Instant deadline;

    ChatPacing(ChatRun run, ChatStreamWriter writer, Instant deadline) {
        this.run = run;
        this.writer = writer;
        this.deadline = deadline;
    }

    /** 停止 → 断连 → 期限：用户意图优先于连接状态。 */
    void check() {
        if (run.cancelRequested()) {
            throw new Stopped(Reason.CANCELLED);
        }
        if (writer.broken()) {
            throw new Stopped(Reason.DISCONNECTED);
        }
        if (Instant.now().isAfter(deadline)) {
            throw new Stopped(Reason.TIMEOUT);
        }
    }

    void sleep(long millis) throws InterruptedException {
        if (millis > 0) {
            Thread.sleep(millis);
        }
        check();
    }

    /** 让出执行权但不等待：用于连续写多个事件的场景。 */
    void tick() {
        Thread.onSpinWait();
        check();
    }
}
