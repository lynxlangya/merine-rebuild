package com.merine.rebuild.system.unit;

import com.merine.rebuild.system.unit.dto.UnitSummary;
import com.merine.rebuild.system.unit.persistence.UnitMapper;
import com.merine.rebuild.system.unit.persistence.UnitRow;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * system.unit 对外的只读单位选项；组织树维护由内部 UnitService 负责。
 */
@Service
public class UnitLookup {
    private final UnitMapper mapper;

    public UnitLookup(UnitMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<UnitSummary> listAll() {
        return mapper.findAll().stream()
                .map(UnitLookup::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public UnitSummary findByCode(String code) {
        UnitRow row = mapper.findByCode(code);
        return row == null ? null : toSummary(row);
    }

    private static UnitSummary toSummary(UnitRow row) {
        return new UnitSummary(row.code(), row.name(), row.status(), row.parentCode(), row.level());
    }
}
