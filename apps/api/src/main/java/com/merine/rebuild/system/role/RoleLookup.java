package com.merine.rebuild.system.role;

import com.merine.rebuild.system.role.dto.RoleSummary;
import com.merine.rebuild.system.role.persistence.RoleMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * system.role 对外的角色选项查询能力。
 *
 * 用户管理的筛选与表单选择走这里；角色定义的维护由 {@link RoleService} 负责，
 * 两者共用同一张表和同一份映射，但不互相回调。
 */
@Service
public class RoleLookup {
    private final RoleMapper mapper;

    public RoleLookup(RoleMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<RoleSummary> listAll() {
        return mapper.findAllSummaries();
    }

}
