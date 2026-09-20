package com.merine.rebuild.system.org;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * 单位树的节点。
 *
 * 直属下级数直接从 children 得出；用户数是直属用户数，不含后代单位用户，
 * 这样删除按钮和详情面板看到的是同一个口径。
 */
public record UnitTreeNode(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "单位状态：ENABLED 启用，DISABLED 停用")
        String status,
        @Schema(description = "上级单位编码；一级单位为 null", nullable = true)
        String parentCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "单位层级：1 总队，2 支队，3 大队")
        int level,
        @Schema(description = "行政区划代码；历史合成数据可能为 null", nullable = true)
        String areaCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "直属下级单位数")
        long childCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "直属用户数")
        long userCount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "编辑版本；保存时原样提交，冲突须重新读取")
        int version,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "最近修改时刻（UTC）")
        Instant updatedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "直属下级节点，按单位编码升序")
        List<UnitTreeNode> children) {
}
