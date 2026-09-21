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

    /**
     * 引用锁：在调用方事务里共享锁住目标单位行，供写路径在引用该单位前调用。
     *
     * 它替代已移除的外键父行锁：与 {@code UnitService} 删除单位时的全表 FOR UPDATE 互斥，
     * 「删除单位」与「写入用户」因此不会互相穿透。必须在写用例自己的事务里调用——
     * 事务外调用会立刻释放锁，等于没锁。
     *
     * 先按编码解析主键（一致性读，只用于取 id），再按主键取锁并回读最新行：
     * 锁定读看到的是最新已提交状态，取锁期间被并发删掉的单位会在这里变成 null。
     */
    @Transactional
    public UnitSummary lockByCode(String code) {
        UnitRow found = mapper.findByCode(code);
        if (found == null) {
            return null;
        }
        UnitRow locked = mapper.findByIdForShare(found.id());
        return locked == null || !found.code().equals(locked.code()) ? null : toSummary(locked);
    }

    private static UnitSummary toSummary(UnitRow row) {
        return new UnitSummary(row.code(), row.name(), row.status(), row.parentCode(), row.level());
    }
}
