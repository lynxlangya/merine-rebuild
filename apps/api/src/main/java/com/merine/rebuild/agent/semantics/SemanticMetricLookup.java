package com.merine.rebuild.agent.semantics;

import com.merine.rebuild.agent.semantics.dto.SemanticCatalogView.DefinitionStatus;
import com.merine.rebuild.agent.semantics.dto.SemanticCatalogView.Metric;
import org.springframework.stereotype.Component;

/** 对查询工具公开正式定义确认能力；实现版本与资料库版本不匹配时禁止启动旧算法。 */
@Component
public final class SemanticMetricLookup {
    private final SemanticCatalogService catalog;

    SemanticMetricLookup(SemanticCatalogService catalog) { this.catalog = catalog; }

    public Metric requireConfirmed(String id, int implementedRevision) {
        Metric metric = catalog.catalog().topics().stream().flatMap(topic -> topic.metrics().stream())
                .filter(candidate -> candidate.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("查询工具对应的指标定义不存在"));
        if (metric.status() != DefinitionStatus.CONFIRMED || metric.revision() != implementedRevision) {
            throw new IllegalStateException("查询实现与已确认指标版本不一致，请先核对业务口径");
        }
        return metric;
    }
}
