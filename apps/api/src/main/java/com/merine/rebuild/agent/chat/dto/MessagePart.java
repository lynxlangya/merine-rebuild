package com.merine.rebuild.agent.chat.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 助手消息内的结构化片段（判别联合，{@code type} 区分）。
 *
 * <p>服务端只下发类型与数据；前端按注册表渲染。TEXT 用增量事件流式追加，
 * 其余部件在 {@code PART_SNAPSHOT} 中一次定稿。
 * 标注为必填的字段缺失即视为损坏部件，前端以提示降级而不是静默渲染。
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = MessagePart.Text.class, name = "TEXT"),
        @JsonSubTypes.Type(value = MessagePart.Table.class, name = "TABLE"),
        @JsonSubTypes.Type(value = MessagePart.Chart.class, name = "CHART"),
        @JsonSubTypes.Type(value = MessagePart.Sources.class, name = "SOURCES"),
        @JsonSubTypes.Type(value = MessagePart.Steps.class, name = "STEPS"),
        @JsonSubTypes.Type(value = MessagePart.Question.class, name = "QUESTION"),
        @JsonSubTypes.Type(value = MessagePart.Notice.class, name = "NOTICE")
})
@Schema(name = "MessagePart", oneOf = {
        MessagePart.Text.class, MessagePart.Table.class, MessagePart.Chart.class,
        MessagePart.Sources.class, MessagePart.Steps.class, MessagePart.Question.class,
        MessagePart.Notice.class
}, discriminatorProperty = "type", discriminatorMapping = {
        @DiscriminatorMapping(value = "TEXT", schema = MessagePart.Text.class),
        @DiscriminatorMapping(value = "TABLE", schema = MessagePart.Table.class),
        @DiscriminatorMapping(value = "CHART", schema = MessagePart.Chart.class),
        @DiscriminatorMapping(value = "SOURCES", schema = MessagePart.Sources.class),
        @DiscriminatorMapping(value = "STEPS", schema = MessagePart.Steps.class),
        @DiscriminatorMapping(value = "QUESTION", schema = MessagePart.Question.class),
        @DiscriminatorMapping(value = "NOTICE", schema = MessagePart.Notice.class)
})
public sealed interface MessagePart {

    enum ChartType { LINE, BAR, STACKED_BAR, PIE }

    enum NoticeLevel { INFO, WARNING, ERROR }

    enum QuestionMode { SINGLE, MULTIPLE, TEXT }

    enum SourceKind { TASK, FLOW, INTEL, MENU, EXTERNAL }

    enum StepLabel { RETRIEVE, PLAN, QUERY, ANALYZE, CITE }

    enum StepStatus { RUNNING, DONE, FAILED }

    record Text(@Schema(requiredMode = REQUIRED) String text) implements MessagePart {}

    record Table(
            String title,
            @Schema(requiredMode = REQUIRED) List<TableColumn> columns,
            @Schema(requiredMode = REQUIRED) List<List<String>> rows,
            String note) implements MessagePart {}

    record Chart(@Schema(requiredMode = REQUIRED) ChartSpec spec) implements MessagePart {}

    record Sources(@Schema(requiredMode = REQUIRED) List<SourceRef> items) implements MessagePart {}

    record Steps(@Schema(requiredMode = REQUIRED) List<StepItem> items) implements MessagePart {}

    /**
     * 追问：选项与上限由服务端下发并在提交时服务端校验。
     * {@code allowOther} 只对选择型问题有意义；{@code maxLength} 只对 TEXT/自由文本有意义。
     */
    record Question(
            @Schema(requiredMode = REQUIRED) String questionId,
            @Schema(requiredMode = REQUIRED) String prompt,
            @Schema(requiredMode = REQUIRED) QuestionMode mode,
            List<QuestionOption> options,
            Boolean allowOther,
            Integer minSelections,
            Integer maxSelections,
            String placeholder,
            Integer maxLength,
            @Schema(requiredMode = REQUIRED) boolean required) implements MessagePart {}

    record Notice(
            @Schema(requiredMode = REQUIRED) NoticeLevel level,
            @Schema(requiredMode = REQUIRED) String text,
            String code) implements MessagePart {}

    record TableColumn(String key, String title, String unit, Boolean numeric) {}

    record ChartSpec(
            @Schema(requiredMode = REQUIRED) ChartType chartType,
            String title,
            String unit,
            List<String> categories,
            @Schema(requiredMode = REQUIRED) List<ChartSeries> series,
            ChartMeta meta) {}

    record ChartSeries(
            @Schema(requiredMode = REQUIRED) String name,
            @Schema(requiredMode = REQUIRED) List<Double> data) {}

    record ChartMeta(String metricId, String period, String coverage, String updatedAt) {}

    /** 站内来源只带 kind + refId/routeKey；外链只允许 http(s)。 */
    record SourceRef(
            @Schema(requiredMode = REQUIRED) SourceKind kind,
            String refId,
            String routeKey,
            String url,
            @Schema(requiredMode = REQUIRED) String title,
            String snippet) {}

    record StepItem(
            @Schema(requiredMode = REQUIRED) StepLabel label,
            @Schema(requiredMode = REQUIRED) String text,
            @Schema(requiredMode = REQUIRED) StepStatus status) {}

    record QuestionOption(
            @Schema(requiredMode = REQUIRED) String value,
            String label,
            String hint) {}
}
