package com.merine.rebuild.intelligence;

import com.merine.rebuild.intelligence.persistence.IntelligenceMapper;
import org.springframework.stereotype.Service;

/** 单位管理的显式读取能力。调用者持有单位锁；所有历史单位必在源头或范围/草稿目标中。 */
@Service
public class IntelligenceUnitUsageLookup {
    private final IntelligenceMapper mapper;
    public IntelligenceUnitUsageLookup(IntelligenceMapper mapper) {this.mapper=mapper;}
    public boolean hasReferences(long unitId) {
        return !mapper.unitReferences(unitId).isEmpty() || !mapper.scopeReferences(unitId).isEmpty()
                || !mapper.targetReferences(unitId).isEmpty();
    }
}
