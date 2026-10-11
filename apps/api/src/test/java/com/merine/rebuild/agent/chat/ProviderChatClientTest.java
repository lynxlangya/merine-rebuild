package com.merine.rebuild.agent.chat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

/** 系统规则缺失时不能悄悄启动一个不带约束的模型客户端。 */
class ProviderChatClientTest {

    @Test
    void rejectsMissingSystemPromptAtStartup() {
        assertThatThrownBy(() -> ProviderChatClient.readSystemPrompt(
                new ClassPathResource("prompts/missing-agent-system.txt")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("无法读取海防助手系统提示词")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void rejectsBlankSystemPromptAtStartup() {
        var blank = new ByteArrayResource(" \n\t ".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> ProviderChatClient.readSystemPrompt(blank))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("海防助手系统提示词不能为空");
    }
}
