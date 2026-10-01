package com.merine.rebuild.intelligence;

import static org.assertj.core.api.Assertions.*;
import com.merine.rebuild.auth.AuthenticatedAccount;
import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.intelligence.dto.IntelligenceRequests.*;
import com.merine.rebuild.intelligence.dto.IntelligenceViews.*;
import com.merine.rebuild.system.security.PermissionCodes;
import java.util.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** 隔离 MySQL 的真实引用、权限与发送链回归；所有合成记录随用例回滚。 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IntelligenceRegressionTest {
    @Autowired IntelligenceService service;
    @Autowired IntelligenceUnitUsageLookup usage;
    @Autowired com.merine.rebuild.system.unit.UnitService unitService;
    @Autowired JdbcTemplate sql;
    @Autowired DataSource dataSource;
    @Autowired SqlSessionTemplate session;
    record Unit(long id,String code,String name) { }
    private Unit root,a,b,c,aa,aa2,bb;
    private Authentication hq,pa,pb,pc,paa,paa2,pbb;
    @BeforeEach void setup() throws Exception {
        try(var connection=dataSource.getConnection()) {assertThat(connection.getCatalog()).isEqualTo("merine_rebuild_test");}
        root=unit("SELECT id,unit_code,unit_name FROM sys_unit WHERE unit_level=1 LIMIT 1");
        List<Unit> divisions=sql.query("SELECT id,unit_code,unit_name FROM sys_unit WHERE parent_id=? AND (SELECT COUNT(*) FROM sys_unit child WHERE child.parent_id=sys_unit.id)>=2 ORDER BY id LIMIT 3",(r,n)->new Unit(r.getLong(1),r.getString(2),r.getString(3)),root.id());
        a=divisions.get(0); b=divisions.get(1); c=divisions.get(2);
        aa=unit("SELECT id,unit_code,unit_name FROM sys_unit WHERE parent_id="+a.id()+" ORDER BY id LIMIT 1");
        aa2=unit("SELECT id,unit_code,unit_name FROM sys_unit WHERE parent_id="+a.id()+" ORDER BY id LIMIT 1 OFFSET 1");
        bb=unit("SELECT id,unit_code,unit_name FROM sys_unit WHERE parent_id="+b.id()+" ORDER BY id LIMIT 1");
        hq=principal(root,PermissionCodes.all()); pa=principal(a,PermissionCodes.all());pb=principal(b,PermissionCodes.all());pc=principal(c,PermissionCodes.all());
        paa=principal(aa,PermissionCodes.all());paa2=principal(aa2,PermissionCodes.all());pbb=principal(bb,PermissionCodes.all());
    }
    private Unit unit(String query) {return sql.queryForObject(query,(r,n)->new Unit(r.getLong(1),r.getString(2),r.getString(3)));}
    private Authentication principal(Unit unit,List<String> codes) {
        String login="regr.intel."+UUID.randomUUID().toString().replace("-","");
        sql.update("INSERT INTO sys_user(login_name,display_name,password_hash,unit_id) VALUES(?,?,?,?)",login,"合成账号", "synthetic-test-only",unit.id());
        long id=sql.queryForObject("SELECT id FROM sys_user WHERE login_name=?",Long.class,login);
        return new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(id,login,login,unit.name(),List.of(),List.of(),codes,0),null,codes.stream().map(SimpleGrantedAuthority::new).toList());
    }
    private static String key() {return UUID.randomUUID().toString();}
    private static long id(IntelligenceDetail d) {return Long.parseLong(d.id());}
    private static long receipt(IntelligenceDetail d,Unit unit) {return Long.parseLong(d.receipts().stream().filter(r->r.toUnitName().equals(unit.name())).findFirst().orElseThrow().id());}
    private IntelligenceDraftRequest input(List<Unit> scope,List<Unit> targets,int version) {return new IntelligenceDraftRequest("合成船舶线索","夜间关闭定位的合成线索",scope.stream().map(Unit::code).toList(),targets.stream().map(Unit::code).toList(),"首次说明",version);}
    private IntelligenceDetail create(Authentication auth,List<Unit> scope,List<Unit> targets) {return service.create(auth,key(),input(scope,targets,0));}
    private IntelligenceDetail send(Authentication auth,IntelligenceDetail d,Long from,List<Unit> targets) {return service.send(auth,id(d),from,key(),new IntelligenceSendRequest(targets.stream().map(Unit::code).toList(),"附加说明"));}
    private void sign(Authentication auth,IntelligenceDetail d,long r) {service.receiptAction(auth,id(d),r,"sign",key(),null);}
    private static void error(String code,Runnable run) {assertThatThrownBy(run::run).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo(code));}

    @Test void keywordSearchSupportsChineseNumbersAndLiteralWildcardsWithinDeliveryScope() {
        String marker=UUID.randomUUID().toString();
        IntelligenceDetail draft=service.create(hq,key(),new IntelligenceDraftRequest(
                "中文检索 "+marker+" %_!", "合成检索样例",List.of(a.code(),b.code()),List.of(a.code()),"",0));
        IntelligenceDetail published=send(hq,draft,null,List.of(a));
        service.create(hq,key(),new IntelligenceDraftRequest(
                "中文检索 "+marker+" ABC!", "用于排除通配符误匹配",List.of(a.code()),List.of(a.code()),"",0));
        var chinese=service.list(pa,"received","all","中文检索 "+marker,1,20);
        assertThat(chinese.total()).isEqualTo(1);
        assertThat(chinese.items()).extracting(IntelligenceListItem::id).containsExactly(published.id());
        var number=service.list(hq,"sent","all",published.topicNo(),1,20);
        assertThat(number.total()).isEqualTo(1);
        assertThat(number.items()).extracting(IntelligenceListItem::id).containsExactly(published.id());
        var literal=service.list(hq,"sent","all",marker+" %_!",1,20);
        assertThat(literal.total()).isEqualTo(1);
        assertThat(literal.items()).extracting(IntelligenceListItem::id).containsExactly(published.id());
        assertThat(service.list(pb,"received","all",marker,1,20).total()).isZero();
        assertThat(service.list(pa,"received","all",marker+"不存在",1,20).items()).isEmpty();
    }

    @Test void draftIsPrivateVersionedAndScopeRequiresExplicitTransit() {
        IntelligenceDetail d=create(hq,List.of(a,bb),List.of(a));
        error("TOPIC_NOT_FOUND",()->service.detail(pa,id(d)));
        assertThat(service.list(pa,"received","all",null,1,20).total()).isZero();
        var disabled=service.detail(hq,id(d)).allowedActions().stream().filter(x->x.code().equals("send")).findFirst().orElseThrow();
        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.reasonCode()).isEqualTo("MISSING_TRANSIT_SCOPE");
        assertThatThrownBy(()->send(hq,d,null,List.of(a))).isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.code()).isEqualTo(disabled.reasonCode());assertThat(e.getMessage()).isEqualTo(disabled.reason());});
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM intel_send WHERE topic_id=?",Integer.class,id(d))).isZero();
        assertThat(service.detail(hq,id(d)).status()).isEqualTo("DRAFT");
        IntelligenceDetail updated=service.update(hq,id(d),key(),input(List.of(a,b,bb),List.of(a),d.version()));
        error("DRAFT_VERSION_CHANGED",()->service.update(hq,id(d),key(),input(List.of(a,b),List.of(a),d.version())));
        IntelligenceDetail published=send(hq,updated,null,List.of(a));
        error("TOPIC_PUBLISHED",()->service.update(hq,id(published),key(),input(List.of(a,b),List.of(a),published.version())));
        error("TOPIC_NOT_FOUND",()->service.detail(pb,id(published))); // 范围内未送达也没有查看权
        assertThat(service.detail(pa,id(published)).scopeUnitCodes()).isEmpty();
    }

    @Test void unsignedCanViewWithoutTrackingThenSignFeedbackOrForward() {
        IntelligenceDetail d=send(hq,create(hq,List.of(a,aa),List.of(a)),null,List.of(a)); long r=receipt(d,a);
        assertThat(service.detail(pa,id(d)).body()).isEqualTo(d.body());
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM intel_read WHERE receipt_id=?",Integer.class,r)).isZero();
        IntelligenceAction disabled=service.detail(pa,id(d)).receipts().getFirst().allowedActions().stream().filter(x->x.code().equals("forward")).findFirst().orElseThrow();
        assertThat(disabled.reasonCode()).isEqualTo("SIGN_REQUIRED");
        error(disabled.reasonCode(),()->send(pa,d,r,List.of(aa)));
        error("SIGN_REQUIRED",()->service.receiptAction(pa,id(d),r,"feedbacks",key(),new IntelligenceFeedbackRequest("线索")));
        assertThat(service.detail(pa,id(d)).receipts().getFirst().allowedActions()).extracting(IntelligenceAction::code).doesNotContain("read");
        error("INVALID_ACTION",()->service.receiptAction(pa,id(d),r,"read",key(),null));
        sign(pa,d,r); send(pa,d,r,List.of(aa)); // 不反馈也能继续流转
        assertThat(service.detail(paa,id(d)).receipts()).hasSize(2);
    }

    @Test void repeatedPathsSignIndependentlyAndFeedbackFollowsExactLineage() {
        IntelligenceDetail d=send(hq,create(hq,List.of(a,b,aa,bb),List.of(a,b)),null,List.of(a,b));
        long ra=receipt(d,a), originalB=receipt(d,b); sign(pa,d,ra); send(pa,d,ra,List.of(b));
        IntelligenceDetail mine=service.detail(pb,id(d)); assertThat(mine.receipts().stream().filter(IntelligenceReceipt::mine)).hasSize(2);
        assertThat(service.list(pb,"received","pending",null,1,20).items()).hasSize(1);
        long forwardedB=Long.parseLong(mine.receipts().stream().filter(r->r.mine() && r.parentReceiptId()!=null).findFirst().orElseThrow().id());
        sign(pb,d,originalB);
        assertThat(service.list(pb,"received","pending",null,1,20).items().getFirst().pendingReceiptCount()).isEqualTo(1);
        service.receiptAction(pb,id(d),originalB,"feedbacks",key(),new IntelligenceFeedbackRequest("原分支反馈"));
        assertThat(service.detail(pa,id(d)).receipts().stream().flatMap(r->r.feedbacks().stream())).isEmpty();
        sign(pb,d,forwardedB);
        service.receiptAction(pb,id(d),forwardedB,"feedbacks",key(),new IntelligenceFeedbackRequest("经甲分支反馈"));
        assertThat(service.detail(pa,id(d)).receipts().stream().flatMap(r->r.feedbacks().stream()).map(IntelligenceFeedback::body)).containsExactly("经甲分支反馈");
        assertThat(service.detail(hq,id(d)).receipts().stream().flatMap(r->r.feedbacks().stream())).hasSize(2);
        send(pb,d,originalB,List.of(bb));
        assertThat(service.detail(pbb,id(d)).receipts().stream().flatMap(r->r.feedbacks().stream())).isEmpty(); // 下游也不能看到上游反馈
        assertThat(service.detail(pa,id(d)).receipts().stream().map(IntelligenceReceipt::toUnitName)).doesNotContain(bb.name());
        assertThat(service.list(pb,"received","signed",null,1,20).items()).hasSize(1);
    }

    @Test void returningThroughSameUnitPreservesExactBranchFeedbackVisibility() {
        IntelligenceDetail d=send(hq,create(hq,List.of(a,b,aa),List.of(a,b)),null,List.of(a,b));
        long ra=receipt(d,a),rb=receipt(d,b);sign(pa,d,ra);sign(pb,d,rb);
        service.receiptAction(pa,id(d),ra,"feedbacks",key(),new IntelligenceFeedbackRequest("甲上游反馈"));
        service.receiptAction(pb,id(d),rb,"feedbacks",key(),new IntelligenceFeedbackRequest("乙平行反馈"));
        send(pa,d,ra,List.of(b));
        long viaA=Long.parseLong(service.detail(pb,id(d)).receipts().stream().filter(r->r.mine() && r.parentReceiptId()!=null).findFirst().orElseThrow().id());
        sign(pb,d,viaA);send(pb,d,viaA,List.of(a));
        long returnedA=Long.parseLong(service.detail(pa,id(d)).receipts().stream().filter(r->r.mine() && r.parentReceiptId()!=null).findFirst().orElseThrow().id());
        sign(pa,d,returnedA);send(pa,d,returnedA,List.of(aa));
        long leaf=receipt(service.detail(paa,id(d)),aa);sign(paa,d,leaf);
        service.receiptAction(paa,id(d),leaf,"feedbacks",key(),new IntelligenceFeedbackRequest("末端反馈"));
        assertThat(service.detail(pa,id(d)).receipts().stream().flatMap(r->r.feedbacks().stream()).map(IntelligenceFeedback::body)).containsExactly("甲上游反馈","末端反馈");
        assertThat(service.detail(pb,id(d)).receipts().stream().flatMap(r->r.feedbacks().stream()).map(IntelligenceFeedback::body)).containsExactly("乙平行反馈","末端反馈");
        assertThat(service.detail(paa,id(d)).receipts().stream().flatMap(r->r.feedbacks().stream()).map(IntelligenceFeedback::body)).containsExactly("末端反馈");
        assertThat(service.detail(hq,id(d)).receipts().stream().flatMap(r->r.feedbacks().stream())).hasSize(3);
    }

    @Test void draftCanRemoveDisabledScopeUnitThenPublishToRemainingTarget() {
        IntelligenceDetail d=create(hq,List.of(a,b),List.of(a));
        sql.update("UPDATE sys_unit SET status='DISABLED' WHERE id=?",a.id());session.clearCache();
        assertThat(service.detail(hq,id(d)).allowedActions().stream().filter(x->x.code().equals("send")).findFirst().orElseThrow().reasonCode()).isEqualTo("SCOPE_UNIT_DISABLED");
        IntelligenceDetail updated=service.update(hq,id(d),key(),input(List.of(b),List.of(b),d.version()));
        IntelligenceDetail sent=send(hq,updated,null,List.of(b));
        assertThat(sent.status()).isEqualTo("PUBLISHED");
        assertThat(sent.scopeUnitCodes()).containsExactly(b.code());
        assertThat(sent.receipts()).extracting(IntelligenceReceipt::toUnitName).containsExactly(b.name());
    }

    @Test void hierarchicalAndDivisionPeerFlowsWorkButBrigadePeersAreRejected() {
        IntelligenceDetail up=send(paa,create(paa,List.of(a,root),List.of(a)),null,List.of(a));
        sign(pa,up,receipt(service.detail(pa,id(up)),a)); send(pa,up,receipt(service.detail(pa,id(up)),a),List.of(root));
        assertThat(service.detail(hq,id(up)).sourceUnitName()).isEqualTo(aa.name());
        error("ILLEGAL_FLOW_RELATION",()->create(paa,List.of(a,aa2),List.of(aa2)));
        assertThat(service.options(paa,"create",null,null).stream().filter(IntelligenceUnitOption::targetEligible))
                .extracting(IntelligenceUnitOption::code).containsExactly(a.code());
        IntelligenceDetail down=send(hq,create(hq,List.of(a,aa,aa2),List.of(a)),null,List.of(a));
        long parent=receipt(service.detail(pa,id(down)),a);sign(pa,down,parent);send(pa,down,parent,List.of(aa));
        long own=receipt(service.detail(paa,id(down)),aa);sign(paa,down,own);
        assertThat(service.options(paa,"forward",id(down),own)).extracting(IntelligenceUnitOption::code).containsExactly(a.code());
        error("ILLEGAL_FLOW_RELATION",()->send(paa,down,own,List.of(aa2)));
        error("TOPIC_NOT_FOUND",()->service.detail(paa2,id(down)));
        error("ILLEGAL_FLOW_RELATION",()->create(hq,List.of(aa),List.of(aa)));
        error("ILLEGAL_FLOW_RELATION",()->create(paa,List.of(bb),List.of(bb)));
        IntelligenceDetail d=create(hq,List.of(a,b,c),List.of(a));
        error("OUTSIDE_SCOPE",()->send(hq,d,null,List.of(a,aa)));
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM intel_send WHERE topic_id=?",Integer.class,id(d))).isZero();
        send(hq,d,null,List.of(a));
        sql.update("UPDATE sys_unit SET status='DISABLED' WHERE id=?",b.id());session.clearCache();
        error("INVALID_UNIT",()->send(hq,d,null,List.of(a,b)));
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM intel_send WHERE topic_id=?",Integer.class,id(d))).isEqualTo(1);
    }

    @Test void sourceSharesWithNewUnitsButNeverResendsToAnyExistingRecipient() {
        IntelligenceDetail d=send(hq,create(hq,List.of(a,b,c),List.of(a)),null,List.of(a));
        assertThat(d.allowedActions()).extracting(IntelligenceAction::code).doesNotContain("update").contains("send");
        assertThat(service.options(hq,"send",id(d),null)).extracting(IntelligenceUnitOption::code).containsExactlyInAnyOrder(b.code(),c.code());
        long ra=receipt(d,a);sign(pa,d,ra);send(pa,d,ra,List.of(b));
        assertThat(service.options(hq,"send",id(d),null)).extracting(IntelligenceUnitOption::code).containsExactly(c.code());
        error("UNIT_ALREADY_RECEIVED",()->send(hq,d,null,List.of(a)));
        error("UNIT_ALREADY_RECEIVED",()->send(hq,d,null,List.of(b,c)));
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM intel_receipt r JOIN intel_send s ON s.id=r.send_id WHERE s.topic_id=?",Integer.class,id(d))).isEqualTo(2);
        IntelligenceDetail finalDelivery=send(hq,d,null,List.of(c));
        assertThat(finalDelivery.allowedActions()).extracting(IntelligenceAction::code).containsExactly("supplement");
        error("NO_TARGET_UNIT",()->send(hq,d,null,List.of(a)));
    }

    @Test void sameParentAndTargetCannotRepeatButDifferentPathsRemainIndependent() {
        IntelligenceDetail d=send(hq,create(hq,List.of(a,b,c,aa),List.of(a,b)),null,List.of(a,b));
        long ra=receipt(d,a);sign(pa,d,ra);send(pa,d,ra,List.of(b));
        assertThat(service.options(pa,"forward",id(d),ra)).extracting(IntelligenceUnitOption::code).containsExactlyInAnyOrder(c.code(),aa.code());
        error("PATH_ALREADY_SHARED",()->send(pa,d,ra,List.of(b,c)));
        assertThat(service.list(pb,"received","pending",null,1,20).items().getFirst().myReceiptCount()).isEqualTo(2);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM intel_receipt r JOIN intel_send s ON s.id=r.send_id WHERE s.topic_id=?",Integer.class,id(d))).isEqualTo(3);
        send(pa,d,ra,List.of(c,aa));
        assertThat(service.detail(pa,id(d)).receipts().stream().filter(r->r.id().equals(Long.toString(ra))).findFirst().orElseThrow().allowedActions())
                .extracting(IntelligenceAction::code).doesNotContain("sign","forward","read").contains("feedbacks");
    }

    @Test void sourceAddsImmutableCorrectionsAndAdminsNeedActualReceipt() {
        IntelligenceDetail d=send(pa,create(pa,List.of(b),List.of(b)),null,List.of(b));
        error("TOPIC_NOT_FOUND",()->service.detail(hq,id(d))); // 全功能管理员没有数据授权
        assertThat(service.list(hq,"received","all",null,1,20).total()).isZero();
        error("FORBIDDEN",()->service.supplement(pb,id(d),key(),new IntelligenceSupplementRequest("SUPPLEMENT","越权")));
        service.supplement(pa,id(d),key(),new IntelligenceSupplementRequest("CORRECTION","合成时间更正"));
        assertThat(service.detail(pb,id(d)).body()).isEqualTo(d.body());
        assertThat(service.detail(pb,id(d)).supplements().getFirst().body()).isEqualTo("合成时间更正");
        assertThat(usage.hasReferences(a.id())).isTrue();assertThat(usage.hasReferences(b.id())).isTrue();assertThat(usage.hasReferences(c.id())).isFalse();
    }

    @Test void normalizedReplayNeverDuplicatesAndStillChecksPermissions() {
        String key=key(); IntelligenceDraftRequest input=input(List.of(a,b),List.of(a),0);
        IntelligenceDetail d=service.create(hq,key,input);
        assertThat(service.create(hq,key,input).id()).isEqualTo(d.id());
        error("IDEMPOTENCY_CONFLICT",()->service.create(hq,key,input(List.of(b),List.of(b),0)));
        String sendKey=key(); var request=new IntelligenceSendRequest(List.of(a.code()),"说明");
        service.send(hq,id(d),null,sendKey,request);service.send(hq,id(d),null,sendKey,request);
        assertThat(sql.queryForObject("SELECT COUNT(*) FROM intel_receipt r JOIN intel_send s ON s.id=r.send_id WHERE s.topic_id=?",Integer.class,id(d))).isEqualTo(1);
        long r=receipt(service.detail(hq,id(d)),a); String feedbackKey=key(); sign(pa,d,r);
        var feedback=new IntelligenceFeedbackRequest("线索");
        service.receiptAction(pa,id(d),r,"feedbacks",feedbackKey,feedback);service.receiptAction(pa,id(d),r,"feedbacks",feedbackKey,feedback);
        assertThat(service.detail(hq,id(d)).receipts().getFirst().feedbacks()).hasSize(1);
        Authentication reader=principal(a,List.of(PermissionCodes.INTEL_READ));
        assertThat(service.detail(reader,id(d)).receipts().getFirst().allowedActions()).isEmpty();
        error("FORBIDDEN",()->service.receiptAction(reader,id(d),r,"feedbacks",feedbackKey,feedback));
    }

    @Test void scopeOnlyUnitCannotBeDeletedAndPublishingRefreshesItsSnapshot() {
        sql.update("INSERT INTO sys_unit(unit_code,unit_name,parent_id,unit_level,area_code) VALUES('REGR_INTEL_SCOPE','合成范围大队',?,3,'330100')",a.id());session.clearCache();
        Unit leaf=unit("SELECT id,unit_code,unit_name FROM sys_unit WHERE unit_code='REGR_INTEL_SCOPE'");
        IntelligenceDetail d=create(hq,List.of(a,leaf),List.of(a));
        error("UNIT_HAS_INTELLIGENCE",()->unitService.delete(leaf.code()));
        sql.update("UPDATE sys_unit SET unit_name='合成范围大队更名' WHERE id=?",leaf.id());session.clearCache();
        send(hq,d,null,List.of(a));
        assertThat(sql.queryForObject("SELECT unit_name FROM intel_scope_unit WHERE topic_id=? AND unit_id=?",String.class,id(d),leaf.id())).isEqualTo("合成范围大队更名");
    }

    @Test void newUnitsNeverJoinFrozenScopeAndScopeOnlySourceCanInspect() {
        IntelligenceDetail d=send(hq,create(hq,List.of(a,b),List.of(a)),null,List.of(a));
        sql.update("INSERT INTO sys_unit(unit_code,unit_name,parent_id,unit_level,area_code) VALUES('REGR_INTEL_NEW','合成新增大队',?,3,'330100')",a.id());session.clearCache();
        assertThat(service.detail(hq,id(d)).scopeUnitCodes()).containsExactlyInAnyOrder(a.code(),b.code());
        long ra=receipt(service.detail(pa,id(d)),a);sign(pa,d,ra);
        assertThat(service.options(pa,"forward",id(d),ra)).extracting(IntelligenceUnitOption::code).doesNotContain("REGR_INTEL_NEW");
        error("OUTSIDE_SCOPE",()->service.send(pa,id(d),ra,key(),new IntelligenceSendRequest(List.of("REGR_INTEL_NEW"),"新增")));
    }
}
