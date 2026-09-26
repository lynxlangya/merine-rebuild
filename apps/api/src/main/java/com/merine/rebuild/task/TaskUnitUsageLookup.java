package com.merine.rebuild.task;

import com.merine.rebuild.task.persistence.TaskMapper;
import org.springframework.stereotype.Service;

/** 供单位管理在持有单位锁时校验任务引用；不暴露任务表的写入能力。 */
@Service
public class TaskUnitUsageLookup {
    private final TaskMapper mapper;
    public TaskUnitUsageLookup(TaskMapper mapper) { this.mapper = mapper; }
    public boolean hasReferences(long unitId) { return mapper.unitActiveReferences(unitId) > 0; }
}
