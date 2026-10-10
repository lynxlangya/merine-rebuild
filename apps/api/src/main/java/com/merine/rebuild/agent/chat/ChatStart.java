package com.merine.rebuild.agent.chat;

import com.merine.rebuild.agent.chat.dto.ChatRunView;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** POST 的两种结果：新执行返回 SSE；同 key 已完成返回原快照 JSON。 */
sealed interface ChatStart {

    record Stream(SseEmitter emitter) implements ChatStart {}

    record Snapshot(ChatRunView view) implements ChatStart {}
}
