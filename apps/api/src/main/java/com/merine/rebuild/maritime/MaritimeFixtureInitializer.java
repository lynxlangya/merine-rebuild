package com.merine.rebuild.maritime;

import com.merine.rebuild.maritime.dto.*;
import com.merine.rebuild.system.user.admin.UserAdminService;
import com.merine.rebuild.system.user.admin.dto.UserRequests;
import com.merine.rebuild.system.user.usage.UserDirectoryLookup;
import com.merine.rebuild.system.unit.UnitLookup;
import com.merine.rebuild.maritime.persistence.*;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 固定本地样例清单的受控初始化；不覆盖已存在 fixtureKey 的档案。 */
@Service
public class MaritimeFixtureInitializer {
    private final com.merine.rebuild.system.role.RoleLookup roles;
    private final com.merine.rebuild.system.role.RoleService roleAdmin;
    private final UserAdminService userAdmin;
    private final UserDirectoryLookup users;
    private final UnitLookup units;
    private final com.merine.rebuild.system.user.authorization.AdminCoverageGuard coverage;
    private final MaritimeService service;
    private final MaritimeMapper mapper;
    private final ObjectMapper json;
    private final Validator validator;
    private final DataSource dataSource;
    public MaritimeFixtureInitializer(MaritimeService service, MaritimeMapper mapper,
            ObjectMapper json, Validator validator, DataSource dataSource, UserAdminService userAdmin, UserDirectoryLookup users, UnitLookup units, com.merine.rebuild.system.user.authorization.AdminCoverageGuard coverage, com.merine.rebuild.system.role.RoleLookup roles, com.merine.rebuild.system.role.RoleService roleAdmin) {
        this.roles=roles; this.roleAdmin=roleAdmin;
        this.coverage=coverage; this.userAdmin=userAdmin; this.users=users; this.units=units;
        this.service=service; this.mapper=mapper; this.json=json; this.validator=validator; this.dataSource=dataSource;
    }
    @Transactional
    public int initialize() {
        // 命令只允许本地开发库；测试入口另由实际 catalog 确认隔离库。
        requireCatalog("merine_rebuild");
        return fillMissing();
    }
    @Transactional
    int initializeForTest() {
        requireCatalog("merine_rebuild_test");
        return fillMissing();
    }
    private void requireCatalog(String catalog) {
        try (var connection = dataSource.getConnection()) {
            if (!catalog.equals(connection.getCatalog()) || !connection.getMetaData().getURL().startsWith("jdbc:mysql://mysql:3306/")) {
                throw new IllegalStateException("涉海样例只允许本项目 Docker 本地指定库");
            }
        } catch (java.sql.SQLException error) { throw new IllegalStateException("无法确认本地样例数据库", error); }
    }
    private int fillMissing() {
        coverage.lock();
        try (var stream = new ClassPathResource("fixtures/maritime-zhejiang.json").getInputStream()) {
            JsonNode records = json.readTree(stream).get("records");
            Map<String, ArchiveRow> existingRecords = new HashMap<>();
            for (JsonNode record : records) {
                ArchiveRow existing = existing(record.get("kind").asString(), record.get("key").asString());
                if (existing != null) existingRecords.put(record.get("key").asString(), existing);
            }
            Map<String, UserDirectoryLookup.Person> identities = prepareIdentities(records, existingRecords);
            Map<String, Long> ids = new HashMap<>();
            int created=0;
            for (JsonNode record : records) {
                String kind=record.get("kind").asString(); String key=record.get("key").asString();
                ArchiveRow existing = existingRecords.get(key);
                if (existing != null) { ids.put(key, existing.id); continue; }
                var input = (tools.jackson.databind.node.ObjectNode) record.get("input").deepCopy();
                for (String relation : List.of("port", "policeStation", "responsibleOfficer")) {
                    JsonNode fixture = input.remove(relation+"Key");
                    if (fixture != null) {
                        Long id = ids.get(fixture.asString());
                        if (id == null) throw new IllegalStateException("样例引用不存在："+fixture.asString());
                        input.put(relation+"Id", id.toString());
                    }
                }
                if (kind.equals("PortOfficer")) {
                    String login = input.remove("userLoginName").asString();
                    input.remove("userUnitCode");
                    input.remove("userName");
                    var user = identities.get(login);
                    input.put("userId",Long.toString(user.id()));
                }
                String id = switch (kind) {
                    case "Port" -> service.createPort(validated(json.treeToValue(input, WritePort.class)), key).id();
                    case "PoliceStation" -> service.createPoliceStation(validated(json.treeToValue(input, WritePoliceStation.class)), key).id();
                    case "PortOfficer" -> service.createPortOfficer(validated(json.treeToValue(input, WritePortOfficer.class)), key).id();
                    case "Wharf" -> service.createWharf(validated(json.treeToValue(input, WriteWharf.class)), key).id();
                    case "Anchorage" -> service.createAnchorage(validated(json.treeToValue(input, WriteAnchorage.class)), key).id();
                    case "Island" -> service.createIsland(validated(json.treeToValue(input, WriteIsland.class)), key).id();
                    default -> throw new IllegalStateException("样例档案类型无效");
                };
                ids.put(key, Long.parseLong(id)); created++;
            }
            return created;
        } catch (IOException error) { throw new IllegalStateException("读取固定涉海样例失败", error); }
    }
    private ArchiveRow existing(String kind, String key) {
        return switch (kind) {
            case "Port" -> mapper.findPortFixture(key);
            case "PoliceStation" -> mapper.findPoliceStationFixture(key);
            case "PortOfficer" -> mapper.findPortOfficerFixture(key);
            case "Wharf" -> mapper.findWharfFixture(key);
            case "Anchorage" -> mapper.findAnchorageFixture(key);
            case "Island" -> mapper.findIslandFixture(key);
            default -> throw new IllegalStateException("样例档案类型无效");
        };
    }

    /** 初始化也遵循单位 → 用户 → 所 → 民警 → 港口的固定锁顺序。 */
    private Map<String, UserDirectoryLookup.Person> prepareIdentities(JsonNode records, Map<String, ArchiveRow> existing) {
        SortedSet<Long> ownerIds = new TreeSet<>();
        SortedSet<Long> personIds = new TreeSet<>();
        Map<String, UserDirectoryLookup.Person> identities = new HashMap<>();
        for (JsonNode record : records) {
            String kind = record.get("kind").asString();
            ArchiveRow row = existing.get(record.get("key").asString());
            JsonNode input = record.get("input");
            if (kind.equals("PoliceStation")) {
                if (row != null) ownerIds.add(row.unitId);
                else ownerIds.add(requireFixtureUnit(input.get("unitCode").asString()).id());
            }
            if (kind.equals("PortOfficer")) {
                if (row != null) {
                    var person = users.find(row.userId);
                    ownerIds.add(person.unitId()); personIds.add(person.id());
                } else {
                    var unit = requireFixtureUnit(input.get("userUnitCode").asString());
                    ownerIds.add(unit.id());
                    var person = users.findByLogin(input.get("userLoginName").asString());
                    if (person != null) { ownerIds.add(person.unitId()); personIds.add(person.id()); }
                }
            }
        }
        for (long id : ownerIds) if (units.lockReference(id) == null) throw new IllegalStateException("样例所属单位已删除");
        for (JsonNode record : records) {
            if (!record.get("kind").asString().equals("PortOfficer") || existing.containsKey(record.get("key").asString())) continue;
            JsonNode input = record.get("input");
            String login = input.get("userLoginName").asString();
            var user = users.findByLogin(login);
            if (user == null) {
                // 只创建样例缺失的本地账号；随机初始凭据不输出，基础角色没有功能权限。
                if (roles.listAll().stream().noneMatch(role -> role.code().equals("MARITIME_MEMBER"))) {
                    roleAdmin.create(new com.merine.rebuild.system.role.dto.RoleRequests.CreateRole("MARITIME_MEMBER", "警务成员", "功能权限由管理员另行分配", List.of()));
                }
                var created = userAdmin.create(validated(new UserRequests.CreateUser(login, input.get("userName").asString(), input.get("userUnitCode").asString(), List.of("MARITIME_MEMBER"), UUID.randomUUID().toString())));
                user = users.find(Long.parseLong(created.id()));
            }
            if (user.unitId() != requireFixtureUnit(input.get("userUnitCode").asString()).id()) throw new IllegalStateException("样例用户归属已由人工调整，请核对固定映射：" + login);
            identities.put(login, user); personIds.add(user.id());
        }
        for (long id : personIds) users.lock(id);
        for (String kind : List.of("PoliceStation", "PortOfficer", "Port")) {
            records.valueStream().filter(record -> kind.equals(record.get("kind").asString()))
                    .map(record -> existing.get(record.get("key").asString())).filter(Objects::nonNull)
                    .sorted(Comparator.comparing(row -> row.id)).forEach(row -> {
                        switch (kind) {
                            case "PoliceStation" -> mapper.lockPoliceStation(row.id);
                            case "PortOfficer" -> mapper.lockPortOfficer(row.id);
                            case "Port" -> mapper.lockPort(row.id);
                        }
                    });
        }
        return identities;
    }
    private UnitLookup.Reference requireFixtureUnit(String code) {
        var unit = units.referenceByCode(code);
        if (unit == null) throw new IllegalStateException("样例所属单位不存在：" + code);
        return unit;
    }
    private <T> T validated(T input) {
        var violations=validator.validate(input);
        if (!violations.isEmpty()) throw new IllegalStateException("固定样例校验失败："+violations.iterator().next().getPropertyPath());
        return input;
    }
}
