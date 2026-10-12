package com.merine.rebuild.system.audit;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.merine.rebuild.system.security.SystemAdminRegressionSupport;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

/**
 * S3 埋点回归：涉海档案（六类 × 增/改/删 = 18 个写点）。
 *
 * 档案删掉后没有别的地方能核对名称，因此这里的重点断言是**名称快照在删除后仍在**。
 */
class AuditS3RegressionTest extends SystemAdminRegressionSupport {
    private static final String ARCHIVE_PREFIX = "审计回归";
    private static final List<String> ARCHIVE_TABLES = List.of("archive_wharf", "archive_port_officer",
            "archive_island", "archive_anchorage", "archive_port", "archive_police_station");

    @BeforeEach void cleanAudit() { jdbcTemplate.update("DELETE FROM sys_audit_log"); }

    @AfterEach void cleanup() {
        jdbcTemplate.update("DELETE FROM archive_port_officer WHERE user_id IN "
                + "(SELECT id FROM sys_user WHERE login_name LIKE 'regr.audit.maritime.%')");
        for (String table : ARCHIVE_TABLES) {
            if (!table.equals("archive_port_officer")) {
                jdbcTemplate.update("DELETE FROM " + table + " WHERE name LIKE ?", ARCHIVE_PREFIX + "%");
            }
        }
        jdbcTemplate.update("DELETE FROM sys_user WHERE login_name LIKE 'regr.audit.maritime.%'");
        jdbcTemplate.update("DELETE FROM sys_audit_log");
    }

    private List<Map<String, Object>> auditRows(String action) {
        return jdbcTemplate.queryForList(
                "SELECT result, module, target_type, target_id, target_label, summary FROM sys_audit_log "
                        + "WHERE action = ? ORDER BY id", action);
    }

    @Test
    void everyArchiveWritePointIsRecordedAndNamesSurviveDeletion() throws Exception {
        MockHttpSession session = adminSession();
        String unitCode = "ORG_003";
        jdbcTemplate.update("INSERT INTO sys_user(login_name,display_name,password_hash,unit_id) "
                + "SELECT 'regr.audit.maritime.officer','审计回归民警',?,id FROM sys_unit WHERE unit_code=?",
                passwordEncoder.encode(ADMIN_PASSWORD), unitCode);
        String userId = jdbcTemplate.queryForObject(
                "SELECT CAST(id AS CHAR) FROM sys_user WHERE login_name='regr.audit.maritime.officer'", String.class);

        String station = create(session, "police-stations", archiveInput("派出所", null, Map.of(
                "unitCode", unitCode)));
        String port = create(session, "ports", archiveInput("港口", null, Map.of()));
        String wharf = create(session, "wharfs", archiveInput("码头", null, Map.of()));
        String anchorage = create(session, "anchorages", archiveInput("锚地", null, Map.of()));
        String island = create(session, "islands", archiveInput("海岛", null, Map.of(
                "inhabitationType", "UNINHABITED")));
        String officer = create(session, "port-officers", officerInput(null, userId, station));

        // 18 个写点：六类各 增/改/删。动作码按「资源:动作」，对象带类型、id 与名称。
        Map<String, String> ids = new LinkedHashMap<>();
        ids.put("police-station", station);
        ids.put("port", port);
        ids.put("wharf", wharf);
        ids.put("anchorage", anchorage);
        ids.put("island", island);
        ids.put("port-officer", officer);
        Map<String, String> labels = Map.of("police-station", "派出所", "port", "港口", "wharf", "码头",
                "anchorage", "锚地", "island", "海岛", "port-officer", "民警");
        for (var entry : ids.entrySet()) {
            var creates = auditRows(entry.getKey() + ":create");
            assertThat(creates).as(entry.getKey()).hasSize(1);
            assertThat(creates.getFirst()).containsEntry("module", "maritime")
                    .containsEntry("target_id", entry.getValue())
                    .containsEntry("target_label", ARCHIVE_PREFIX + labels.get(entry.getKey()));
            assertThat((String) creates.getFirst().get("summary"))
                    .contains("新增" + labels.get(entry.getKey()) + "「" + ARCHIVE_PREFIX);
        }

        for (var entry : ids.entrySet()) {
            String path = "/api/maritime/" + entry.getKey() + "s/" + entry.getValue();
            Map<String, Object> body = "port-officer".equals(entry.getKey())
                    ? officerInput(0, userId, station)
                    : new HashMap<>(archiveInput(labels.get(entry.getKey()), 0, switch (entry.getKey()) {
                        case "police-station" -> Map.of("unitCode", unitCode);
                        case "island" -> Map.of("inhabitationType", "UNINHABITED");
                        default -> Map.of();
                    }));
            assertThat(sendJson(put(path).content(objectMapper.writeValueAsString(body)), session)
                    .getResponse().getStatus()).as(path).isEqualTo(200);
            var updates = auditRows(entry.getKey() + ":update");
            assertThat(updates).as(entry.getKey()).hasSize(1);
            assertThat((String) updates.getFirst().get("summary"))
                    .contains("修改" + labels.get(entry.getKey()) + "「");
        }

        // 删除顺序受引用保护约束：民警与码头先删，派出所与港口才能删
        List<String> deleteOrder = List.of("port-officer", "wharf", "police-station", "port",
                "anchorage", "island");
        for (String resource : deleteOrder) {
            MvcResult deleted = sendJson(delete("/api/maritime/" + resource + "s/" + ids.get(resource)
                    + "?version=1"), session);
            assertThat(deleted.getResponse().getStatus()).as(resource + " " + bodyOf(deleted)).isEqualTo(200);
            var deletes = auditRows(resource + ":delete");
            assertThat(deletes).as(resource).hasSize(1);
            assertThat(deletes.getFirst()).containsEntry("target_label", ARCHIVE_PREFIX + labels.get(resource));
        }

        // 档案已经查不到了，但审计里的名称快照还在——涉海档案唯一的追溯依据
        for (String table : ARCHIVE_TABLES) {
            if (!table.equals("archive_port_officer")) {
                assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table
                        + " WHERE name LIKE ?", Integer.class, ARCHIVE_PREFIX + "%")).isZero();
            }
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM archive_port_officer", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_audit_log "
                + "WHERE module='maritime' AND target_label LIKE ?", Integer.class, ARCHIVE_PREFIX + "%"))
                .isEqualTo(18);
        assertThat(auditRows("island:delete").getFirst().get("summary"))
                .isEqualTo("删除海岛「" + ARCHIVE_PREFIX + "海岛」");

        // 负面清单：档案正文（区域、位置、职务等字段值）不进审计，只留对象标识与名称
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_audit_log WHERE CONCAT_WS(' ', "
                + "actor_login, actor_name, actor_unit_name, module, action, target_type, target_id, "
                + "target_label, summary, request_id) LIKE '%回归区域%'", Integer.class)).isZero();
    }

    /** 民警档案只关联身份，姓名来自 sys_user 的显示名（档案表没有 name 列）。 */
    private Map<String, Object> officerInput(Integer version, String userId, String stationId) {
        Map<String, Object> data = new HashMap<>();
        data.put("version", version);
        data.put("userId", userId);
        data.put("policeStationId", stationId);
        data.put("status", "ENABLED");
        data.put("duty", "审计回归职务");
        return data;
    }

    private Map<String, Object> archiveInput(String suffix, Integer version, Map<String, Object> extra) {
        Map<String, Object> data = new HashMap<>(extra);
        data.put("version", version);
        data.put("name", ARCHIVE_PREFIX + suffix);
        data.put("region", "回归区域");
        data.put("location", null);
        data.put("status", "ENABLED");
        return data;
    }

    private String create(MockHttpSession session, String resource, Map<String, Object> input) throws Exception {
        MvcResult result = sendJson(post("/api/maritime/" + resource)
                .content(objectMapper.writeValueAsString(input)), session);
        assertThat(result.getResponse().getStatus()).as(resource + " " + bodyOf(result)).isEqualTo(201);
        return jsonOf(bodyOf(result), "$.data.id");
    }
}
