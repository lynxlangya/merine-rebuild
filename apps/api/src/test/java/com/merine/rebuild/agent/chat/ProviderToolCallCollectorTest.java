package com.merine.rebuild.agent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

/** 分片、完整性与大小界限用纯 Java 验证；工具执行/权限由真实 HTTP + MySQL 验证。 */
class ProviderToolCallCollectorTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ProviderToolCallCollector collector() {
        return new ProviderToolCallCollector(new ChatProperties(ChatProperties.ChatMode.PROVIDER,
                40, 8000, 128000, 32, 100, 12, 200, 12, 8, 500, 1, 20, 200,
                Duration.ofMinutes(30), Duration.ofMinutes(5), Duration.ofSeconds(15), true, 2, 2048));
    }

    @Test
    void assemblesInterleavedFunctionFragmentsByIndex() {
        var calls = collector();
        var first = JSON.createArrayNode();
        first.addObject().put("index", 1).put("id", "call-last").put("type", "function")
                .putObject("function").put("name", "query_completed_").put("arguments", "{\"period\":");
        first.addObject().put("index", 0).put("id", "call-now").put("type", "function")
                .putObject("function").put("name", "query_completed_").put("arguments", "{\"period\":");
        calls.append(first);
        var second = JSON.createArrayNode();
        second.addObject().put("index", 0).putObject("function")
                .put("name", "task_count").put("arguments", "\"THIS_MONTH\"}");
        second.addObject().put("index", 1).putObject("function")
                .put("name", "task_count").put("arguments", "\"LAST_MONTH\"}");
        calls.append(second);
        calls.finish();
        assertThat(calls.completed()).containsExactly(
                new ProviderToolCall("call-now", AgentQueryTools.NAME, "{\"period\":\"THIS_MONTH\"}"),
                new ProviderToolCall("call-last", AgentQueryTools.NAME, "{\"period\":\"LAST_MONTH\"}"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"call-recent"})
    void retainsFirstIdWhenContinuationIdIsNullEmptyOrRepeated(String id) {
        var calls = collector();
        var first = JSON.createArrayNode();
        first.addObject().put("index", 0).put("id", "call-recent").put("type", "function")
                .putObject("function").put("name", AgentQueryTools.NAME).put("arguments", "{\"period\":");
        calls.append(first);
        var continuation = JSON.createArrayNode();
        var fragment = continuation.addObject().put("index", 0);
        if (id == null) fragment.putNull("id");
        else fragment.put("id", id);
        fragment.putObject("function").put("name", "").put("arguments", "\"ROLLING_MONTH\"}");
        calls.append(continuation);
        calls.finish();
        assertThat(calls.completed()).containsExactly(new ProviderToolCall("call-recent",
                AgentQueryTools.NAME, "{\"period\":\"ROLLING_MONTH\"}"));
    }

    @Test
    void acceptsEmptyIdBeforeActualIdArrivesButRejectsCompletedCallWithoutId() {
        var first = JSON.createArrayNode();
        first.addObject().put("index", 0).put("id", "").putObject("function")
                .put("name", AgentQueryTools.NAME).put("arguments", "{\"period\":");
        var calls = collector();
        calls.append(first);
        var second = JSON.createArrayNode();
        second.addObject().put("index", 0).put("id", "call-recent").putObject("function")
                .put("arguments", "\"ROLLING_MONTH\"}");
        calls.append(second);
        calls.finish();
        assertThat(calls.completed()).containsExactly(new ProviderToolCall("call-recent",
                AgentQueryTools.NAME, "{\"period\":\"ROLLING_MONTH\"}"));

        var incomplete = collector();
        incomplete.append(first);
        incomplete.finish();
        assertThatThrownBy(incomplete::completed).isInstanceOf(ChatUpstreamException.class)
                .hasMessage("模型工具标识无效");
    }

    @Test
    void refusesIncompleteMissingIndexesChangedIdsAndLateFragments() {
        var calls = collector();
        var first = JSON.readTree("""
                [{"index":0,"id":"call-a","function":{"name":"query_completed_task_count","arguments":"{}"}}]
                """);
        calls.append(first);
        assertThatThrownBy(calls::completed).isInstanceOf(ChatUpstreamException.class);
        calls.append(JSON.readTree("[{\"index\":0,\"id\":\"\"}]"));
        assertThatThrownBy(() -> calls.append(JSON.readTree("[{\"index\":0,\"id\":\"changed\"}]")))
                .isInstanceOf(ChatUpstreamException.class);
        calls.finish();
        assertThatThrownBy(() -> calls.append(first)).isInstanceOf(ChatUpstreamException.class);
        var gap = collector();
        gap.append(JSON.readTree("[{\"index\":1,\"id\":\"call-b\"}]"));
        gap.finish();
        assertThatThrownBy(gap::completed).isInstanceOf(ChatUpstreamException.class);
    }

    @Test
    void refusesMalformedNonEmptyOrNonTextualIds() {
        for (String id : new String[] {" ", "bad/id", "bad\nid", "x".repeat(129)}) {
            var first = JSON.createArrayNode();
            first.addObject().put("index", 0).put("id", id);
            assertThatThrownBy(() -> collector().append(first)).isInstanceOf(ChatUpstreamException.class)
                    .hasMessage("模型工具标识无效");
        }
        assertThatThrownBy(() -> collector().append(JSON.readTree("[{\"index\":0,\"id\":1}]")))
                .isInstanceOf(ChatUpstreamException.class).hasMessage("模型工具标识无效");
    }

    @Test
    void refusesQuotaOverflowAndOversizedArguments() {
        var calls = collector();
        assertThatThrownBy(() -> calls.append(JSON.readTree("[{\"index\":2}]")))
                .isInstanceOf(ChatUpstreamException.class);
        var oversized = JSON.createArrayNode();
        oversized.addObject().put("index", 0).putObject("function").put("arguments", "x".repeat(2049));
        assertThatThrownBy(() -> calls.append(oversized)).isInstanceOf(ChatUpstreamException.class);
    }
}
