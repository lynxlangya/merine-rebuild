-- owner: intelligence；研判只追加，分享的是本次明确选定的摘要。
CREATE TABLE intel_assessment (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '研判主键，API 返回字符串',
 topic_id BIGINT NOT NULL COMMENT '所属情报，引用 intel_topic(id)，锁定校验',
 receipt_id BIGINT NULL COMMENT '研判依据的本单位已签回执，引用 intel_receipt(id)；源头为空',
 analysis TEXT NOT NULL COMMENT '本单位研判说明，最多4000字符，不自动公开',
 recommendation_code VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '建议行动：VERIFY/HANDLE/WATCH/NO_ACTION',
 unit_id BIGINT NOT NULL COMMENT '研判单位，引用 sys_unit(id)',
 user_id BIGINT NOT NULL COMMENT '记录人，引用 sys_user(id)',
 unit_name VARCHAR(100) NOT NULL COMMENT '记录时单位名称快照',
 user_name VARCHAR(100) NOT NULL COMMENT '记录时人员名称快照',
 idempotency_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '同单位同意图请求键',
 request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范化命令SHA256摘要',
 created_at DATETIME(6) NOT NULL COMMENT '记录时刻，UTC',
 PRIMARY KEY(id), UNIQUE KEY uk_intel_assessment_intent(unit_id,idempotency_key),
 INDEX idx_intel_assessment_history(topic_id,unit_id,id), INDEX idx_intel_assessment_receipt(receipt_id),
 INDEX idx_intel_assessment_user(user_id),
 CONSTRAINT ck_intel_assessment_recommendation CHECK(recommendation_code IN ('VERIFY','HANDLE','WATCH','NO_ACTION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='intelligence模块：本单位追加式研判';
ALTER TABLE intel_send
 ADD COLUMN assessment_id BIGINT NULL COMMENT '本次引用的发送单位研判，引用 intel_assessment(id)',
 ADD COLUMN assessment_summary TEXT NULL COMMENT '本次明确共享的研判摘要，最多4000字符，发送后不改写',
 ADD INDEX idx_intel_send_assessment(assessment_id),
 ADD CONSTRAINT ck_intel_send_assessment CHECK((assessment_id IS NULL AND assessment_summary IS NULL) OR (assessment_id IS NOT NULL AND assessment_summary IS NOT NULL));
INSERT INTO sys_dict_type(dict_code,dict_name,description) VALUES('intelligence.assessment.recommendation','研判建议行动','建议不是审批结论，不自动产生任务');
INSERT INTO sys_dict_item(dict_type_id,item_value,item_label,description,sort_order)
 SELECT t.id,s.code,s.label,s.label,s.sort_order FROM sys_dict_type t JOIN
 (SELECT 'VERIFY' code,'核查' label,10 sort_order UNION ALL SELECT 'HANDLE','跟进处置',20 UNION ALL SELECT 'WATCH','继续关注',30 UNION ALL SELECT 'NO_ACTION','暂不处置',40) s
 WHERE t.dict_code='intelligence.assessment.recommendation';
INSERT INTO sys_permission(permission_code,permission_name,description) VALUES('intelligence:topic:assess','信息流转 · 记录研判','签收后记录本单位研判');
INSERT INTO sys_menu(parent_id,menu_type,menu_name,permission_id,sort_order,status,description)
 SELECT m.id,'BUTTON','记录研判',p.id,90,'ENABLED',p.description FROM sys_menu m JOIN sys_permission p ON p.permission_code='intelligence:topic:assess' WHERE m.route_key='collaboration.flows';
