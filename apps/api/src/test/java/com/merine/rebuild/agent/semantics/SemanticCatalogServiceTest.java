package com.merine.rebuild.agent.semantics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.merine.rebuild.agent.semantics.dto.SemanticCatalogView.DefinitionStatus;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 不启动 Spring/数据库；守住正式内容、启动失败和不可变目录边界。 */
class SemanticCatalogServiceTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private static final Resource CONTENT = new ClassPathResource("agent/semantics/catalog.json");

    @AfterAll
    static void closeValidator() { VALIDATORS.close(); }

    @Test
    void loadsOfficialContentAndPreservesConfirmedScope() {
        var catalog = load(CONTENT).catalog();
        assertThat(catalog.schemaVersion()).isEqualTo(1);
        assertThat(catalog.revision()).isEqualTo(3);
        assertThat(catalog.topics()).hasSize(1);
        var topic = catalog.topics().getFirst();
        assertThat(topic.concepts()).hasSize(4);
        var metric = topic.metrics().getFirst();
        assertThat(metric.id()).isEqualTo("task.completed-count");
        assertThat(metric.revision()).isEqualTo(3);
        assertThat(metric.status()).isEqualTo(DefinitionStatus.CONFIRMED);
        assertThat(metric.statusLabel()).isEqualTo("已确认");
        assertThat(metric.pendingQuestions()).isEmpty();
        assertThat(metric.scopeRule()).contains("当前登录用户所属单位发起", "后端登录身份", "任务查看权限与数据范围");
        assertThat(metric.timeBasis()).contains("确认总体办结", "UTC+8", "服务器当前时间", "至查询时刻", "最近一个月");
        assertThatThrownBy(() -> catalog.topics().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> topic.concepts().getFirst().rules().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> metric.pendingQuestions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsMissingBlankAndMalformedFiles() {
        assertThatThrownBy(() -> load(new ClassPathResource("agent/semantics/missing.json")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> load(resource(""))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> load(resource("{broken"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnknownSchemaAndFields() throws Exception {
        var changed = source();
        changed.put("schemaVersion", 2);
        assertRejected(changed);
        changed = source();
        changed.put("unknownField", "拼写错误不能被忽略");
        assertRejected(changed);
    }

    @Test
    void rejectsInvalidAndMissingNestedFields() throws Exception {
        var changed = source();
        metric(changed).put("title", " ");
        assertRejected(changed);
        changed = source();
        metric(changed).remove("countingObject");
        assertRejected(changed);
        changed = source();
        metric(changed).put("revision", 0);
        assertRejected(changed);
    }

    @Test
    void rejectsDuplicateIdentifiersAcrossDefinitionKinds() throws Exception {
        var changed = source();
        metric(changed).put("id", "task.whole-task");
        assertRejected(changed);
    }

    @Test
    void rejectsConfirmedDefinitionWithPendingQuestionsAndAllowsADraft() throws Exception {
        var changed = source();
        metric(changed).putArray("pendingQuestions").add("合成测试：统计范围尚待确认。");
        assertRejected(changed);
        metric(changed).put("status", "DRAFT").put("statusLabel", "待确认");
        metric(changed).put("scopeRule", "合成草稿：统计范围尚待确认。");
        assertThat(load(resource(JSON.writeValueAsString(changed))).catalog()
                .topics().getFirst().metrics().getFirst().status()).isEqualTo(DefinitionStatus.DRAFT);
    }

    @Test
    void queryImplementationRequiresMatchingConfirmedMetric() throws Exception {
        var lookup = new SemanticMetricLookup(load(CONTENT));
        assertThat(lookup.requireConfirmed("task.completed-count", 3).revision()).isEqualTo(3);
        assertThatThrownBy(() -> lookup.requireConfirmed("task.completed-count", 2))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> lookup.requireConfirmed("unknown.metric", 3))
                .isInstanceOf(IllegalStateException.class);
        var draft = source();
        metric(draft).put("status", "DRAFT").put("statusLabel", "待确认");
        metric(draft).putArray("pendingQuestions").add("合成待确认问题");
        var draftLookup = new SemanticMetricLookup(load(resource(JSON.writeValueAsString(draft))));
        assertThatThrownBy(() -> draftLookup.requireConfirmed("task.completed-count", 3))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsDraftWithoutPendingQuestionsAndMisleadingStatusLabel() throws Exception {
        var changed = source();
        metric(changed).put("status", "DRAFT").put("statusLabel", "待确认");
        assertRejected(changed);
        changed = source();
        metric(changed).put("statusLabel", "待确认");
        assertRejected(changed);
    }

    private static SemanticCatalogService load(Resource resource) {
        return new SemanticCatalogService(JSON, VALIDATORS.getValidator(), resource);
    }

    private static ObjectNode source() throws Exception {
        return (ObjectNode) JSON.readTree(CONTENT.getContentAsString(StandardCharsets.UTF_8));
    }

    private static ObjectNode metric(ObjectNode source) {
        return (ObjectNode) source.path("topics").get(0).path("metrics").get(0);
    }

    private static Resource resource(String content) {
        return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertRejected(ObjectNode source) {
        assertThatThrownBy(() -> load(resource(JSON.writeValueAsString(source))))
                .isInstanceOf(IllegalStateException.class);
    }
}
