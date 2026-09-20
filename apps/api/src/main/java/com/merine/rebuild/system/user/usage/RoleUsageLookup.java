package com.merine.rebuild.system.user.usage;

import com.merine.rebuild.system.user.usage.persistence.RoleUsageMapper;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * system.user.usage 对角色模块公开的只读能力。
 *
 * 角色的使用情况（成员数、成员清单、删除前引用检查）由用户模块回答：
 * sys_user_role 的写入 owner 是 user，role 模块不直接读它。
 */
@Service
public class RoleUsageLookup {

    private final RoleUsageMapper mapper;

    public RoleUsageLookup(RoleUsageMapper mapper) {
        this.mapper = mapper;
    }

    /** 批量统计各角色的成员数；没有成员的编码不会出现在结果里。 */
    @Transactional(readOnly = true)
    public Map<String, Long> countHoldersByRoleCodes(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> result = new LinkedHashMap<>();
        for (RoleHolderCount count : mapper.countHoldersByRoleCodes(roleCodes)) {
            result.put(count.roleCode(), count.holderCount());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public long countHolders(String roleCode) {
        return mapper.countHolders(roleCode);
    }

    @Transactional(readOnly = true)
    public List<RoleHolder> findHolders(String roleCode, long offset, int limit) {
        return mapper.findHolders(roleCode, offset, limit);
    }
}
