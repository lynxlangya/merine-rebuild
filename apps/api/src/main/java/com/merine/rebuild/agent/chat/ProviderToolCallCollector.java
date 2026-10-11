package com.merine.rebuild.agent.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import tools.jackson.databind.JsonNode;

/** 有界地按 index 组装 SSE 工具分片；未完成的计划不允许执行。 */
final class ProviderToolCallCollector {
    private static final class Fragment {
        String id;
        final StringBuilder name = new StringBuilder();
        final StringBuilder arguments = new StringBuilder();
    }
    private final ChatProperties props;
    private final TreeMap<Integer, Fragment> fragments = new TreeMap<>();
    private boolean finished;

    ProviderToolCallCollector(ChatProperties props) { this.props = props; }

    void append(JsonNode calls) {
        if (finished || !calls.isArray()) throw ProviderChatClient.protocol("模型工具调用格式无效");
        for (JsonNode call : calls) {
            var index = call.path("index");
            if (!index.isIntegralNumber() || !index.canConvertToInt()
                    || index.asInt() < 0 || index.asInt() >= props.maxToolCalls()) {
                throw ProviderChatClient.protocol("模型请求的查询次数超过上限");
            }
            Fragment fragment = fragments.computeIfAbsent(index.asInt(), ignored -> new Fragment());
            if (call.hasNonNull("id")) {
                if (!call.path("id").isTextual()) throw ProviderChatClient.protocol("模型工具标识无效");
                String id = call.path("id").asText();
                // 千问的后续增量可能带空 id：表示没有新标识，不覆盖已按 index 保存的调用。
                if (!id.isEmpty()) {
                    if (!id.matches("[A-Za-z0-9._:-]{1,128}")) throw ProviderChatClient.protocol("模型工具标识无效");
                    if (fragment.id != null && !fragment.id.equals(id)) throw ProviderChatClient.protocol("模型工具标识发生变化");
                    fragment.id = id;
                }
            }
            if (call.hasNonNull("type") && !"function".equals(call.path("type").asText())) {
                throw ProviderChatClient.protocol("模型请求了未开放的工具类型");
            }
            appendText(fragment.name, call.path("function").path("name"), 128);
            appendText(fragment.arguments, call.path("function").path("arguments"), props.maxToolArgumentChars());
        }
    }

    void finish() {
        if (finished || fragments.isEmpty()) throw ProviderChatClient.protocol("模型工具结束标记与调用不一致");
        finished = true;
    }

    List<ProviderToolCall> completed() {
        if (!fragments.isEmpty() && !finished) throw ProviderChatClient.protocol("模型工具参数未完整结束");
        List<ProviderToolCall> calls = new ArrayList<>();
        int expected = 0;
        for (var entry : fragments.entrySet()) {
            if (entry.getKey() != expected++) throw ProviderChatClient.protocol("模型工具分片序号不连续");
            Fragment fragment = entry.getValue();
            if (fragment.id == null) throw ProviderChatClient.protocol("模型工具标识无效");
            calls.add(new ProviderToolCall(fragment.id, fragment.name.toString(), fragment.arguments.toString()));
        }
        return List.copyOf(calls);
    }

    private static void appendText(StringBuilder target, JsonNode value, int limit) {
        if (value.isMissingNode() || value.isNull()) return;
        if (!value.isTextual() || target.length() + value.asText().length() > limit) {
            throw ProviderChatClient.protocol("模型工具参数格式无效或超过上限");
        }
        target.append(value.asText());
    }
}
