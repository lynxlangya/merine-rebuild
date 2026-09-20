package com.merine.rebuild.system.role;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * system 模块对外的角色查询能力。
 * 本轮只读：角色定义的新建与修改还没有用例，因此不提供写命令。
 */
@Service
public class RoleLookup {
    private final RoleMapper mapper;

    public RoleLookup(RoleMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<RoleSummary> listAll() {
        return mapper.findAll();
    }

}
