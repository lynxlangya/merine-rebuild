package com.merine.rebuild.agent.provider;

import com.merine.rebuild.agent.provider.dto.ProviderRequests.ReasoningEffort;

/**
 * 对 agent 对话模块公开的最小能力：按「连接 + 模型标识」取一条可调用的连接。
 * 校验与解密留在 provider 模块内；调用方拿到的是可直接用于出站请求的目标，不感知密文。
 */
public interface ProviderTargetLookup {

    /**
     * 取启用连接下的启用模型；连接停用、模型不存在或停用、密钥不可解密、
     * 推理强度不在该模型允许集合内，都抛可预期的业务异常。
     */
    ProviderTarget requireEnabled(String providerId, String modelId,
                                  ReasoningEffort reasoningEffort);
}
