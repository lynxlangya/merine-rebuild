package com.merine.rebuild.maritime;

import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.system.audit.AuditEvent;
import com.merine.rebuild.system.audit.AuditTrail;
import com.merine.rebuild.common.PageResult;
import com.merine.rebuild.maritime.dto.*;
import com.merine.rebuild.maritime.persistence.*;
import java.util.*;
import com.merine.rebuild.system.unit.UnitLookup;
import com.merine.rebuild.system.user.usage.UserDirectoryLookup;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 功能权限下统一可见。关联写入锁顺序：单位 → 用户 → 所 → 民警 → 港口 → 码头，同类按主键升序。 */
@Service
public class MaritimeService {
    private final MaritimeMapper mapper;
    private final UnitLookup units;
    private final UserDirectoryLookup users;
    private final AuditTrail audit;
    public MaritimeService(MaritimeMapper mapper, UnitLookup units, UserDirectoryLookup users,
                           AuditTrail audit) {
        this.mapper = mapper; this.units = units; this.users = users; this.audit = audit;
    }

    /**
     * 档案类操作统一留痕：模块 maritime，动作 `<资源>:<增改删>`，对象带类型与名称快照。
     * 涉海档案没有历史表，审计是唯一的追溯依据，所以删除要在删除前先取名称。
     */
    private void auditArchive(String resource, String targetType, String verb, String id, String name) {
        String action = resource + ":" + switch (verb) {
            case "新增" -> "create";
            case "修改" -> "update";
            default -> "delete";
        };
        audit.recordCurrent(AuditEvent.succeeded("maritime", action, targetType, id, name,
                "%s%s「%s」".formatted(verb, typeLabel(targetType), name)));
    }

    private static String typeLabel(String targetType) {
        return switch (targetType) {
            case "PORT" -> "港口";
            case "WHARF" -> "码头";
            case "ANCHORAGE" -> "锚地";
            case "ISLAND" -> "海岛";
            case "POLICE_STATION" -> "派出所";
            case "PORT_OFFICER" -> "民警";
            default -> "档案";
        };
    }

    /** 快照仅定位锁；全部引用在取锁后用最新事实重新核对，变更归属则返回冲突。 */
    private void lockOwners(Collection<Long> stationIds, Collection<Long> userIds, Long extraUnit) {
        Map<Long,Long> stationUnits = new TreeMap<>();
        Map<Long,Long> userUnits = new TreeMap<>();
        SortedSet<Long> unitIds = new TreeSet<>();
        if (extraUnit != null) unitIds.add(extraUnit);
        for (Long id : stationIds) if (id != null) {
            ArchiveRow row = required(mapper.findPoliceStation(id));
            stationUnits.put(id,row.unitId); unitIds.add(row.unitId);
        }
        for (Long id : userIds) if (id != null) {
            var person = users.find(id);
            if (person == null) invalid("所选用户不存在");
            userUnits.put(id,person.unitId()); unitIds.add(person.unitId());
        }
        for (long id : unitIds) if (units.lockReference(id) == null) invalid("所属单位不存在");
        for (var entry : userUnits.entrySet()) {
            var person = users.lock(entry.getKey());
            if (person == null || person.unitId() != entry.getValue()) conflict("用户归属已调整，请刷新后重新选择");
        }
        for (var entry : stationUnits.entrySet()) {
            ArchiveRow row = required(mapper.lockPoliceStation(entry.getKey()));
            if (!Objects.equals(row.unitId,entry.getValue())) conflict("派出所归属已调整，请刷新后重新选择");
        }
    }
    private UnitLookup.Reference stationUnit(String code) {
        var snapshot = units.referenceByCode(code);
        if (snapshot == null) invalid("所选单位不存在");
        return snapshot;
    }
    private void validateStationUnit(UnitLookup.Reference unit, boolean changed) {
        if (unit == null) invalid("所属单位不存在");
        if (unit.level() < 2 || unit.level() > 3) invalid("派出所必须属于支队或大队");
        if (changed && !"ENABLED".equals(unit.status())) invalid("所属单位已停用，不能新增关联");
    }
    private void validateMember(ArchiveRow row, ArchiveRow before) {
        ArchiveRow station = relation(mapper.lockPoliceStation(row.policeStationId), before == null ? null : before.policeStationId, row.policeStationId, "所属派出所");
        var person = users.lock(row.userId);
        if (person == null || person.unitId() != station.unitId) invalid("只能选择派出所所属单位的用户");
        boolean newAssociation = before == null || !Objects.equals(before.policeStationId,row.policeStationId) || !Objects.equals(before.status,row.status) && "ENABLED".equals(row.status);
        if (newAssociation && !"ENABLED".equals(person.status())) invalid("账号已停用，不能新增或启用成员关联");
        if (newAssociation && !"ENABLED".equals(units.lockReference(station.unitId).status())) invalid("所属单位已停用");
    }

    public static ArchiveQuery query(String keyword, String region, String status, Long portId,
            Long policeStationId, Long responsibleOfficerId, String inhabitationType, int page, int pageSize) {
        if (page < 1 || page > 1000000 || pageSize < 1 || pageSize > 100) invalid("分页参数超出范围");
        if (keyword != null && keyword.length() > 120 || region != null && region.length() > 120) invalid("查询条件最多120个字符");
        if (status != null && !Set.of("ENABLED", "DISABLED").contains(status)) invalid("状态无效");
        if (inhabitationType != null && !Set.of("INHABITED", "UNINHABITED").contains(inhabitationType)) invalid("居住类型无效");
        for (Long id : Arrays.asList(portId, policeStationId, responsibleOfficerId)) if (id != null && id <= 0) invalid("关联标识无效");
        return new ArchiveQuery(likeText(keyword), likeText(region), text(status), portId, policeStationId, responsibleOfficerId, inhabitationType, page, pageSize);
    }

    private static String text(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private static String likeText(String value) {
        value = text(value);
        return value == null ? null : value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
    private static String id(Long value) { return value == null ? null : value.toString(); }
    private static Long reference(String value) {
        if (value == null) return null;
        try { long id = Long.parseLong(value); if (id <= 0) invalid("关联标识无效"); return id; }
        catch (NumberFormatException error) { invalid("关联标识超出范围"); return null; }
    }
    private static void invalid(String message) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARCHIVE", message); }
    private static void conflict(String message) { throw new ApiException(HttpStatus.CONFLICT, "ARCHIVE_CONFLICT", message); }
    private static ArchiveRow required(ArchiveRow row) {
        if (row == null) throw new ApiException(HttpStatus.NOT_FOUND, "ARCHIVE_NOT_FOUND", "档案不存在或已删除");
        return row;
    }
    private static void version(ArchiveRow row, Integer version) {
        required(row);
        if (version == null || version < 0) invalid("编辑或删除必须提供有效版本");
        if (!Objects.equals(row.version, version)) conflict("档案已被修改，请刷新后重试");
    }
    private static ArchiveRow relation(ArchiveRow row, Long oldId, Long newId, String label) {
        if (row == null) invalid(label + "不存在或已删除");
        if (!Objects.equals(oldId, newId) && !"ENABLED".equals(row.status)) invalid(label + "已停用，不能新增关联");
        return row;
    }
    private static SortedSet<Long> ids(Long... values) {
        SortedSet<Long> result = new TreeSet<>();
        for (Long value : values) if (value != null) result.add(value);
        return result;
    }
    private void lockOfficers(Long... values) { for (long id : ids(values)) mapper.lockPortOfficer(id); }
    private void lockPorts(Long... values) { for (long id : ids(values)) mapper.lockPort(id); }

    private void lockWharfReferences(ArchiveRow before, ArchiveRow next) {
        List<Long> stationIds = Arrays.asList(before == null ? null : before.policeStationId,next.policeStationId);
        List<Long> userIds = new ArrayList<>();
        for (long officerId : ids(before == null ? null : before.responsibleOfficerId,next.responsibleOfficerId))
            userIds.add(required(mapper.findPortOfficer(officerId)).userId);
        lockOwners(stationIds,userIds,null);
        lockOfficers(before == null ? null : before.responsibleOfficerId, next.responsibleOfficerId);
        lockPorts(before == null ? null : before.portId, next.portId);
    }
    private void validateWharf(ArchiveRow before, ArchiveRow next) {
        if (next.portId != null) relation(mapper.lockPort(next.portId), before == null ? null : before.portId, next.portId, "所属港口");
        if (next.policeStationId != null) relation(mapper.lockPoliceStation(next.policeStationId), before == null ? null : before.policeStationId, next.policeStationId, "管辖派出所");
        if (next.responsibleOfficerId != null) {
            if (next.policeStationId == null) invalid("选择责任民警前须选择管辖派出所");
            ArchiveRow officer = relation(mapper.lockPortOfficer(next.responsibleOfficerId), before == null ? null : before.responsibleOfficerId, next.responsibleOfficerId, "责任民警");
            if ((before == null || !Objects.equals(before.responsibleOfficerId,next.responsibleOfficerId)) && !"ENABLED".equals(users.lock(officer.userId).status())) invalid("责任民警账号已停用，不能新增责任关联");
            if (!Objects.equals(officer.policeStationId, next.policeStationId)) invalid("责任民警必须属于管辖派出所；更换派出所后请清除或重新选择民警");
        }
    }

    private static PortView viewPort(ArchiveRow row) {
        required(row);
        return new PortView(
                id(row.id),
                row.name,
                row.region,
                row.location,
                row.status,
                row.purpose,
                row.version,
                row.createdAt,
                row.updatedAt,
                row.fixtureKey);
    }
    private static ArchiveRow rowPort(WritePort input) {
        ArchiveRow row = new ArchiveRow();
        row.name = text(input.name());
        row.region = text(input.region());
        row.location = text(input.location());
        row.status = text(input.status());
        row.purpose = text(input.purpose());
        return row;
    }
    @Transactional(readOnly = true)
    public PageResult<PortView> listPort(ArchiveQuery query) {
        return new PageResult<>(mapper.listPort(query).stream().map(MaritimeService::viewPort).toList(), mapper.countPort(query), query.page(), query.pageSize());
    }
    @Transactional(readOnly = true)
    public PortView getPort(long id) { return viewPort(mapper.findPort(id)); }
    @Transactional
    public PortView createPort(WritePort input, String fixtureKey) {
        ArchiveRow row = rowPort(input);
        row.fixtureKey = fixtureKey;
        mapper.insertPort(row);
        var created = viewPort(mapper.findPort(row.id));
        auditArchive("port", "PORT", "新增", created.id(), created.name());
        return created;
    }
    @Transactional
    public PortView updatePort(long id, WritePort input) {
        ArchiveRow row = rowPort(input);
        ArchiveRow before = required(mapper.lockPort(id));
        version(before, input.version());
        row.id = id; row.version = input.version();
        if (mapper.updatePort(row) != 1) conflict("档案已被修改，请刷新后重试");
        var updated = viewPort(mapper.findPort(id));
        auditArchive("port", "PORT", "修改", updated.id(), updated.name());
        return updated;
    }
    @Transactional
    public void deletePort(long id, int inputVersion) {
        ArchiveRow row = required(mapper.lockPort(id));
        version(row, inputVersion);
        if (!mapper.lockPortWharfs(id).isEmpty()) conflict("该港口仍有下属码头，不能删除；可先解除关联或停用");
        if (mapper.deletePort(id, inputVersion) != 1) conflict("档案已被修改，请刷新后重试");
        auditArchive("port", "PORT", "删除", id(row.id), row.name);
    }

    private static WharfView viewWharf(ArchiveRow row) {
        required(row);
        return new WharfView(
                id(row.id),
                row.name,
                row.region,
                row.location,
                row.status,
                row.purpose,
                id(row.portId),
                id(row.policeStationId),
                id(row.responsibleOfficerId),
                row.version,
                row.createdAt,
                row.updatedAt,
                row.fixtureKey,
                row.portName,
                row.portStatus,
                row.policeStationName,
                row.policeStationStatus,
                row.responsibleOfficerName,
                row.responsibleOfficerStatus);
    }
    private static ArchiveRow rowWharf(WriteWharf input) {
        ArchiveRow row = new ArchiveRow();
        row.name = text(input.name());
        row.region = text(input.region());
        row.location = text(input.location());
        row.status = text(input.status());
        row.purpose = text(input.purpose());
        row.portId = reference(input.portId());
        row.policeStationId = reference(input.policeStationId());
        row.responsibleOfficerId = reference(input.responsibleOfficerId());
        return row;
    }
    @Transactional(readOnly = true)
    public PageResult<WharfView> listWharf(ArchiveQuery query) {
        return new PageResult<>(mapper.listWharf(query).stream().map(MaritimeService::viewWharf).toList(), mapper.countWharf(query), query.page(), query.pageSize());
    }
    @Transactional(readOnly = true)
    public WharfView getWharf(long id) { return viewWharf(mapper.findWharf(id)); }
    @Transactional
    public WharfView createWharf(WriteWharf input, String fixtureKey) {
        ArchiveRow row = rowWharf(input);
        row.fixtureKey = fixtureKey;
        lockWharfReferences(null, row);
        validateWharf(null, row);
        mapper.insertWharf(row);
        var created = viewWharf(mapper.findWharf(row.id));
        auditArchive("wharf", "WHARF", "新增", created.id(), created.name());
        return created;
    }
    @Transactional
    public WharfView updateWharf(long id, WriteWharf input) {
        ArchiveRow row = rowWharf(input);
        ArchiveRow snapshot = required(mapper.findWharf(id));
        lockWharfReferences(snapshot, row);
        ArchiveRow before = required(mapper.lockWharf(id));
        version(before, input.version());
        validateWharf(before, row);
        row.id = id; row.version = input.version();
        if (mapper.updateWharf(row) != 1) conflict("档案已被修改，请刷新后重试");
        var updated = viewWharf(mapper.findWharf(id));
        auditArchive("wharf", "WHARF", "修改", updated.id(), updated.name());
        return updated;
    }
    @Transactional
    public void deleteWharf(long id, int inputVersion) {
        ArchiveRow row = required(mapper.lockWharf(id));
        version(row, inputVersion);
        if (mapper.deleteWharf(id, inputVersion) != 1) conflict("档案已被修改，请刷新后重试");
        auditArchive("wharf", "WHARF", "删除", id(row.id), row.name);
    }

    private static AnchorageView viewAnchorage(ArchiveRow row) {
        required(row);
        return new AnchorageView(
                id(row.id),
                row.name,
                row.region,
                row.location,
                row.status,
                row.purpose,
                row.version,
                row.createdAt,
                row.updatedAt,
                row.fixtureKey);
    }
    private static ArchiveRow rowAnchorage(WriteAnchorage input) {
        ArchiveRow row = new ArchiveRow();
        row.name = text(input.name());
        row.region = text(input.region());
        row.location = text(input.location());
        row.status = text(input.status());
        row.purpose = text(input.purpose());
        return row;
    }
    @Transactional(readOnly = true)
    public PageResult<AnchorageView> listAnchorage(ArchiveQuery query) {
        return new PageResult<>(mapper.listAnchorage(query).stream().map(MaritimeService::viewAnchorage).toList(), mapper.countAnchorage(query), query.page(), query.pageSize());
    }
    @Transactional(readOnly = true)
    public AnchorageView getAnchorage(long id) { return viewAnchorage(mapper.findAnchorage(id)); }
    @Transactional
    public AnchorageView createAnchorage(WriteAnchorage input, String fixtureKey) {
        ArchiveRow row = rowAnchorage(input);
        row.fixtureKey = fixtureKey;
        mapper.insertAnchorage(row);
        var created = viewAnchorage(mapper.findAnchorage(row.id));
        auditArchive("anchorage", "ANCHORAGE", "新增", created.id(), created.name());
        return created;
    }
    @Transactional
    public AnchorageView updateAnchorage(long id, WriteAnchorage input) {
        ArchiveRow row = rowAnchorage(input);
        ArchiveRow before = required(mapper.lockAnchorage(id));
        version(before, input.version());
        row.id = id; row.version = input.version();
        if (mapper.updateAnchorage(row) != 1) conflict("档案已被修改，请刷新后重试");
        var updated = viewAnchorage(mapper.findAnchorage(id));
        auditArchive("anchorage", "ANCHORAGE", "修改", updated.id(), updated.name());
        return updated;
    }
    @Transactional
    public void deleteAnchorage(long id, int inputVersion) {
        ArchiveRow row = required(mapper.lockAnchorage(id));
        version(row, inputVersion);
        if (mapper.deleteAnchorage(id, inputVersion) != 1) conflict("档案已被修改，请刷新后重试");
        auditArchive("anchorage", "ANCHORAGE", "删除", id(row.id), row.name);
    }

    private static IslandView viewIsland(ArchiveRow row) {
        required(row);
        return new IslandView(
                id(row.id),
                row.name,
                row.region,
                row.location,
                row.status,
                row.inhabitationType,
                row.version,
                row.createdAt,
                row.updatedAt,
                row.fixtureKey);
    }
    private static ArchiveRow rowIsland(WriteIsland input) {
        ArchiveRow row = new ArchiveRow();
        row.name = text(input.name());
        row.region = text(input.region());
        row.location = text(input.location());
        row.status = text(input.status());
        row.inhabitationType = text(input.inhabitationType());
        return row;
    }
    @Transactional(readOnly = true)
    public PageResult<IslandView> listIsland(ArchiveQuery query) {
        return new PageResult<>(mapper.listIsland(query).stream().map(MaritimeService::viewIsland).toList(), mapper.countIsland(query), query.page(), query.pageSize());
    }
    @Transactional(readOnly = true)
    public IslandView getIsland(long id) { return viewIsland(mapper.findIsland(id)); }
    @Transactional
    public IslandView createIsland(WriteIsland input, String fixtureKey) {
        ArchiveRow row = rowIsland(input);
        row.fixtureKey = fixtureKey;
        mapper.insertIsland(row);
        var created = viewIsland(mapper.findIsland(row.id));
        auditArchive("island", "ISLAND", "新增", created.id(), created.name());
        return created;
    }
    @Transactional
    public IslandView updateIsland(long id, WriteIsland input) {
        ArchiveRow row = rowIsland(input);
        ArchiveRow before = required(mapper.lockIsland(id));
        version(before, input.version());
        row.id = id; row.version = input.version();
        if (mapper.updateIsland(row) != 1) conflict("档案已被修改，请刷新后重试");
        var updated = viewIsland(mapper.findIsland(id));
        auditArchive("island", "ISLAND", "修改", updated.id(), updated.name());
        return updated;
    }
    @Transactional
    public void deleteIsland(long id, int inputVersion) {
        ArchiveRow row = required(mapper.lockIsland(id));
        version(row, inputVersion);
        if (mapper.deleteIsland(id, inputVersion) != 1) conflict("档案已被修改，请刷新后重试");
        auditArchive("island", "ISLAND", "删除", id(row.id), row.name);
    }

    private static PoliceStationView viewPoliceStation(ArchiveRow row) {
        required(row);
        return new PoliceStationView(
                id(row.id),
                row.name,
                row.unitCode,
                row.unitName,
                row.unitLevel,
                row.region,
                row.location,
                row.status,
                row.version,
                row.createdAt,
                row.updatedAt,
                row.fixtureKey);
    }
    private static ArchiveRow rowPoliceStation(WritePoliceStation input) {
        ArchiveRow row = new ArchiveRow();
        row.name = text(input.name());
        row.region = text(input.region());
        row.location = text(input.location());
        row.status = text(input.status());
        return row;
    }
    @Transactional(readOnly = true)
    public PageResult<PoliceStationView> listPoliceStation(ArchiveQuery query) {
        return new PageResult<>(mapper.listPoliceStation(query).stream().map(MaritimeService::viewPoliceStation).toList(), mapper.countPoliceStation(query), query.page(), query.pageSize());
    }
    @Transactional(readOnly = true)
    public PoliceStationView getPoliceStation(long id) { return viewPoliceStation(mapper.findPoliceStation(id)); }
    @Transactional
    public PoliceStationView createPoliceStation(WritePoliceStation input, String fixtureKey) {
        ArchiveRow row = rowPoliceStation(input);
        var unit = stationUnit(input.unitCode());
        validateStationUnit(units.lockReference(unit.id()),true);
        row.unitId = unit.id();
        row.fixtureKey = fixtureKey;
        mapper.insertPoliceStation(row);
        var created = viewPoliceStation(mapper.findPoliceStation(row.id));
        auditArchive("police-station", "POLICE_STATION", "新增", created.id(), created.name());
        return created;
    }
    @Transactional
    public PoliceStationView updatePoliceStation(long id, WritePoliceStation input) {
        ArchiveRow row = rowPoliceStation(input);
        var unit = stationUnit(input.unitCode());
        lockOwners(List.of(id),List.of(),unit.id());
        ArchiveRow before = required(mapper.lockPoliceStation(id));
        version(before, input.version());
        validateStationUnit(units.lockReference(unit.id()), !Objects.equals(before.unitId,unit.id()));
        if (!Objects.equals(before.unitId,unit.id()) && !mapper.lockStationOfficers(id).isEmpty()) conflict("派出所仍有所属民警，请先处理成员归属再更换所属单位");
        row.unitId = unit.id();
        row.id = id; row.version = input.version();
        if (mapper.updatePoliceStation(row) != 1) conflict("档案已被修改，请刷新后重试");
        var updated = viewPoliceStation(mapper.findPoliceStation(id));
        auditArchive("police-station", "POLICE_STATION", "修改", updated.id(), updated.name());
        return updated;
    }
    @Transactional
    public void deletePoliceStation(long id, int inputVersion) {
        lockOwners(List.of(id),List.of(),null);
        ArchiveRow row = required(mapper.lockPoliceStation(id));
        version(row, inputVersion);
        if (!mapper.lockStationOfficers(id).isEmpty()) conflict("该派出所仍有所属民警，不能删除；可先解除关联或停用");
        if (!mapper.lockStationWharfs(id).isEmpty()) conflict("该派出所仍有管辖码头，不能删除；可先解除关联或停用");
        if (mapper.deletePoliceStation(id, inputVersion) != 1) conflict("档案已被修改，请刷新后重试");
        auditArchive("police-station", "POLICE_STATION", "删除", id(row.id), row.name);
    }

    private static PortOfficerView viewPortOfficer(ArchiveRow row) {
        required(row);
        return new PortOfficerView(
                id(row.id),
                row.name,
                id(row.userId),
                row.loginName,
                row.userStatus,
                row.unitCode,
                row.unitName,
                row.status,
                id(row.policeStationId),
                row.duty,
                row.version,
                row.createdAt,
                row.updatedAt,
                row.fixtureKey,
                row.region,
                row.policeStationName,
                row.policeStationStatus);
    }
    private static ArchiveRow rowPortOfficer(WritePortOfficer input) {
        ArchiveRow row = new ArchiveRow();
        row.userId = reference(input.userId());
        row.status = text(input.status());
        row.policeStationId = reference(input.policeStationId());
        row.duty = text(input.duty());
        return row;
    }
    @Transactional(readOnly = true)
    public PageResult<PortOfficerView> listPortOfficer(ArchiveQuery query) {
        return new PageResult<>(mapper.listPortOfficer(query).stream().map(MaritimeService::viewPortOfficer).toList(), mapper.countPortOfficer(query), query.page(), query.pageSize());
    }
    @Transactional(readOnly = true)
    public PortOfficerView getPortOfficer(long id) { return viewPortOfficer(mapper.findPortOfficer(id)); }
    @Transactional
    public PortOfficerView createPortOfficer(WritePortOfficer input, String fixtureKey) {
        ArchiveRow row = rowPortOfficer(input);
        row.fixtureKey = fixtureKey;
        lockOwners(List.of(row.policeStationId),List.of(row.userId),null);
        validateMember(row,null);
        try { mapper.insertPortOfficer(row); }
        catch (DuplicateKeyException error) { conflict("该用户已关联派出所，请勿重复添加"); }
        var created = viewPortOfficer(mapper.findPortOfficer(row.id));
        auditArchive("port-officer", "PORT_OFFICER", "新增", created.id(), created.name());
        return created;
    }
    @Transactional
    public PortOfficerView updatePortOfficer(long id, WritePortOfficer input) {
        ArchiveRow row = rowPortOfficer(input);
        ArchiveRow snapshot = required(mapper.findPortOfficer(id));
        lockOwners(Arrays.asList(snapshot.policeStationId,row.policeStationId),List.of(snapshot.userId,row.userId),null);
        ArchiveRow before = required(mapper.lockPortOfficer(id));
        version(before, input.version());
        if (!Objects.equals(before.userId,row.userId)) invalid("已建立的民警档案不能更换人员身份，请解除关联后重新添加");
        validateMember(row,before);
        if (!Objects.equals(before.policeStationId, row.policeStationId) && !mapper.lockOfficerWharfs(id).isEmpty()) conflict("该民警仍负责码头，请先解除码头责任关联再更换所属派出所");
        row.id = id; row.version = input.version();
        if (mapper.updatePortOfficer(row) != 1) conflict("档案已被修改，请刷新后重试");
        var updated = viewPortOfficer(mapper.findPortOfficer(id));
        auditArchive("port-officer", "PORT_OFFICER", "修改", updated.id(), updated.name());
        return updated;
    }
    @Transactional
    public void deletePortOfficer(long id, int inputVersion) {
        ArchiveRow snapshot = required(mapper.findPortOfficer(id));
        lockOwners(List.of(snapshot.policeStationId),List.of(snapshot.userId),null);
        ArchiveRow row = required(mapper.lockPortOfficer(id));
        version(row, inputVersion);
        if (!mapper.lockOfficerWharfs(id).isEmpty()) conflict("该民警仍负责码头，不能删除；可先解除责任关联或停用");
        // 锁定行本身没有姓名（民警名在 sys_user），删除前先在同事务内取一次，留痕要的是姓名快照
        String name = required(mapper.findPortOfficer(id)).name;
        if (mapper.deletePortOfficer(id, inputVersion) != 1) conflict("档案已被修改，请刷新后重试");
        auditArchive("port-officer", "PORT_OFFICER", "删除", id(row.id), name);
    }

    @Transactional(readOnly = true)
    public PageResult<ArchiveOption> portOptions(ArchiveQuery query) {
        return new PageResult<>(mapper.listPort(query).stream().map(MaritimeService::option).toList(), mapper.countPort(query), query.page(), query.pageSize());
    }
    @Transactional(readOnly = true)
    public PageResult<ArchiveOption> stationOptions(ArchiveQuery query) {
        return new PageResult<>(mapper.listPoliceStation(query).stream().map(MaritimeService::option).toList(), mapper.countPoliceStation(query), query.page(), query.pageSize());
    }
    @Transactional(readOnly = true)
    public PageResult<ArchiveOption> officerOptions(ArchiveQuery query) {
        return new PageResult<>(mapper.listOfficerOptions(query).stream().map(MaritimeService::option).toList(), mapper.countOfficerOptions(query), query.page(), query.pageSize());
    }
    @Transactional(readOnly = true)
    public PageResult<WharfRelationView> wharfRelations(ArchiveQuery query) {
        if (query.policeStationId() == null && query.responsibleOfficerId() == null) invalid("责任关系查询须限定派出所或民警");
        return new PageResult<>(mapper.listWharf(query).stream().map(row -> new WharfRelationView(
                id(row.id), row.name, row.status, id(row.responsibleOfficerId),
                row.responsibleOfficerName)).toList(), mapper.countWharf(query), query.page(), query.pageSize());
    }
    @Transactional(readOnly = true)
    public PageResult<OfficerUserOption> officerUserOptions(long stationId, String keyword, int page, int size) {
        var query = query(keyword,null,null,null,null,null,null,page,size);
        ArchiveRow station = required(mapper.findPoliceStation(stationId));
        if (!"ENABLED".equals(station.status) || !"ENABLED".equals(units.referenceByCode(station.unitCode).status())) return new PageResult<>(List.of(),0,page,size);
        return new PageResult<>(mapper.userCandidates(station.unitId,query.keyword(),query.offset(),size),mapper.countUserCandidates(station.unitId,query.keyword()),page,size);
    }
    private static ArchiveOption option(ArchiveRow row) {
        return new ArchiveOption(id(row.id), row.name, row.status, id(row.policeStationId));
    }
}
