package com.merine.rebuild.agent.chat;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 对话的边界与行为配置。默认值即当前冻结的上限，全部由服务端执行；
 * 任何字段被调大都会影响内存与并发占用，改动必须同时更新边界测试。
 *
 * <p>{@code mode} 决定执行者（真实模型或本地替身），其余上限对两种模式一致。
 */
@ConfigurationProperties(prefix = "merine.agent.chat")
public record ChatProperties(
        @DefaultValue("PROVIDER") ChatMode mode,
        @DefaultValue("40") int maxMessages,
        @DefaultValue("8000") int maxMessageChars,
        @DefaultValue("128000") int maxTotalChars,
        @DefaultValue("32") int maxPartsPerMessage,
        @DefaultValue("100") int maxTableRows,
        @DefaultValue("12") int maxTableColumns,
        @DefaultValue("200") int maxTableCellChars,
        @DefaultValue("12") int maxQuestionOptions,
        @DefaultValue("8") int maxAnswerValues,
        @DefaultValue("500") int maxAnswerTextChars,
        @DefaultValue("1") int maxActivePerUser,
        @DefaultValue("20") int maxActiveGlobal,
        @DefaultValue("200") int maxRecords,
        @DefaultValue("30m") Duration recordTtl,
        @DefaultValue("5m") Duration runTimeout,
        @DefaultValue("15s") Duration heartbeatInterval,
        /** 是否请求上游在流式响应里返回 token 用量（DeepSeek 与千问官方都支持，默认开启）。 */
        @DefaultValue("true") boolean requestUsage) {

    /** PROVIDER = 调真实模型；DEMO = 本地脚本替身（仅 dev/test 注册）。不提供自动回退。 */
    public enum ChatMode { PROVIDER, DEMO }
}
