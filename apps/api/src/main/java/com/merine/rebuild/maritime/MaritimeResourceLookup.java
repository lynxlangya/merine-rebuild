package com.merine.rebuild.maritime;

import com.merine.rebuild.common.PageResult;
import com.merine.rebuild.maritime.dto.UnitPoliceStation;
import com.merine.rebuild.maritime.persistence.MaritimeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 对系统单位/用户维护公开引用检查及单位所属派出所摘要；不依赖 MaritimeService。 */
@Service
public class MaritimeResourceLookup {
    private final MaritimeMapper mapper;
    public MaritimeResourceLookup(MaritimeMapper mapper) { this.mapper = mapper; }
    @Transactional
    public boolean unitHasStations(long id) { return !mapper.lockUnitStations(id).isEmpty(); }
    @Transactional
    public boolean userHasMembership(long id) { return !mapper.lockUserOfficers(id).isEmpty(); }
    @Transactional(readOnly = true)
    public PageResult<UnitPoliceStation> unitStations(String code, int page, int size) {
        MaritimeService.query(null,null,null,null,null,null,null,page,size);
        return new PageResult<>(mapper.unitStations(code,(long)(page-1)*size,size),mapper.countUnitStations(code),page,size);
    }
}
