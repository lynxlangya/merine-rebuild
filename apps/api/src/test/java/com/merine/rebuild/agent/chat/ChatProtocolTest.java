package com.merine.rebuild.agent.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.merine.rebuild.agent.chat.dto.ChatEvent;
import com.merine.rebuild.agent.chat.dto.ChatEvent.ExecutionMode;
import com.merine.rebuild.agent.chat.dto.ChatRequest;
import com.merine.rebuild.agent.chat.dto.ChatRunView;
import com.merine.rebuild.agent.chat.dto.MessagePart;
import com.merine.rebuild.common.ApiException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * 协议与内存执行状态的纯 Java 回归：事件不变量、部件状态、追问原子性、
 * 准入去重/上限/TTL、请求摘要与 JSON 判别序列化。
 * 不启动 Spring、不连数据库；真实端点行为由 SSE 集成测试覆盖。
 */
class ChatProtocolTest {

    private static final String CONVERSATION = "conv-0001-demo";

    @Test
    void eventsSerializeWithTypeDiscriminatorAndRoundTrip() {
        var json = JsonMapper.builder().build();
        String payload = json.writeValueAsString(new ChatEvent.StreamStart(
                ChatEvent.PROTOCOL_VERSION, "m-1", "g-1", CONVERSATION, "srv-1",
                ExecutionMode.LOCAL_STUB, null));
        assertThat(payload).contains("\"type\":\"STREAM_START\"")
                .contains("\"protocolVersion\":1")
                .contains("\"executionMode\":\"LOCAL_STUB\"");
        assertThat(json.readValue(payload, ChatEvent.class)).isInstanceOf(ChatEvent.StreamStart.class);

        String question = json.writeValueAsString(new MessagePart.Question(
                "Q1", "口径？", MessagePart.QuestionMode.SINGLE,
                List.of(new MessagePart.QuestionOption("CASE", "立案", null)),
                false, null, null, null, null, true));
        assertThat(question).contains("\"type\":\"QUESTION\"");
        assertThat(json.readValue(question, MessagePart.class)).isInstanceOf(MessagePart.Question.class);
    }

    @Test
    void runKeepsPartOrderTextAndTerminalStatuses() {
        ChatRun run = run();
        assertThat(run.startPart("p-1", "TEXT")).isTrue();
        assertThat(run.startPart("p-1", "TEXT")).isFalse();
        assertThat(run.appendText("p-1", "甲")).isTrue();
        assertThat(run.appendText("p-1", "乙")).isTrue();
        assertThat(run.appendText("p-2", "不存在")).isFalse();
        assertThat(run.textChars()).isEqualTo(2);

        assertThat(run.startPart("p-2", "TABLE")).isTrue();
        run.snapshotPart("p-2", new MessagePart.Table("表", List.of(), List.of(), null));
        run.donePart("p-2");
        run.finish(ChatRunView.ChatRunState.AWAITING_INPUT);

        var view = run.view();
        assertThat(view.status()).isEqualTo(ChatRunView.ChatRunState.AWAITING_INPUT);
        assertThat(view.parts()).hasSize(2);
        assertThat(view.parts().getFirst().status()).isEqualTo(ChatRunView.MessagePartStatus.DONE);
        assertThat(((MessagePart.Text) view.parts().getFirst().part()).text()).isEqualTo("甲乙");
        assertThat(view.finishedAt()).isNotNull();
    }

    @Test
    void failureKeepsUnfinishedPartsAsFailed() {
        ChatRun run = run();
        run.startPart("p-1", "TEXT");
        run.appendText("p-1", "半句");
        run.startPart("p-2", "CHART");
        run.finish(ChatRunView.ChatRunState.FAILED);
        assertThat(run.view().parts())
                .allMatch(part -> part.status() == ChatRunView.MessagePartStatus.FAILED);
    }

    @Test
    void questionIsConsumedAtomicallyAndConditionsAreRecorded() {
        ChatRun run = run();
        run.setPendingQuestion(new MessagePart.Question("Q1", "口径？",
                MessagePart.QuestionMode.SINGLE, List.of(
                new MessagePart.QuestionOption("CASE", "立案", null)), false, null, null, null, null, true));
        assertThat(run.answerQuestion("Q1", List.of("CASE"), null)).isEqualTo(ChatRun.AnswerResult.ACCEPTED);
        assertThat(run.answerQuestion("Q1", List.of("LEAD"), null))
                .isEqualTo(ChatRun.AnswerResult.ALREADY_ANSWERED);
        assertThat(run.answerQuestion("Q2", List.of("CASE"), null))
                .isEqualTo(ChatRun.AnswerResult.MISMATCH);
        assertThat(run.conditions()).containsEntry("Q1", "立案");
        assertThat(run.view().question().answered()).isTrue();
    }

    @Test
    void registryDeduplicatesByKeyAndRejectsDifferentCommand() {
        ChatRunRegistry registry = new ChatRunRegistry(props(50, 1, 10, Duration.ofMinutes(30)));
        ChatRequest request = request("key-0001-aaaa", "甲");
        ChatRunRegistry.Admission first = registry.admit(1L, request, "digest-a");
        assertThat(first.created()).isTrue();
        assertThatThrownBy(() -> registry.admit(1L, request, "digest-a"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("正在执行");
        registry.finish(first.run(), ChatRunView.ChatRunState.SUCCEEDED);
        ChatRunRegistry.Admission replay = registry.admit(1L, request, "digest-a");
        assertThat(replay.created()).isFalse();
        assertThat(replay.run().state()).isEqualTo(ChatRunView.ChatRunState.SUCCEEDED);
        assertThatThrownBy(() -> registry.admit(1L, request, "digest-b"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("不同内容");
    }

    @Test
    void registryEnforcesPerUserLimit() {
        ChatRunRegistry registry = new ChatRunRegistry(props(50, 1, 10, Duration.ofMinutes(30)));
        registry.admit(1L, request("key-0001-aaaa", "甲"), "digest-1");
        assertThatThrownBy(() -> registry.admit(1L, request("key-0002-bbbb", "乙"), "digest-2"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("同时只能有一条");
    }

    @Test
    void registryEnforcesGlobalLimit() {
        ChatRunRegistry registry = new ChatRunRegistry(props(50, 1, 1, Duration.ofMinutes(30)));
        registry.admit(1L, request("key-0001-aaaa", "甲"), "digest-1");
        assertThatThrownBy(() -> registry.admit(2L, request("key-0002-bbbb", "乙"), "digest-2"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("上限");
    }

    @Test
    void registryEvictsOldestTerminalAndExpiresByTtl() throws Exception {
        ChatRunRegistry registry = new ChatRunRegistry(props(1, 1, 10, Duration.ofMillis(1)));
        ChatRunRegistry.Admission first = registry.admit(1L, request("key-0001-aaaa", "甲"), "digest-1");
        registry.finish(first.run(), ChatRunView.ChatRunState.SUCCEEDED);
        ChatRunRegistry.Admission second = registry.admit(1L, request("key-0002-bbbb", "乙"), "digest-2");
        assertThat(second.created()).isTrue();
        assertThat(registry.recordCount()).isEqualTo(1);
        registry.finish(second.run(), ChatRunView.ChatRunState.SUCCEEDED);
        Thread.sleep(5);
        registry.purgeExpired();
        assertThat(registry.recordCount()).isZero();
        assertThat(registry.findOwned(1L, "key-0002-bbbb")).isNull();
    }

    @Test
    void slotIsReleasedWhenWriterFinishedFirst() {
        ChatRunRegistry registry = new ChatRunRegistry(props(50, 1, 10, Duration.ofMinutes(30)));
        ChatRunRegistry.Admission admission = registry.admit(1L, request("key-0001-aaaa", "甲"), "digest-1");
        // 写入线程先落终态（writer.messageDone），收尾方随后再 finish：名额仍必须释放。
        admission.run().finish(ChatRunView.ChatRunState.SUCCEEDED);
        registry.finish(admission.run(), ChatRunView.ChatRunState.SUCCEEDED);
        assertThat(registry.activeGlobal()).isZero();
        assertThat(registry.admit(1L, request("key-0002-bbbb", "乙"), "digest-2").created()).isTrue();
    }

    @Test
    void digestIgnoresAnswerOptionOrderButKeepsMessageOrder() {
        ChatRequest first = requestWithMessages("key-0001-aaaa", List.of("甲", "乙"));
        assertThat(ChatService.digestOf(first))
                .isEqualTo(ChatService.digestOf(requestWithMessages("key-0001-aaaa", List.of("甲", "乙"))));
        assertThat(ChatService.digestOf(first))
                .isNotEqualTo(ChatService.digestOf(requestWithMessages("key-0001-aaaa", List.of("乙", "甲"))));
    }

    @Test
    void writerEnforcesFrozenPartLimits() {
        ChatProperties small = new ChatProperties(ChatProperties.ChatMode.PROVIDER, 40, 8000, 128000, /*maxPartsPerMessage*/ 1, 1, 1,
                200, 1, 8, 500, 1, 10, 50, Duration.ofMinutes(30),
                Duration.ofMinutes(5), Duration.ofSeconds(15), true);
        ChatRun run = run();
        var writer = new ChatStreamWriter(run, new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(10_000L),
                JsonMapper.builder().build(), small);
        writer.streamStart();
        assertThat(writer.startPart("p-1", "TEXT")).isTrue();
        assertThat(writer.startPart("p-2", "TEXT")).as("部件数超过上限").isFalse();
        assertThat(run.state()).isEqualTo(ChatRunView.ChatRunState.FAILED);

        ChatRun second = run();
        var secondWriter = new ChatStreamWriter(second,
                new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(10_000L),
                JsonMapper.builder().build(), small);
        secondWriter.streamStart();
        assertThat(secondWriter.snapshotPart("p-tb", new MessagePart.Table("大表",
                List.of(new MessagePart.TableColumn("a", "A", null, false),
                        new MessagePart.TableColumn("b", "B", null, false)),
                List.of(List.of("1", "2")), null))).as("表格列数超过上限").isFalse();
        assertThat(second.state()).isEqualTo(ChatRunView.ChatRunState.FAILED);
    }

    private static ChatRun run() {
        return new ChatRun(1L, "key-0001-aaaa", "digest", "m-1", "g-1", CONVERSATION);
    }

    private static ChatRequest request(String key, String text) {
        return requestWithMessages(key, List.of(text));
    }

    private static ChatRequest requestWithMessages(String key, List<String> texts) {
        return new ChatRequest(key, null, CONVERSATION, null, "Asia/Shanghai", null, null, null,
                texts.stream().map(text -> new ChatRequest.ChatTurn(ChatRequest.ChatRole.USER, text)).toList(),
                null, null);
    }

    private static ChatProperties props(int maxRecords, int maxActivePerUser, int maxActiveGlobal,
                                        Duration ttl) {
        return new ChatProperties(ChatProperties.ChatMode.PROVIDER, 40, 8000, 128000, 32, 100, 12, 200, 12, 8, 500,
                maxActivePerUser, maxActiveGlobal, maxRecords, ttl,
                Duration.ofMinutes(5), Duration.ofSeconds(15), true);
    }
}
