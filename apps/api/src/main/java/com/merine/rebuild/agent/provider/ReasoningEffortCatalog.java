package com.merine.rebuild.agent.provider;

import com.merine.rebuild.agent.provider.dto.ProviderRequests;
import com.merine.rebuild.agent.provider.dto.ProviderRequests.ReasoningEffort;
import static com.merine.rebuild.agent.provider.dto.ProviderRequests.ReasoningEffort.*;
import java.util.List;
import java.util.Locale;

/**
 * 逐模型的推理强度取值目录，整理自两家官方文档（2026-10-10 核对）。
 *
 * 只收录文档明确写出的取值：文档没写的返回空集，界面就不显示档位、也不发送该参数——
 * 上游对不认识的取值通常是静默忽略，填上「看起来能用」的值只会让人以为生效（实测确认过）。
 * 取值来源：DeepSeek《思考模式（Chat Completions）》与千问 AI 平台 Chat Completions API 参考
 * 的 reasoning_effort 一节（按系列列举）。
 */
final class ReasoningEffortCatalog {
    private ReasoningEffortCatalog() {}

    /** 该供应商是否已经有整理好的官方取值目录；没有目录时界面保留自行选择。 */
    static boolean covered(ProviderRequests.Vendor vendor) {
        return vendor == ProviderRequests.Vendor.DEEPSEEK || vendor == ProviderRequests.Vendor.QWEN;
    }

    /** 该模型官方支持的档位，按强度升序；未知模型返回空，表示不使用档位参数。 */
    static List<ReasoningEffort> effortsFor(ProviderRequests.Vendor vendor, String modelId) {
        if (vendor == null) return List.of();
        String id = modelId == null ? "" : modelId.trim().toLowerCase(Locale.ROOT);
        return switch (vendor) {
            // DeepSeek：reasoning_effort 取值 none/low/high/max，none 即关闭思考，默认 high。
            case DEEPSEEK -> List.of(NONE, LOW, HIGH, MAX);
            case QWEN -> qwen(id);
            default -> List.of();
        };
    }

    /** 官方默认档位；没有文档依据时返回 null。 */
    static ReasoningEffort defaultFor(ProviderRequests.Vendor vendor, String modelId) {
        List<ReasoningEffort> efforts = effortsFor(vendor, modelId);
        if (efforts.isEmpty()) return null;
        String id = modelId == null ? "" : modelId.trim().toLowerCase(Locale.ROOT);
        if (vendor == ProviderRequests.Vendor.QWEN) {
            if (isQwen38(id)) return XHIGH;                      // 千问默认 xhigh
            if (isGlm53(id) || id.equals("kimi-k3")) return MAX;  // 默认 max
            return HIGH;
        }
        return HIGH;                                             // DeepSeek 默认 high
    }

    /**
     * 千问 AI 平台的取值按系列区分：
     * Qwen3.8 系列 low/medium/xhigh；glm-5.3 与 kimi-k3（阿里云直供）low/high/max；
     * DeepSeek-V4 直供 high/max（带日期版本与 4.1-flash 为 low/high/max）。
     * 其余系列文档未给出档位，一律留空。
     */
    private static List<ReasoningEffort> qwen(String id) {
        if (isQwen38(id)) return List.of(LOW, MEDIUM, XHIGH);
        if (isGlm53(id)) return List.of(LOW, HIGH, MAX);
        if (id.equals("kimi-k3")) return List.of(LOW, HIGH, MAX);
        if (id.equals("kimi/kimi-k3")) return List.of(MAX);
        if (id.startsWith("glm-5.2") || id.startsWith("glm-5.1") || id.startsWith("glm-5")
                || id.startsWith("zhipu/glm-5")) {
            return List.of(HIGH, MAX);
        }
        if (id.startsWith("deepseek-v4.1-flash") || id.startsWith("deepseek-v4-flash-0731")
                || id.startsWith("deepseek-v4-pro-0813")) {
            return List.of(LOW, HIGH, MAX);
        }
        if (id.startsWith("deepseek-v4-pro") || id.startsWith("deepseek-v4-flash")) {
            return List.of(HIGH, MAX);
        }
        return List.of();
    }

    /** Qwen3.8 的对话模型；实时音视频等其它形态不在 Chat Completions 的档位范围内。 */
    private static boolean isQwen38(String id) {
        if (id.contains("realtime") || id.contains("livetranslate")) return false;
        return id.startsWith("qwen3.8-max") || id.startsWith("qwen3.8-flash")
                || id.equals("qwen3.8-27b") || id.startsWith("qwen3.8-omni-flash");
    }

    private static boolean isGlm53(String id) {
        return id.startsWith("glm-5.3") || id.startsWith("zhipu/glm-5.3");
    }
}
