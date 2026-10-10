package com.merine.rebuild.agent.provider.persistence;

import com.merine.rebuild.agent.provider.dto.ProviderRequests.State;
import com.merine.rebuild.agent.provider.dto.ProviderRequests.Vendor;
import java.time.Instant;

/** 供应商连接表的 SQL 投射；模型列表由服务层按需补装，不在这里做聚合。 */
public record ProviderRow(
        String id,
        Vendor vendor,
        String name,
        String remark,
        String website,
        String baseUrl,
        State status,
        int version,
        Instant updatedAt) {}
