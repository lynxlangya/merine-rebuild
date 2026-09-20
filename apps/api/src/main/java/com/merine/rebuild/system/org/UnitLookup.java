package com.merine.rebuild.system.org;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * system 模块对外的单位查询能力。
 * 本轮只读：单位的新建与修改还没有用例，因此不提供写命令。
 */
@Service
public class UnitLookup {
    private final UnitMapper mapper;

    public UnitLookup(UnitMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<UnitSummary> listAll() {
        return mapper.findAll();
    }

    @Transactional(readOnly = true)
    public UnitSummary findByCode(String code) {
        return mapper.findByCode(code);
    }
}
