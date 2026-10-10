package com.merine.rebuild.agent.provider.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class ProviderRequests {
    private ProviderRequests() {}
    public enum Vendor { DEEPSEEK, QWEN, ZHIPU, MOONSHOT, DOUBAO, BAIDU, TENCENT, MINIMAX, CUSTOM }
    public enum State { ENABLED, DISABLED }

    /**
     * 推理强度档位，按强度升序排列。取值是各供应商官方定义的并集：
     * DeepSeek 用 none/low/high/max，千问 AI 平台用 low/medium/xhigh 或 high/max 等；
     * NONE 表示关闭思考模式。向上游发送时按原样转小写，具体哪些档位可用由逐模型配置决定。
     */
    public enum ReasoningEffort { NONE, LOW, MEDIUM, HIGH, XHIGH, MAX }

    /**
     * 一个连接下的模型输入。{@code modelId} 是调用时原样发送的标识；
     * {@code displayName} 空白时由服务端回填为 {@code modelId}；列表顺序即展示顺序。
     */
    @Schema(name = "SaveProviderModel")
    public record ModelInput(
            @NotBlank @Size(max = 120) @Pattern(regexp = "[A-Za-z0-9._:/-]+",
                    message = "模型标识只能包含字母、数字与 . _ : / -") String modelId,
            @Size(max = 80) String displayName,
            @Size(max = 300) String remark,
            @Size(max = 4) List<ReasoningEffort> reasoningEfforts,
            @NotNull State status) {}

    /**
     * 拉取模型清单的输入。已保存的连接可以只给 {@code providerId}，用服务端保存的密钥；
     * 表单里刚输入的密钥优先，用于「还没保存就先看有哪些模型」的场景。
     */
    @Schema(name = "DiscoverProviderModels")
    public record Discover(
            @Size(max = 80) String providerId,
            /** 新建连接时用它决定推理强度目录；已保存连接以库里的供应商为准。 */
            Vendor vendor,
            @NotBlank @Size(max = 500) String baseUrl,
            @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
            @Size(max = 4096) String apiKey) {
        @Override public String toString() { return "DiscoverProviderModels[redacted]"; }
    }

    /** 批量查推理强度目录的输入：勾选或编辑模型时按当前模型标识问一次。 */
    @Schema(name = "ProviderEffortLookup")
    public record EffortLookup(
            @NotNull Vendor vendor,
            @Size(max = 20) List<@NotBlank @Size(max = 120) String> modelIds) {}

    @Schema(name = "SaveModelProvider")
    public record Save(
            @NotNull Vendor vendor,
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 300) String remark,
            @NotBlank @Size(max = 500) String website,
            @NotBlank @Size(max = 500) String baseUrl,
            @Schema(accessMode = Schema.AccessMode.WRITE_ONLY, description = "新建必填；编辑留空保留原密钥；更换供应商或地址时须重新输入")
            @Size(max = 4096) String apiKey,
            @NotNull State status,
            @Size(max = 20) @Valid List<ModelInput> models,
            @Min(0) Integer version) {
        @Override public String toString() { return "SaveModelProvider[redacted]"; }
    }
}
