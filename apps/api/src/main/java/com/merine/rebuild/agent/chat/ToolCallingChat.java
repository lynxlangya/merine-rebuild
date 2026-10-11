package com.merine.rebuild.agent.chat;

import com.merine.rebuild.agent.chat.dto.ChatRequest;
import com.merine.rebuild.agent.provider.ProviderTarget;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/** 一轮受控工具编排：完整选择 → 整批验证 → 本轮查询 → 最终回答；不提供无限调用循环。 */
@Component
final class ToolCallingChat {
    private final ProviderChatClient client;
    private final AgentQueryTools tools;

    ToolCallingChat(ProviderChatClient client, AgentQueryTools tools) {
        this.client = client;
        this.tools = tools;
    }

    void stream(ChatStreamWriter writer, ChatPacing pacing, ChatRequest request, ProviderTarget target,
                Authentication authentication, HttpSession session) throws InterruptedException {
        if (!tools.available(authentication, target.vendor())) {
            client.stream(writer, pacing, request, target);
            return;
        }
        tools.requireCurrentSession(authentication, session);
        var selection = client.selectTools(writer, pacing, request, target, tools);
        pacing.check();
        tools.requireCurrentSession(authentication, session);
        List<String> queryResults = new ArrayList<>();
        if (!selection.calls().isEmpty()) {
            var plan = tools.validate(selection.calls());
            Map<AgentQueryTools.QueryKey, AgentQueryTools.ExecutedQuery> results = new HashMap<>();
            for (var query : plan) {
                pacing.check();
                tools.requireCurrentSession(authentication, session);
                var result = results.get(query.key());
                if (result == null) {
                    result = tools.execute(authentication, session, query);
                    results.put(query.key(), result);
                    queryResults.add(result.protocolJson());
                    pacing.check();
                    String partId = "p-query-" + results.size();
                    if (!writer.startPart(partId, "TEXT") || !writer.textDelta(partId, result.displayText())
                            || !writer.donePart(partId)) {
                        throw new ChatPacing.Stopped(ChatPacing.Reason.DISCONNECTED);
                    }
                }
            }
        }
        pacing.check();
        tools.requireCurrentSession(authentication, session);
        // 选择阶段的文本不直接展示；最终回答使用原推理档位，避免把查询阶段变成用户的普通回答。
        client.streamAnswer(writer, pacing, request, target, queryResults, tools.instructions());
    }
}
