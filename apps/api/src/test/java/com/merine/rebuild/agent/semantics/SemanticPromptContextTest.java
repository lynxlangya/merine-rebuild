package com.merine.rebuild.agent.semantics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 不调用模型：验证完整目录、草稿状态及使用说明缺失时拒绝启动。 */
class SemanticPromptContextTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private static final Resource CATALOG = new ClassPathResource("agent/semantics/catalog.json");
    private static final Resource INSTRUCTIONS = new ClassPathResource("prompts/agent-semantics.txt");

    @AfterAll
    static void closeValidator() { VALIDATORS.close(); }

    @Test
    void includesTheWholeCatalogWithConfirmedScope() throws IOException {
        String context = load(INSTRUCTIONS).content();
        assertThat(context).startsWith(INSTRUCTIONS.getContentAsString(StandardCharsets.UTF_8).strip());
        var catalog = catalogFromContext(context);
        assertThat(catalog).isEqualTo(JSON.readTree(CATALOG.getContentAsString(StandardCharsets.UTF_8)));
        var metric = catalog.path("topics").get(0).path("metrics").get(0);
        assertThat(metric.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(metric.path("revision").asInt()).isEqualTo(3);
        assertThat(metric.path("pendingQuestions").size()).isZero();
    }

    @Test
    void preservesDraftStatusForUnconfirmedDefinitions() throws IOException {
        var source = (ObjectNode) JSON.readTree(CATALOG.getContentAsString(StandardCharsets.UTF_8));
        var metric = (ObjectNode) source.path("topics").get(0).path("metrics").get(0);
        metric.put("status", "DRAFT").put("statusLabel", "待确认")
                .put("scopeRule", "合成草稿：统计范围尚待确认。");
        metric.putArray("pendingQuestions").add("合成测试：统计范围尚待确认。");
        var draft = new SemanticCatalogService(JSON, VALIDATORS.getValidator(),
                new ByteArrayResource(JSON.writeValueAsBytes(source)));
        String context = new SemanticPromptContext(draft, JSON, INSTRUCTIONS).content();
        assertThat(catalogFromContext(context)).isEqualTo(source);
    }

    @Test
    void rejectsMissingInstructionsAtStartup() {
        assertThatThrownBy(() -> load(new ClassPathResource("prompts/missing-agent-semantics.txt")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("无法读取海防助手业务语义使用说明")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void rejectsBlankInstructionsAtStartup() {
        var blank = new ByteArrayResource(" \n\t ".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> load(blank))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("海防助手业务语义使用说明不能为空");
    }

    private static SemanticPromptContext load(Resource instructions) {
        var catalog = new SemanticCatalogService(JSON, VALIDATORS.getValidator(), CATALOG);
        return new SemanticPromptContext(catalog, JSON, instructions);
    }

    private static JsonNode catalogFromContext(String context) {
        String payload = context.substring(context.indexOf("<semantic_catalog>\n")
                + "<semantic_catalog>\n".length(), context.lastIndexOf("\n</semantic_catalog>"));
        return JSON.readTree(payload);
    }
}
