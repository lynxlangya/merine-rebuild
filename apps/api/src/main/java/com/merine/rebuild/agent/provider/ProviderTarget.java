package com.merine.rebuild.agent.provider;

import com.merine.rebuild.agent.provider.dto.ProviderRequests.ReasoningEffort;

/**
 * 对话调用所需的连接与密钥：providerId、连接名、模型标识、基础地址与解密后的 API Key。
 * 只在服务端内存中流动，不进入任何 HTTP 响应、日志或前端契约。
 */
public record ProviderTarget(
        String providerId,
        String providerName,
        String modelId,
        String baseUrl,
        String apiKey,
        /** 已按模型配置校验过的推理强度；null 表示不发送该参数，交给供应商默认值。 */
        ReasoningEffort reasoningEffort) {}
