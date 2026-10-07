package com.merine.rebuild.maritime;

import com.merine.rebuild.maritime.dto.*;
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

/** 固定公开样例清单的受控初始化；不覆盖已存在 fixtureKey 的档案。 */
@Service
public class MaritimeFixtureInitializer {
    private final MaritimeService service;
    private final MaritimeMapper mapper;
    private final ObjectMapper json;
    private final Validator validator;
    private final DataSource dataSource;
    public MaritimeFixtureInitializer(MaritimeService service, MaritimeMapper mapper,
            ObjectMapper json, Validator validator, DataSource dataSource) {
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
        try (var stream = new ClassPathResource("fixtures/maritime-zhejiang.json").getInputStream()) {
            JsonNode records = json.readTree(stream).get("records");
            Map<String, Long> ids = new HashMap<>();
            int created=0;
            for (JsonNode record : records) {
                String kind=record.get("kind").asString(); String key=record.get("key").asString();
                ArchiveRow existing = switch (kind) {
                    case "Port" -> mapper.findPortFixture(key);
                    case "PoliceStation" -> mapper.findPoliceStationFixture(key);
                    case "PortOfficer" -> mapper.findPortOfficerFixture(key);
                    case "Wharf" -> mapper.findWharfFixture(key);
                    case "Anchorage" -> mapper.findAnchorageFixture(key);
                    case "Island" -> mapper.findIslandFixture(key);
                    default -> throw new IllegalStateException("样例档案类型无效");
                };
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
    private <T> T validated(T input) {
        var violations=validator.validate(input);
        if (!violations.isEmpty()) throw new IllegalStateException("固定样例校验失败："+violations.iterator().next().getPropertyPath());
        return input;
    }
}
