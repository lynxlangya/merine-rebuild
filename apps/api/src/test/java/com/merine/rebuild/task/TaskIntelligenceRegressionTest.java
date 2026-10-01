package com.merine.rebuild.task;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import com.merine.rebuild.auth.AuthenticatedAccount;
import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.intelligence.*;
import com.merine.rebuild.intelligence.dto.IntelligenceRequests.*;
import com.merine.rebuild.intelligence.dto.AssessmentRequests.RecordAssessment;
import com.merine.rebuild.intelligence.dto.IntelligenceViews.IntelligenceDetail;
import com.merine.rebuild.task.dto.*;
import com.merine.rebuild.task.dto.TaskIntelligenceRequests.*;
import com.merine.rebuild.system.security.PermissionCodes;
import com.merine.rebuild.support.MockMvcRegressionSupport;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;

/** 隔离MySQL和真实Controller：情报原文、内部研判与任务参与授权不会互相授予。 */
@Transactional
class TaskIntelligenceRegressionTest extends MockMvcRegressionSupport {
 @Autowired IntelligenceService flows;
 @Autowired IntelligenceTaskAccess assessments;
 @Autowired TaskIntelligenceService integration;
 @Autowired TaskService tasks;
 record Unit(long id,String code,String name) { }
 Unit root,a,b,c,aa,bb;Authentication hq,pa,pb,pc,paa,pbb;
 @BeforeEach void fixtures() {
  root=unit("SELECT id,unit_code,unit_name FROM sys_unit WHERE unit_level=1 LIMIT 1");
  var divisions=jdbcTemplate.query("SELECT id,unit_code,unit_name FROM sys_unit WHERE parent_id=? ORDER BY id LIMIT 3",(r,n)->new Unit(r.getLong(1),r.getString(2),r.getString(3)),root.id());
  a=divisions.get(0);b=divisions.get(1);c=divisions.get(2);
  aa=unit("SELECT id,unit_code,unit_name FROM sys_unit WHERE parent_id="+a.id()+" ORDER BY id LIMIT 1");
  bb=unit("SELECT id,unit_code,unit_name FROM sys_unit WHERE parent_id="+b.id()+" ORDER BY id LIMIT 1");
  hq=principal(root,PermissionCodes.all());pa=principal(a,PermissionCodes.all());pb=principal(b,PermissionCodes.all());pc=principal(c,PermissionCodes.all());paa=principal(aa,PermissionCodes.all());pbb=principal(bb,PermissionCodes.all());
 }
 Unit unit(String query) {return jdbcTemplate.queryForObject(query,(r,n)->new Unit(r.getLong(1),r.getString(2),r.getString(3)));}
 Authentication principal(Unit u,List<String> permissions) {
  String login="regr.link."+UUID.randomUUID().toString().replace("-","");
  jdbcTemplate.update("INSERT INTO sys_user(login_name,display_name,password_hash,unit_id) VALUES(?,?,?,?)",login,"合成关联账号","synthetic-test-only",u.id());
  long id=jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE login_name=?",Long.class,login);
  return new UsernamePasswordAuthenticationToken(new AuthenticatedAccount(id,login,login,u.name(),List.of(),List.of(),permissions,0),null,permissions.stream().map(SimpleGrantedAuthority::new).toList());
 }
 static String key(){return UUID.randomUUID().toString();}
 long topic(Authentication source,List<Unit> scope,List<Unit> targets) {
  var d=flows.create(source,key(),new IntelligenceDraftRequest("合成来源情报","原始疑似线索",scope.stream().map(Unit::code).toList(),targets.stream().map(Unit::code).toList(),"",0));
  flows.send(source,Long.parseLong(d.id()),null,key(),new IntelligenceSendRequest(targets.stream().map(Unit::code).toList(),""));return Long.parseLong(d.id());
 }
 long receipt(Authentication auth,long topic) {return Long.parseLong(flows.detail(auth,topic).receipts().stream().filter(r->r.mine()).findFirst().orElseThrow().id());}
 void sign(Authentication auth,long topic) {flows.receiptAction(auth,topic,receipt(auth,topic),"sign",key(),null);}
 long up(){long t=topic(paa,List.of(a,root),List.of(a));sign(pa,t);flows.send(pa,t,receipt(pa,t),key(),new IntelligenceSendRequest(List.of(root.code()),"逐级上报"));sign(hq,t);return t;}
 CreateIntelligenceTask createInput(long topic,String assessmentId,RecordAssessment fresh,Unit target) {
  var context=integration.context(hq,topic);
  return new CreateIntelligenceTask("合成核查任务","核查定位异常原因","提交事实与疑点",Instant.now().plusSeconds(86400),List.of(target.code()),"明确共享的核查背景",assessmentId,fresh,context.sourceSupplementCheckpoint());
 }
 TaskViews.TaskDetail linked(long topic,Unit target) {return integration.create(hq,topic,key(),createInput(topic,null,new RecordAssessment(Long.toString(receipt(hq,topic)),"具备核查价值","VERIFY"),target));}
 TaskViews.TaskDetail finish(Authentication actor,TaskViews.TaskDetail d) {
  long task=Long.parseLong(d.id()),branch=Long.parseLong(tasks.detail(actor,task).branches().stream().filter(x->x.currentMine()).findFirst().orElseThrow().id());
  tasks.accept(actor,task,branch,key());return tasks.submitResult(actor,task,branch,key(),new TaskRequests.SubmitResult("NOT_FOUND","完成现场核查","未发现违法事实",null));
 }
 static void error(String code,Runnable action){assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo(code));}

 @Test void unsignedAndDraftAssessmentsAreRejectedAndLocalRecordsNeverBecomeGlobal() {
  long t=topic(paa,List.of(a,root),List.of(a));long r=receipt(pa,t);
  error("SIGN_REQUIRED",()->assessments.record(pa,t,key(),new RecordAssessment(Long.toString(r),"分析","VERIFY")));
  sign(pa,t);String k=key();var record=assessments.record(pa,t,k,new RecordAssessment(Long.toString(r),"本单位内部判断","VERIFY"));
  assertThat(assessments.record(pa,t,k,new RecordAssessment(Long.toString(r),"本单位内部判断","VERIFY")).id()).isEqualTo(record.id());
  error("IDEMPOTENCY_CONFLICT",()->assessments.record(pa,t,k,new RecordAssessment(Long.toString(r),"不同判断","VERIFY")));
  assessments.record(pa,t,key(),new RecordAssessment(Long.toString(r),"后续判断","WATCH"));
  assertThat(assessments.list(pa,t,1,20).total()).isEqualTo(2);assertThat(assessments.list(paa,t,1,20).total()).isZero();
  error("TOPIC_NOT_FOUND",()->assessments.list(pc,t,1,20));
  var draft=flows.create(hq,key(),new IntelligenceDraftRequest("草稿","正文",List.of(a.code()),List.of(a.code()),"",0));
  error("TOPIC_DRAFT",()->assessments.record(hq,Long.parseLong(draft.id()),key(),new RecordAssessment(null,"分析","VERIFY")));
 }
 @Test void forwardingSharesOnlySelectedSnapshotAndLaterAssessmentsCannotOverwriteIt() {
  long t=topic(paa,List.of(a,root),List.of(a));sign(pa,t);long r=receipt(pa,t);
  var record=assessments.record(pa,t,key(),new RecordAssessment(Long.toString(r),"不能直接公开的完整判断","VERIFY"));
  flows.send(pa,t,r,key(),new IntelligenceSendRequest(List.of(root.code()),"上报",record.id(),"明确提供给总队的摘要"));
  assessments.record(pa,t,key(),new RecordAssessment(Long.toString(r),"后来不同判断","NO_ACTION"));
  assertThat(flows.detail(hq,t).receipts()).extracting(x->x.assessmentSummary()).contains("明确提供给总队的摘要");
  assertThat(assessments.list(hq,t,1,20).total()).isZero();
 }
 @Test void createsWithAssessmentOnceAndSummaryOnlyUnitsCanDispatchWithoutOriginalAccess() {
  long t=up();String k=key();var input=createInput(t,null,new RecordAssessment(Long.toString(receipt(hq,t)),"需要核查","VERIFY"),b);
  var d=integration.create(hq,t,k,input);assertThat(integration.create(hq,t,k,input).id()).isEqualTo(d.id());
  long id=Long.parseLong(d.id());assertThat(assessments.list(hq,t,1,20).total()).isEqualTo(1);
  assertThat(integration.list(paa,t,1,20).total()).isZero();assertThat(integration.list(hq,t,1,20).total()).isEqualTo(1);
  assertThat(integration.source(hq,id).adoptedAssessment().analysis()).isEqualTo("需要核查");
  var hidden=integration.source(pb,id);assertThat(hidden.backgroundSummary()).isEqualTo(input.backgroundSummary());assertThat(hidden.topicId()).isNull();assertThat(hidden.adoptedAssessment()).isNull();assertThat(hidden.sourceChanged()).isFalse();
  error("TOPIC_NOT_FOUND",()->flows.detail(pb,t));
  long branch=Long.parseLong(tasks.detail(pb,id).branches().getFirst().id());tasks.accept(pb,id,branch,key());
  tasks.dispatch(pb,id,branch,key(),new TaskRequests.Dispatch("核查现场","提交事实",Instant.now().plusSeconds(3600),List.of(bb.code())),false);
  assertThat(integration.source(pbb,id).backgroundSummary()).isEqualTo(input.backgroundSummary());
  assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM intel_receipt r JOIN intel_send s ON s.id=r.send_id WHERE s.topic_id=? AND r.to_unit_id IN (?,?)",Integer.class,t,b.id(),bb.id())).isZero();
 }
 @Test void oneTopicSupportsMultipleTasksButWrongUnitAssessmentAndStaleCheckpointAreRejected() {
  long t=up();var aRecord=assessments.record(pa,t,key(),new RecordAssessment(Long.toString(receipt(pa,t)),"支队内部判断","VERIFY"));
  error("TOPIC_NOT_FOUND",()->integration.create(hq,t,key(),createInput(t,aRecord.id(),null,b)));
  var own=assessments.record(hq,t,key(),new RecordAssessment(Long.toString(receipt(hq,t)),"总队判断","VERIFY"));
  var stale=createInput(t,own.id(),null,b);flows.supplement(paa,t,key(),new IntelligenceSupplementRequest("CORRECTION","源头更正"));
  error("SOURCE_CHANGED",()->integration.create(hq,t,key(),stale));
  String k=key();var fresh=createInput(t,own.id(),null,b);var first=integration.create(hq,t,k,fresh);
  integration.create(hq,t,key(),createInput(t,own.id(),null,c));
  flows.supplement(paa,t,key(),new IntelligenceSupplementRequest("SUPPLEMENT","后续说明"));
  assertThat(integration.create(hq,t,k,fresh).id()).isEqualTo(first.id());
  assertThat(integration.source(hq,Long.parseLong(first.id())).sourceChanged()).isTrue();assertThat(integration.source(pb,Long.parseLong(first.id())).sourceChanged()).isFalse();
  assertThat(integration.list(hq,t,1,20).total()).isEqualTo(2);
 }
 @Test void formalResultsAndExplicitClosureStayInTaskWithoutReturningToIntelligence() {
  long t=up();var d=finish(pb,linked(t,b));long id=Long.parseLong(d.id());
  assertThat(d.status()).isEqualTo("AWAITING_CLOSE");
  assertThat(d.branches().getFirst().result().conclusion()).isEqualTo("未发现违法事实");
  tasks.close(hq,id,key(),new TaskRequests.Close("本轮核查完成"));
  assertThat(tasks.detail(hq,id).status()).isEqualTo("COMPLETED");
  assertThat(flows.detail(paa,t).status()).isEqualTo("PUBLISHED");
  assertThat(flows.detail(paa,t).receipts().stream().flatMap(r->r.feedbacks().stream())).isEmpty();
  assertThat(flows.detail(paa,t).supplements()).isEmpty();
  assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task_intel_return r JOIN task_intel_source s ON s.id=r.source_link_id WHERE s.task_id=?",Integer.class,id)).isZero();
  assertThat(integration.source(hq,id).topicId()).isEqualTo(Long.toString(t));
  assertThat(integration.list(hq,t,1,20).items()).extracting(x->x.id()).containsExactly(d.id());
 }
 @Test void historicalReturnTextAndTaskReferencesRemainReadable() {
  long t=up();var d=finish(pb,linked(t,b));long id=Long.parseLong(d.id());
  long receipt=receipt(hq,t);flows.receiptAction(hq,t,receipt,"feedbacks",key(),new IntelligenceFeedbackRequest("历史共享文字"));
  long feedback=jdbcTemplate.queryForObject("SELECT MAX(id) FROM intel_feedback WHERE receipt_id=?",Long.class,receipt);
  var actor=(AuthenticatedAccount)hq.getPrincipal();
  jdbcTemplate.update("INSERT INTO task_intel_return(source_link_id,source_kind,result_id,target_kind,target_receipt_id,feedback_id,shared_summary,unit_id,user_id,unit_name,user_name,idempotency_key,request_digest,created_at) SELECT s.id,'BRANCH_RESULT',?,'RECEIPT_FEEDBACK',?,?,?,s.created_unit_id,?,s.created_unit_name,? ,?,REPEAT('0',64),UTC_TIMESTAMP(6) FROM task_intel_source s WHERE s.task_id=?",
    Long.parseLong(d.branches().getFirst().result().id()),receipt,feedback,"历史共享文字",actor.userId(),"历史合成人员",key(),id);
  var historical=createInput(t,integration.source(hq,id).adoptedAssessment().id(),null,c);
  var oldTask=integration.create(hq,t,key(),historical);
  jdbcTemplate.update("UPDATE task_order SET source_result_id=? WHERE id=?",Long.parseLong(d.branches().getFirst().result().id()),Long.parseLong(oldTask.id()));
  assertThat(tasks.detail(hq,Long.parseLong(oldTask.id())).sourceResultId()).isEqualTo(d.branches().getFirst().result().id());
  tasks.close(hq,id,key(),new TaskRequests.Close("总体办结"));
  assertThat(flows.detail(paa,t).receipts().stream().flatMap(r->r.feedbacks().stream()).map(f->f.body())).containsExactly("历史共享文字");
  assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task_intel_return r JOIN task_intel_source s ON s.id=r.source_link_id WHERE s.task_id=?",Integer.class,id)).isEqualTo(1);
 }
 @Test void retiredReturnRoutesAndFollowupInputAreRejectedOverHttp() throws Exception {
  String login="regr.link.retired."+key();String password=key();
  jdbcTemplate.update("INSERT INTO sys_user(login_name,display_name,password_hash,unit_id) VALUES(?,?,?,?)",login,"合成简化HTTP账号",passwordEncoder.encode(password),root.id());
  jdbcTemplate.update("INSERT INTO sys_user_role(user_id,role_id) SELECT u.id,r.id FROM sys_user u JOIN sys_role r ON r.role_code='SYSTEM_ADMIN' WHERE u.login_name=?",login);
  var session=signIn(login,password);long t=up();var d=finish(pb,linked(t,b));long id=Long.parseLong(d.id());
  assertThat(getJson("/api/tasks/"+id+"/intelligence-return-context?sourceKind=TASK_CONCLUSION",session).getResponse().getStatus()).isEqualTo(404);
  assertThat(getJson("/api/intelligence-topics/"+t+"/task-return-references",session).getResponse().getStatus()).isEqualTo(404);
  var returned=sendJson(post("/api/tasks/"+id+"/intelligence-returns").header("Idempotency-Key",key()).content("{\"sourceKind\":\"BRANCH_RESULT\",\"resultId\":\""+d.branches().getFirst().result().id()+"\",\"sharedSummary\":\"禁止回流\"}"),session);
  assertThat(returned.getResponse().getStatus()).isEqualTo(404);
  var followup=new TaskRequests.Create("不再支持的后续任务","核查","结论",Instant.now().plusSeconds(86400),List.of(b.code()),d.branches().getFirst().result().id());
  var rejected=sendJson(post("/api/tasks").header("Idempotency-Key",key()).content(objectMapper.writeValueAsString(followup)),session);
  assertThat(rejected.getResponse().getStatus()).isEqualTo(400);
  assertThat(PermissionCodes.all()).doesNotContain("task:handling:share-result");
  assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_permission WHERE permission_code='task:handling:share-result'",Integer.class)).isZero();
  Map<String,Object> schemas=jsonOf(bodyOf(getJson("/api/openapi",session)),"$.components.schemas");
  assertThat(schemas).doesNotContainKeys("IntelligenceReturnContext","ReturnIntelligenceConclusion","ReturnedTaskReference");
  Map<String,Object> createProperties=(Map<String,Object>)((Map<String,Object>)schemas.get("Create")).get("properties");
  assertThat(createProperties).doesNotContainKey("sourceResultId");
  Map<String,Object> resultProperties=(Map<String,Object>)((Map<String,Object>)schemas.get("SubmitResult")).get("properties");
  assertThat(resultProperties).doesNotContainKey("suggestedUnitCode");
  assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM task_intel_return r JOIN task_intel_source s ON s.id=r.source_link_id WHERE s.task_id=?",Integer.class,id)).isZero();
 }
 @Test void permissionsAndAuthorizationVersionAreRecheckedBeforeWriting() {
  long t=up();var reader=principal(root,List.of(PermissionCodes.INTEL_READ,PermissionCodes.TASK_READ));
  var existingOnly=principal(root,List.of(PermissionCodes.INTEL_READ,PermissionCodes.TASK_READ,PermissionCodes.TASK_CREATE,PermissionCodes.INTEL_CREATE_TASK));
  assertThat(integration.context(existingOnly,t).canCreate()).isFalse();
  assessments.record(hq,t,key(),new RecordAssessment(Long.toString(receipt(hq,t)),"已有研判可采用","VERIFY"));
  assertThat(integration.context(existingOnly,t).canCreate()).isTrue();
  error("FORBIDDEN",()->integration.create(reader,t,key(),createInput(t,null,new RecordAssessment(Long.toString(receipt(hq,t)),"判断","VERIFY"),b)));
  long user=((AuthenticatedAccount)hq.getPrincipal()).userId();jdbcTemplate.update("UPDATE sys_user SET authorization_version=authorization_version+1 WHERE id=?",user);
  error("FORBIDDEN",()->assessments.record(hq,t,key(),new RecordAssessment(Long.toString(receipt(pa,t)),"判断","VERIFY")));
 }
 @Test void realHttpUsesStringIdsAndRejectsCsrfAndMalformedSourceChoices() throws Exception {
  String login="regr.link.http."+key();String password=key();
  jdbcTemplate.update("INSERT INTO sys_user(login_name,display_name,password_hash,unit_id) VALUES(?,?,?,?)",login,"合成HTTP账号",passwordEncoder.encode(password),root.id());
  jdbcTemplate.update("INSERT INTO sys_user_role(user_id,role_id) SELECT u.id,r.id FROM sys_user u JOIN sys_role r ON r.role_code='SYSTEM_ADMIN' WHERE u.login_name=?",login);
  var session=signIn(login,password);long t=topic(hq,List.of(a),List.of(a));
  var response=sendJson(post("/api/intelligence-topics/"+t+"/assessments").header("Idempotency-Key",key()).content("{\"analysis\":\"HTTP研判\",\"recommendationCode\":\"VERIFY\"}"),session);
  assertThat(response.getResponse().getStatus()).isEqualTo(200);assertThat((Object)jsonOf(bodyOf(response),"$.data.id")).isInstanceOf(String.class);
  Map<String,Object> assessment=jsonOf(bodyOf(response),"$.data");
  assertThat(assessment).doesNotContainKey("receiptId");
  assertThat(mockMvc.perform(post("/api/intelligence-topics/"+t+"/assessments").session(session).contentType("application/json").content("{}")).andReturn().getResponse().getStatus()).isEqualTo(403);
  var input=createInput(t,null,new RecordAssessment(null,"HTTP任务研判","VERIFY"),b);
  var created=sendJson(post("/api/intelligence-topics/"+t+"/tasks").header("Idempotency-Key",key()).content(objectMapper.writeValueAsString(input)),session);
  assertThat(created.getResponse().getStatus()).isEqualTo(200);String id=jsonOf(bodyOf(created),"$.data.id");
  Map<String,Object> source=jsonOf(bodyOf(getJson("/api/tasks/"+id+"/intelligence-source",session)),"$.data");
  assertThat(source).containsKeys("linked","backgroundSummary","topicId","sourceChanged");assertThat(source.get("topicId")).isInstanceOf(String.class);
  String handlerLogin="regr.link.handler."+key();
  jdbcTemplate.update("INSERT INTO sys_user(login_name,display_name,password_hash,unit_id) VALUES(?,?,?,?)",handlerLogin,"合成范围外HTTP账号",passwordEncoder.encode(password),b.id());
  jdbcTemplate.update("INSERT INTO sys_user_role(user_id,role_id) SELECT u.id,r.id FROM sys_user u JOIN sys_role r ON r.role_code='SYSTEM_ADMIN' WHERE u.login_name=?",handlerLogin);
  var handlerSession=signIn(handlerLogin,password);
  var maskedResponse=getJson("/api/tasks/"+id+"/intelligence-source",handlerSession);
  assertThat(maskedResponse.getResponse().getStatus()).isEqualTo(200);
  Map<String,Object> masked=jsonOf(bodyOf(maskedResponse),"$.data");
  assertThat(masked).containsEntry("sourceChanged",false)
      .doesNotContainKeys("topicId","topicNo","title","adoptedAssessment");
 }
}
