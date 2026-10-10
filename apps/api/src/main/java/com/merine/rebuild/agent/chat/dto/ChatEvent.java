package com.merine.rebuild.agent.chat.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * SSE 事件载荷的判别联合：流上只有一个事件名，JSON 内用 {@code type} 区分。
 *
 * <p>不变量（由 {@code ChatStreamWriter} 与 {@code ChatService} 保证）：
 * <ul>
 *   <li>{@code STREAM_START} 是首个数据事件且恰好一次；</li>
 *   <li>每个 {@code partId} 只 {@code PART_START} 一次，部件顺序即开始顺序；</li>
 *   <li>{@code MESSAGE_DONE}/{@code ERROR} 互斥且各自最多一次，之后不再有数据事件；</li>
 *   <li>{@code CANCEL_REQUESTED} 只表示服务端已受理停止，终态仍是 {@code MESSAGE_DONE(ABORTED)}。</li>
 * </ul>
 * 标注为必填的字段是协议不变量的一部分：缺失即视为损坏事件，前端不静默接受。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ChatEvent.StreamStart.class, name = "STREAM_START"),
        @JsonSubTypes.Type(value = ChatEvent.PartStart.class, name = "PART_START"),
        @JsonSubTypes.Type(value = ChatEvent.TextDelta.class, name = "TEXT_DELTA"),
        @JsonSubTypes.Type(value = ChatEvent.PartSnapshot.class, name = "PART_SNAPSHOT"),
        @JsonSubTypes.Type(value = ChatEvent.PartDone.class, name = "PART_DONE"),
        @JsonSubTypes.Type(value = ChatEvent.CancelRequested.class, name = "CANCEL_REQUESTED"),
        @JsonSubTypes.Type(value = ChatEvent.MessageDone.class, name = "MESSAGE_DONE"),
        @JsonSubTypes.Type(value = ChatEvent.ChatError.class, name = "ERROR")
})
@Schema(name = "ChatStreamEvent", description = "SSE data 载荷；type 判别联合", oneOf = {
        ChatEvent.StreamStart.class, ChatEvent.PartStart.class, ChatEvent.TextDelta.class,
        ChatEvent.PartSnapshot.class, ChatEvent.PartDone.class, ChatEvent.CancelRequested.class,
        ChatEvent.MessageDone.class, ChatEvent.ChatError.class
}, discriminatorProperty = "type", discriminatorMapping = {
        @DiscriminatorMapping(value = "STREAM_START", schema = ChatEvent.StreamStart.class),
        @DiscriminatorMapping(value = "PART_START", schema = ChatEvent.PartStart.class),
        @DiscriminatorMapping(value = "TEXT_DELTA", schema = ChatEvent.TextDelta.class),
        @DiscriminatorMapping(value = "PART_SNAPSHOT", schema = ChatEvent.PartSnapshot.class),
        @DiscriminatorMapping(value = "PART_DONE", schema = ChatEvent.PartDone.class),
        @DiscriminatorMapping(value = "CANCEL_REQUESTED", schema = ChatEvent.CancelRequested.class),
        @DiscriminatorMapping(value = "MESSAGE_DONE", schema = ChatEvent.MessageDone.class),
        @DiscriminatorMapping(value = "ERROR", schema = ChatEvent.ChatError.class)
})
public sealed interface ChatEvent {

    /** 本期协议版本；不兼容变更时递增。 */
    int PROTOCOL_VERSION = 1;

    enum ExecutionMode { LOCAL_STUB, PROVIDER }

    enum MessageStatus { SUCCEEDED, AWAITING_INPUT, ABORTED }

    record StreamStart(
            @Schema(requiredMode = REQUIRED) int protocolVersion,
            @Schema(requiredMode = REQUIRED) String messageId,
            @Schema(requiredMode = REQUIRED) String generationId,
            @Schema(requiredMode = REQUIRED) String clientConversationId,
            /** 服务端会话标识：落库后的会话就是它；前端据此把临时会话换成服务端会话。 */
            @Schema(requiredMode = REQUIRED) String conversationId,
            @Schema(requiredMode = REQUIRED) ExecutionMode executionMode,
            String model) implements ChatEvent {}

    record PartStart(
            @Schema(requiredMode = REQUIRED) String messageId,
            @Schema(requiredMode = REQUIRED) String partId,
            @Schema(requiredMode = REQUIRED) String partType) implements ChatEvent {}

    /** 文本增量；{@code delta} 非空，只作用于已 PART_START 的 TEXT 部件。 */
    record TextDelta(
            @Schema(requiredMode = REQUIRED) String messageId,
            @Schema(requiredMode = REQUIRED) String partId,
            @Schema(requiredMode = REQUIRED) String delta) implements ChatEvent {}

    /** 结构化部件定稿：同一 partId 只发一次，载荷必须是完整部件。 */
    record PartSnapshot(
            @Schema(requiredMode = REQUIRED) String messageId,
            @Schema(requiredMode = REQUIRED) String partId,
            @Schema(requiredMode = REQUIRED) MessagePart part) implements ChatEvent {}

    record PartDone(
            @Schema(requiredMode = REQUIRED) String messageId,
            @Schema(requiredMode = REQUIRED) String partId) implements ChatEvent {}

    /** 服务端已受理停止；ABORTED 需等待 MESSAGE_DONE 确认。 */
    record CancelRequested(@Schema(requiredMode = REQUIRED) String messageId) implements ChatEvent {}

    record MessageDone(
            @Schema(requiredMode = REQUIRED) String messageId,
            @Schema(requiredMode = REQUIRED) MessageStatus status,
            TokenUsage usage) implements ChatEvent {}

    record ChatError(
            String messageId,
            @Schema(requiredMode = REQUIRED) ChatErrorCode code,
            @Schema(requiredMode = REQUIRED) String message,
            @Schema(requiredMode = REQUIRED) boolean retryable) implements ChatEvent {}

    /** 用量缺失时为 null，界面显示「未知」，不编造数值。 */
    record TokenUsage(Integer inputTokens, Integer outputTokens) {}
}
