package com.merine.rebuild.agent.datasource.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 查询范围管理输入：范围与说明分开保存，避免只改说明也影响启停状态。 */
public final class QueryScopeRequests {
    private QueryScopeRequests() {}

    /** 白名单整体替换：带上读到的配置版本；集合实际变化会使检查失效并停用数据源。 */
    @Schema(name = "ReplaceQueryScopeViews")
    public record ReplaceViews(
            @NotNull @Min(0) Integer version,
            @NotNull @Size(max = 50) List<@NotBlank @Size(max = 64) String> views) {}

    /** 单个字段的说明覆盖值；空串表示取消覆盖，回退数据库列注释。 */
    @Schema(name = "QueryScopeColumn")
    public record ColumnDescription(
            @NotBlank @Size(max = 64) String name,
            @Size(max = 300) String description) {}

    /**
     * 指定视图的说明覆盖段整体替换：displayName/description 为空表示回退默认；
     * columns 是完整覆盖集合，未提交的旧字段覆盖会被清除。
     */
    @Schema(name = "SaveQueryScopeMetadata")
    public record SaveMetadata(
            @NotNull @Min(0) Integer version,
            @NotNull @Min(0) Integer semanticVersion,
            @NotBlank @Size(max = 64) String view,
            @Size(max = 80) String displayName,
            @Size(max = 2000) String description,
            @Size(max = 100) List<@Valid ColumnDescription> columns) {}
}
