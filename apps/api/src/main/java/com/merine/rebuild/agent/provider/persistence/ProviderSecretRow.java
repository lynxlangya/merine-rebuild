package com.merine.rebuild.agent.provider.persistence;

import com.merine.rebuild.agent.provider.dto.ProviderRequests.State;
import com.merine.rebuild.agent.provider.dto.ProviderRequests.Vendor;

/** 加密调用所需的连接投射：包含密文字段，只允许在 provider 模块内解密使用。 */
public record ProviderSecretRow(
        String id,
        Vendor vendor,
        String name,
        String baseUrl,
        String apiKeyCipher,
        State status) {}
