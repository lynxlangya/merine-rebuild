package com.merine.rebuild.maritime;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.maritime.dto.*;
import com.merine.rebuild.system.security.SystemAdminRegressionSupport;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 隔离 MySQL：六类协议、版本、权限、责任不变量、幂等填充与真实事务竞争。 */
class MaritimeRegressionTest extends SystemAdminRegressionSupport {
    @Autowired MaritimeService service;
    @Autowired MaritimeFixtureInitializer fixtures;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach void resetArchives() { clearArchives(); }
    @AfterEach void cleanupArchives() {
        clearArchives();
        jdbcTemplate.update("DELETE ur FROM sys_user_role ur JOIN sys_user u ON u.id=ur.user_id WHERE u.login_name LIKE 'regr.maritime.%'");
        jdbcTemplate.update("DELETE FROM sys_user WHERE login_name LIKE 'regr.maritime.%'");
        jdbcTemplate.update("DELETE rp FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id WHERE r.role_code LIKE 'REGR-MARITIME-%'");
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code LIKE 'REGR-MARITIME-%'");
    }
    private void clearArchives() {
        for (String table : List.of("archive_wharf", "archive_port_officer", "archive_island", "archive_anchorage", "archive_port", "archive_police_station")) jdbcTemplate.update("DELETE FROM " + table);
    }
    private WritePort portInput(Integer version, String status) { return new WritePort(version,"回归港口","回归区域",null,status,null); }
    private WritePoliceStation stationInput(Integer version, String status) { return new WritePoliceStation(version,"回归派出所","回归区域",null,status); }
    private WritePortOfficer officerInput(Integer version, String station, String status) { return new WritePortOfficer(version,"测试民警",status,station,"责任民警"); }
    private WriteWharf wharfInput(Integer version, String port, String station, String officer, String status) { return new WriteWharf(version,"回归码头","回归区域",null,status,null,port,station,officer); }
    private String body(Object input) throws Exception { return objectMapper.writeValueAsString(input); }

    @Test void allSixResourcesHaveCrudAndStringIdsUtcTimesAndPagination() throws Exception {
        var session=adminSession();
        var station=service.createPoliceStation(stationInput(null,"ENABLED"),null);
        Map<String,Object> inputs=new LinkedHashMap<>();
        inputs.put("ports",portInput(null,"ENABLED"));
        inputs.put("wharfs",wharfInput(null,null,null,null,"ENABLED"));
        inputs.put("anchorages",new WriteAnchorage(null,"回归锚地","回归区域",null,"ENABLED",null));
        inputs.put("islands",new WriteIsland(null,"回归海岛","回归区域",null,"ENABLED","UNINHABITED"));
        inputs.put("police-stations",stationInput(null,"ENABLED"));
        inputs.put("port-officers",officerInput(null,station.id(),"ENABLED"));
        for (var entry:inputs.entrySet()) {
            String path="/api/maritime/"+entry.getKey();
            var created=sendJson(post(path).content(body(entry.getValue())),session);
            assertThat(created.getResponse().getStatus()).as(bodyOf(created)).isEqualTo(201);
            assertThat(bodyOf(created)).doesNotContain("sourceUrl", "sourceDate", "checkedDate", "isMock", "jurisdictionBasis", "jurisdictionSourceUrl");
            String id=jsonOf(bodyOf(created),"$.data.id");
            assertThat(id).matches("[1-9][0-9]*");
            assertThat((String)jsonOf(bodyOf(created),"$.data.createdAt")).endsWith("Z");
            var input=objectMapper.readTree(bodyOf(created)).get("data").deepCopy();
            ((tools.jackson.databind.node.ObjectNode)input).put("status","DISABLED");
            assertThat(sendJson(put(path+"/"+id).content(input.toString()),session).getResponse().getStatus()).isEqualTo(200);
            assertThat((String)jsonOf(bodyOf(getJson(path+"/"+id,session)),"$.data.status")).isEqualTo("DISABLED");
            assertError(sendJson(put(path+"/"+id).content(input.toString()),session),409,"ARCHIVE_CONFLICT");
            assertThat(getJson(path+"?page=1&pageSize=1&keyword=回归",session).getResponse().getStatus()).isEqualTo(200);
            assertError(sendJson(delete(path+"/"+id),session),400,"INVALID_PARAMETER");
            assertError(sendJson(delete(path+"/"+id+"?version=0"),session),409,"ARCHIVE_CONFLICT");
            assertThat(sendJson(delete(path+"/"+id+"?version=1"),session).getResponse().getStatus()).isEqualTo(200);
            assertError(getJson(path+"/"+id,session),404,"ARCHIVE_NOT_FOUND");
        }
        assertError(getJson("/api/maritime/ports?pageSize=101",session),400,"INVALID_ARCHIVE");
    }

    @Test void relationshipsEnforceSameStationAndProtectReferencedArchives() throws Exception {
        var station=service.createPoliceStation(stationInput(null,"ENABLED"),null);
        var other=service.createPoliceStation(stationInput(null,"ENABLED"),null);
        var officer=service.createPortOfficer(officerInput(null,station.id(),"ENABLED"),null);
        var port=service.createPort(portInput(null,"ENABLED"),null);
        var wharf=service.createWharf(wharfInput(null,port.id(),station.id(),officer.id(),"ENABLED"),null);
        assertThatThrownBy(() -> service.createWharf(wharfInput(null,port.id(),other.id(),officer.id(),"ENABLED"),null)).isInstanceOf(ApiException.class).hasMessageContaining("必须属于");
        assertThatThrownBy(() -> service.updatePortOfficer(Long.parseLong(officer.id()),officerInput(0,other.id(),"ENABLED"))).hasMessageContaining("先解除");
        assertThatThrownBy(() -> service.deletePort(Long.parseLong(port.id()),0)).hasMessageContaining("下属码头");
        assertThatThrownBy(() -> service.deletePoliceStation(Long.parseLong(station.id()),0)).hasMessageContaining("所属民警");
        assertThatThrownBy(() -> service.deletePortOfficer(Long.parseLong(officer.id()),0)).hasMessageContaining("负责码头");
        var session=adminSession();
        assertThat(intOf(bodyOf(getJson("/api/maritime/wharfs?portId="+port.id(),session)),"$.data.total")).isEqualTo(1);
        assertThat(intOf(bodyOf(getJson("/api/maritime/options/wharf-relations?responsibleOfficerId="+officer.id(),session)),"$.data.total")).isEqualTo(1);
        service.updatePort(Long.parseLong(port.id()),portInput(0,"DISABLED"));
        service.updatePoliceStation(Long.parseLong(station.id()),stationInput(0,"DISABLED"));
        service.updatePortOfficer(Long.parseLong(officer.id()),officerInput(0,station.id(),"DISABLED"));
        // 保留已有停用关系允许编辑；新引用拒绝。
        var changed=service.updateWharf(Long.parseLong(wharf.id()),wharfInput(0,port.id(),station.id(),officer.id(),"DISABLED"));
        assertThat(changed.portStatus()).isEqualTo("DISABLED");
        assertThatThrownBy(() -> service.createWharf(wharfInput(null,port.id(),station.id(),officer.id(),"ENABLED"),null)).hasMessageContaining("已停用");
        service.updateWharf(Long.parseLong(wharf.id()),wharfInput(1,null,other.id(),null,"ENABLED"));
        service.deletePortOfficer(Long.parseLong(officer.id()),1);
        service.deletePoliceStation(Long.parseLong(station.id()),1);
        service.deletePort(Long.parseLong(port.id()),1);
    }

    @Test void invalidReferencesAndBlankNamesAreRejectedWithoutPartialWrites() throws Exception {
        var session=adminSession();
        assertError(sendJson(post("/api/maritime/wharfs").content(body(wharfInput(null,"9223372036854775807",null,null,"ENABLED"))),session),400,"INVALID_ARCHIVE");
        var blankInput=new WritePort(null," ","区域",null,"ENABLED",null);
        assertThat(sendJson(post("/api/maritime/ports").content(body(blankInput)),session).getResponse().getStatus()).isEqualTo(400);
        service.createPort(portInput(null,"ENABLED"),null);
        assertThat(intOf(bodyOf(getJson("/api/maritime/ports?keyword=%25",session)),"$.data.total")).isZero();
        assertThat(intOf(bodyOf(getJson("/api/maritime/ports?keyword=_",session)),"$.data.total")).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM archive_wharf",Integer.class)).isZero();
        assertError(getJson("/api/maritime/options/wharf-relations",session),400,"INVALID_ARCHIVE");
    }

    @Test void seedAddsExactly31AndDoesNotOverwriteManualChangesOrUnrelatedData() {
        var manual=service.createPort(portInput(null,"ENABLED"),null);
        assertThat(fixtures.initializeForTest()).isEqualTo(31);
        Long id=jdbcTemplate.queryForObject("SELECT id FROM archive_port WHERE fixture_key='port.nbzs'",Long.class);
        service.updatePort(id,new WritePort(0,"人工修改名称","自定义区域",null,"DISABLED",null));
        assertThat(fixtures.initializeForTest()).isZero();
        assertThat(service.getPort(id).name()).isEqualTo("人工修改名称");
        assertThat(service.getPort(id).status()).isEqualTo("DISABLED");
        assertThat(service.getPort(Long.parseLong(manual.id())).name()).isEqualTo(manual.name());
        assertThat(jdbcTemplate.queryForList("SELECT name FROM archive_port_officer ORDER BY id",String.class)).containsExactly("陈浩","周明远","林嘉诚","吴志航","徐立新","沈亦凡");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM archive_wharf WHERE location IS NULL OR purpose IS NULL",Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('archive_port','archive_wharf','archive_anchorage','archive_island','archive_police_station','archive_port_officer') AND column_name IN ('source_url','source_date','checked_date','jurisdiction_basis','jurisdiction_source_url','is_mock')",Integer.class)).isZero();
        assertThatThrownBy(() -> fixtures.initialize()).hasMessageContaining("只允许");
    }

    @Test void readWriteCsrfAndMinimalRelationPermissionsAreIndependent() throws Exception {
        var admin=adminSession();
        var harbor=restrictedSession("harbor",List.of("maritime:harbor:read"));
        var policing=restrictedSession("policing",List.of("maritime:policing:read"));
        var none=restrictedSession("plain",List.of());
        assertThat(getJson("/api/maritime/ports",harbor).getResponse().getStatus()).isEqualTo(200);
        assertForbidden(getJson("/api/maritime/police-stations",harbor));
        assertForbidden(getJson("/api/maritime/ports",policing));
        assertThat(getJson("/api/maritime/options/police-stations",harbor).getResponse().getStatus()).isEqualTo(200);
        assertForbidden(getJson("/api/maritime/options/ports",none));
        assertForbidden(sendJson(post("/api/maritime/ports").content(body(portInput(null,"ENABLED"))),harbor));
        assertForbidden(mockMvc.perform(post("/api/maritime/ports").session(admin).contentType("application/json").content(body(portInput(null,"ENABLED")))).andReturn());
        for (String kind:List.of("ports","wharfs","anchorages","islands","police-stations","port-officers")) {
            assertForbidden(getJson("/api/maritime/"+kind,none));
            assertForbidden(sendJson(delete("/api/maritime/"+kind+"/1?version=0"),none));
        }
        assertUnauthenticatedJson(getJson("/api/maritime/ports",null));
        var menus=getJson("/api/me/menus",harbor);
        assertThat(bodyOf(menus)).contains("maritime.harbor-sites").doesNotContain("maritime.islands","maritime.police-resources");
    }
    private MockHttpSession restrictedSession(String suffix,List<String> permissions) throws Exception {
        String code="REGR-MARITIME-"+suffix; String login="regr.maritime."+suffix;
        jdbcTemplate.update("DELETE ur FROM sys_user_role ur JOIN sys_user u ON u.id=ur.user_id WHERE u.login_name=?",login);
        jdbcTemplate.update("DELETE FROM sys_user WHERE login_name=?",login);
        jdbcTemplate.update("DELETE rp FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id WHERE r.role_code=?",code);
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code=?",code);
        jdbcTemplate.update("INSERT INTO sys_role(role_code,role_name) VALUES (?,?)",code,"涉海回归角色");
        for(String permission:permissions) jdbcTemplate.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT r.id,p.id FROM sys_role r,sys_permission p WHERE r.role_code=? AND p.permission_code=?",code,permission);
        jdbcTemplate.update("INSERT INTO sys_user(login_name,display_name,password_hash,unit_id) SELECT ?,?,?,id FROM sys_unit WHERE unit_code=?",login,"涉海回归账号",passwordEncoder.encode(ADMIN_PASSWORD),ADMIN_UNIT_CODE);
        jdbcTemplate.update("INSERT INTO sys_user_role(user_id,role_id) SELECT u.id,r.id FROM sys_user u,sys_role r WHERE u.login_name=? AND r.role_code=?",login,code);
        return signIn(login,ADMIN_PASSWORD);
    }

    @Test void deletionWinsOverConcurrentNewReferencesWithoutDanglingRows() throws Exception {
        var port=service.createPort(portInput(null,"ENABLED"),null);
        race(() -> service.deletePort(Long.parseLong(port.id()),0), () -> service.createWharf(wharfInput(null,port.id(),null,null,"ENABLED"),null), "不存在");
        var station=service.createPoliceStation(stationInput(null,"ENABLED"),null);
        race(() -> service.deletePoliceStation(Long.parseLong(station.id()),0), () -> service.createPortOfficer(officerInput(null,station.id(),"ENABLED"),null), "不存在");
        var other=service.createPoliceStation(stationInput(null,"ENABLED"),null);
        var officer=service.createPortOfficer(officerInput(null,other.id(),"ENABLED"),null);
        race(() -> service.deletePortOfficer(Long.parseLong(officer.id()),0), () -> service.createWharf(wharfInput(null,null,other.id(),officer.id(),"ENABLED"),null), "不存在");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM archive_wharf",Integer.class)).isZero();
    }
    @Test void creationWinsOverConcurrentDeletionAndDeletionIsRejected() throws Exception {
        var port=service.createPort(portInput(null,"ENABLED"),null);
        race(() -> service.createWharf(wharfInput(null,port.id(),null,null,"ENABLED"),null), () -> service.deletePort(Long.parseLong(port.id()),0), "下属码头");
        var station=service.createPoliceStation(stationInput(null,"ENABLED"),null);
        race(() -> service.createPortOfficer(officerInput(null,station.id(),"ENABLED"),null), () -> service.deletePoliceStation(Long.parseLong(station.id()),0), "所属民警");
        var officer=service.createPortOfficer(officerInput(null,station.id(),"ENABLED"),null);
        race(() -> service.createWharf(wharfInput(null,null,station.id(),officer.id(),"ENABLED"),null), () -> service.deletePortOfficer(Long.parseLong(officer.id()),0), "负责码头");
    }
    private void race(Runnable first, Runnable second, String message) throws Exception {
        var locked=new CountDownLatch(1); var commit=new CountDownLatch(1); var started=new CountDownLatch(1);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> owner=executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                first.run(); locked.countDown();
                try { if (!commit.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("事务等待超时"); }
                catch(InterruptedException error) { Thread.currentThread().interrupt(); throw new RuntimeException(error); }
            }));
            try {
                assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
                Future<?> contender=executor.submit(() -> { started.countDown(); second.run(); });
                assertThat(started.await(5,TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> contender.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                commit.countDown(); owner.get(5,TimeUnit.SECONDS);
                assertThatThrownBy(() -> contender.get(5,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(ApiException.class).cause().hasMessageContaining(message);
            } finally { commit.countDown(); }
        }
    }
}
