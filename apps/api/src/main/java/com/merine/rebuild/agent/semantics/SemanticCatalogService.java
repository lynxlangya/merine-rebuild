package com.merine.rebuild.agent.semantics;

import com.merine.rebuild.agent.semantics.dto.SemanticCatalogView;
import com.merine.rebuild.agent.semantics.dto.SemanticCatalogView.DefinitionStatus;
import jakarta.validation.Validator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

/** 启动时完整校验固定目录；读取请求不访问业务数据，也不执行统计。 */
@Service
final class SemanticCatalogService {
    private final SemanticCatalogView catalog;

    SemanticCatalogService(ObjectMapper json, Validator validator,
            @Value("classpath:agent/semantics/catalog.json") Resource resource) {
        try {
            catalog = json.readerFor(SemanticCatalogView.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(resource.getContentAsString(StandardCharsets.UTF_8));
            validate(catalog, validator);
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("无法加载海防助手业务定义，请检查固定目录文件", error);
        }
    }

    SemanticCatalogView catalog() { return catalog; }

    private static void validate(SemanticCatalogView catalog, Validator validator) {
        if (catalog == null) throw new IllegalArgumentException("业务定义目录不能为空");
        var violations = validator.validate(catalog);
        if (!violations.isEmpty()) {
            String fields = violations.stream().map(v -> v.getPropertyPath().toString())
                    .sorted().distinct().collect(java.util.stream.Collectors.joining(", "));
            throw new IllegalArgumentException("业务定义字段无效：" + fields);
        }
        if (catalog.schemaVersion() != 1) {
            throw new IllegalArgumentException("不支持的业务定义结构版本");
        }
        Set<String> identifiers = new HashSet<>();
        for (var topic : catalog.topics()) {
            unique(identifiers, topic.id());
            if (topic.concepts().isEmpty() && topic.metrics().isEmpty()) {
                throw new IllegalArgumentException("业务专题必须包含概念或指标");
            }
            for (var concept : topic.concepts()) unique(identifiers, concept.id());
            for (var metric : topic.metrics()) {
                unique(identifiers, metric.id());
                boolean confirmed = metric.status() == DefinitionStatus.CONFIRMED;
                if (confirmed != metric.pendingQuestions().isEmpty()) {
                    throw new IllegalArgumentException("指标确认状态与待确认问题不一致");
                }
                String label = confirmed ? "已确认" : "待确认";
                if (!label.equals(metric.statusLabel())) {
                    throw new IllegalArgumentException("指标确认状态与标签不一致");
                }
            }
        }
    }

    private static void unique(Set<String> identifiers, String id) {
        if (!identifiers.add(id)) throw new IllegalArgumentException("业务定义编号重复：" + id);
    }
}
