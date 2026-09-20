package com.merine.rebuild.system.user.usage;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * system.user.usage 对单位模块公开的只读能力。
 *
 * 单位模块不该直接读 sys_user；需要判断“这个单位还有没有用户”时走这里，
 * 返回口径只有直属用户数，不携带账号或姓名。
 */
@Service
public class UserUnitUsageLookup {
    private final UserUnitUsageMapper mapper;

    public UserUnitUsageLookup(UserUnitUsageMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Map<Long, Long> countByUnitIds(Collection<Long> unitIds) {
        if (unitIds == null || unitIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new LinkedHashMap<>();
        for (UserUnitCount count : mapper.countByUnitIds(unitIds)) {
            result.put(count.unitId(), count.userCount());
        }
        return result;
    }
}
