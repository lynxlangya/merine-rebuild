package com.merine.rebuild.agent.chat;

import com.merine.rebuild.agent.chat.dto.ChatRequest;
import com.merine.rebuild.agent.chat.dto.ChatRunView;
import com.merine.rebuild.common.ApiException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 有上限、有 TTL 的内存执行记录：同 key 去重、断连核对、停止确认与追问校验都靠它。
 *
 * <p>记录不落库，服务重启即丢失；过期或重启后只能显示「结果未知」，不自动重新执行。
 * 容量与并发上限由 {@link ChatProperties} 冻结，准入是唯一入口，多标签并发也只能拿到一个名额。
 */
@Component
final class ChatRunRegistry {

    private final ChatProperties props;
    private final Map<String, ChatRun> byKey = new HashMap<>();
    private final Map<String, ChatRun> byGeneration = new HashMap<>();
    private final Map<Long, Integer> activeByUser = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private int activeGlobal;

    ChatRunRegistry(ChatProperties props) {
        this.props = props;
    }

    /** created=true 表示拿到了活动名额；false 表示同 key 已有终态结果，直接回原快照。 */
    record Admission(ChatRun run, boolean created) {}

    Admission admit(long userId, ChatRequest request, String digest) {
        lock.lock();
        try {
            purgeExpiredLocked(Instant.now());
            String key = keyOf(userId, request.idempotencyKey());
            ChatRun existing = byKey.get(key);
            if (existing != null) {
                if (!existing.digest().equals(digest)) {
                    throw new ApiException(HttpStatus.CONFLICT, "CHAT_REQUEST_CONFLICT",
                            "该幂等键已用于不同内容的请求，请换新的幂等键");
                }
                if (existing.state() == ChatRunView.ChatRunState.RUNNING) {
                    throw new ApiException(HttpStatus.CONFLICT, "CHAT_REQUEST_IN_PROGRESS",
                            "该请求正在执行，请先查询原执行状态");
                }
                return new Admission(existing, false);
            }
            if (activeGlobal >= props.maxActiveGlobal()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_CAPACITY_EXCEEDED",
                        "演示执行已达上限，请稍后再试");
            }
            int activeUser = activeByUser.getOrDefault(userId, 0);
            if (activeUser >= props.maxActivePerUser()) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CHAT_CONCURRENCY_LIMIT",
                        "同一账号同时只能有一条演示执行");
            }
            if (byKey.size() >= props.maxRecords() && !evictOldestTerminalLocked()) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CHAT_CAPACITY_EXCEEDED",
                        "演示记录已满，请稍后再试");
            }
            ChatRun run = new ChatRun(userId, request.idempotencyKey(), digest,
                    java.util.UUID.randomUUID().toString(), java.util.UUID.randomUUID().toString(),
                    request.clientConversationId());
            byKey.put(key, run);
            byGeneration.put(run.generationId(), run);
            activeByUser.merge(userId, 1, Integer::sum);
            activeGlobal++;
            return new Admission(run, true);
        } finally {
            lock.unlock();
        }
    }

    /** 只按当前用户查；找不到或不属于该用户都返回 null，不泄露存在性。 */
    ChatRun findOwned(long userId, String idempotencyKey) {
        lock.lock();
        try {
            ChatRun run = byKey.get(keyOf(userId, idempotencyKey));
            return run != null && run.userId() == userId ? run : null;
        } finally {
            lock.unlock();
        }
    }

    ChatRun findOwnedGeneration(long userId, String generationId) {
        lock.lock();
        try {
            ChatRun run = generationId == null ? null : byGeneration.get(generationId);
            return run != null && run.userId() == userId ? run : null;
        } finally {
            lock.unlock();
        }
    }

    /** 幂等收尾：无论终态由写入线程还是收尾方落定，活动名额只释放一次。 */
    void finish(ChatRun run, ChatRunView.ChatRunState state) {
        lock.lock();
        try {
            run.finish(state);
            if (run.releaseSlot()) {
                releaseLocked(run);
            }
        } finally {
            lock.unlock();
        }
    }

    /** 该用户在这个会话里是否有未结束的轮次（正在生成或等待作答）；删除会话前的守卫用。 */
    boolean hasUnfinishedRunInConversation(long userId, String conversationId) {
        lock.lock();
        try {
            return byKey.values().stream().anyMatch(run -> run.userId() == userId
                    && (run.state() == ChatRunView.ChatRunState.RUNNING
                        || run.state() == ChatRunView.ChatRunState.AWAITING_INPUT)
                    && conversationId.equals(run.conversationId()));
        } finally {
            lock.unlock();
        }
    }

    /** 未受理完成的记录直接丢弃：不留下一条用户能用原键重试失败的快照。 */
    void discard(ChatRun run) {
        lock.lock();
        try {
            removeLocked(run);
            if (run.releaseSlot()) {
                releaseLocked(run);
            }
        } finally {
            lock.unlock();
        }
    }

    /** 定时清理：只清终态记录；RUNNING 由执行期限负责收尾。 */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    void scheduledPurge() {
        purgeExpired();
    }

    void purgeExpired() {
        lock.lock();
        try {
            purgeExpiredLocked(Instant.now());
        } finally {
            lock.unlock();
        }
    }

    int activeGlobal() {
        lock.lock();
        try {
            return activeGlobal;
        } finally {
            lock.unlock();
        }
    }

    int recordCount() {
        lock.lock();
        try {
            return byKey.size();
        } finally {
            lock.unlock();
        }
    }

    private void purgeExpiredLocked(Instant now) {
        Instant threshold = now.minus(props.recordTtl());
        List<ChatRun> expired = byKey.values().stream()
                .filter(run -> run.state() != ChatRunView.ChatRunState.RUNNING)
                .filter(run -> run.finishedAt() != null && run.finishedAt().isBefore(threshold))
                .toList();
        expired.forEach(this::removeLocked);
    }

    private boolean evictOldestTerminalLocked() {
        return byKey.values().stream()
                .filter(run -> run.state() != ChatRunView.ChatRunState.RUNNING)
                .min(Comparator.comparing(ChatRun::finishedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(run -> {
                    removeLocked(run);
                    return true;
                })
                .orElse(false);
    }

    private void removeLocked(ChatRun run) {
        byKey.remove(keyOf(run.userId(), run.idempotencyKey()));
        byGeneration.remove(run.generationId());
    }

    private void releaseLocked(ChatRun run) {
        if (activeGlobal > 0) {
            activeGlobal--;
        }
        activeByUser.computeIfPresent(run.userId(), (userId, count) -> count <= 1 ? null : count - 1);
    }

    private static String keyOf(long userId, String idempotencyKey) {
        return userId + ":" + idempotencyKey;
    }
}
