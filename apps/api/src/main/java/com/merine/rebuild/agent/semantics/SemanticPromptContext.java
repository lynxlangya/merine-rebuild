package com.merine.rebuild.agent.semantics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 对对话模块公开固定业务资料的模型上下文；资料库与模型复用同一个已校验目录。
 * 不接收用户内容，不读取业务记录，不授予数据权限；调用方仍须校验对话使用权限。
 */
@Component
public final class SemanticPromptContext {
    private final String content;

    SemanticPromptContext(SemanticCatalogService catalog, ObjectMapper json,
                          @Value("classpath:prompts/agent-semantics.txt") Resource instructionsResource) {
        String instructions;
        try {
            instructions = instructionsResource.getContentAsString(StandardCharsets.UTF_8).strip();
        } catch (IOException error) {
            throw new IllegalStateException("无法读取海防助手业务语义使用说明", error);
        }
        if (instructions.isEmpty()) {
            throw new IllegalStateException("海防助手业务语义使用说明不能为空");
        }
        content = instructions + "\n\n<semantic_catalog>\n"
                + json.writeValueAsString(catalog.catalog()) + "\n</semantic_catalog>";
    }

    /** 启动时生成的不可变快照，含目录版本、定义编号及草稿状态。 */
    public String content() { return content; }
}
