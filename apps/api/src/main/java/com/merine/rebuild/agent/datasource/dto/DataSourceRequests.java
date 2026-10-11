package com.merine.rebuild.agent.datasource.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class DataSourceRequests {
    private DataSourceRequests() {}

    /** 首版只支持 MySQL；连接串完全由服务端按该类型生成。 */
    public enum DbType { MYSQL }

    /** 配置启停状态；新建固定 DISABLED，只能通过启停接口改变。 */
    public enum State { ENABLED, DISABLED }

    /** 保存输入。密码是唯一写入型字段：新建必填；编辑留空（null）表示保留原值。
     * 允许查询的视图范围不在连接保存里修改，改由「查询范围」页整表替换。
     */
    @Schema(name = "SaveDataSource")
    public record Save(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 300) String remark,
            @NotNull DbType dbType,
            @NotBlank @Size(max = 253) String host,
            @NotNull @Min(1) @Max(65535) Integer port,
            @NotBlank @Size(max = 64) String databaseName,
            @NotBlank @Size(max = 64) String username,
            @Schema(accessMode = Schema.AccessMode.WRITE_ONLY,
                    description = "新建必填；编辑留空保留原密码。只保存到服务端密文，不回显")
            @Size(max = 128) String password,
            /** 兼容字段：不再写入白名单，允许范围改由「查询范围」页整表替换。 */
            @Deprecated
            @Schema(deprecated = true, description = "已忽略：连接保存不修改允许查询的视图，请使用查询范围页")
            @Size(max = 50) List<@NotBlank @Size(max = 64) String> views,
            @Min(0) Integer version) {
        @Override
        public String toString() {
            return "SaveDataSource[redacted]";
        }
    }

    /**
     * 未保存配置的连接测试：不落库，只做地址范围校验、连通性与只读授权判定，
     * 并返回该账号可 SELECT 的视图清单；带 views 时按勾选项判定 VIEW_DENIED（兼容旧客户端）。
     */
    @Schema(name = "CheckDataSourceDraft")
    public record CheckDraft(
            @Size(max = 80) String id,
            @NotNull DbType dbType,
            @NotBlank @Size(max = 253) String host,
            @NotNull @Min(1) @Max(65535) Integer port,
            @NotBlank @Size(max = 64) String databaseName,
            @NotBlank @Size(max = 64) String username,
            @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
            @Size(max = 128) String password,
            @Size(max = 50) List<@NotBlank @Size(max = 64) String> views) {
        @Override
        public String toString() {
            return "CheckDataSourceDraft[redacted]";
        }
    }

    /** 启停动作：启用会在服务端重新检查，不能复用页面上的旧结果。 */
    @Schema(name = "DataSourceStatusChange")
    public record StatusChange(
            @NotNull State status,
            @NotNull @Min(0) Integer version) {}
}
