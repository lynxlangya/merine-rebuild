package com.merine.rebuild.agent.chat.dto;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/** 对话运行模式：界面据此标注「本地演示」或真实模型选择器。 */
@Schema(name = "ChatRuntime")
public record ChatRuntimeView(
        @Schema(requiredMode = REQUIRED) ChatEvent.ExecutionMode executionMode) {}
