-- owner: task；手动共享正式结论，与目标反馈/补充同事务保存。
CREATE TABLE task_intel_return (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '回流记录主键',
 source_link_id BIGINT NOT NULL COMMENT '任务来源关系，引用 task_intel_source(id)',
 source_kind VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'BRANCH_RESULT分支正式结果或TASK_CONCLUSION总体结论',
 result_id BIGINT NULL COMMENT '选定正式结果，引用 task_result(id)；总体结论为空',
 target_kind VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'RECEIPT_FEEDBACK回执反馈或SOURCE_SUPPLEMENT源头补充',
 target_receipt_id BIGINT NULL COMMENT '目标已签回执，引用 intel_receipt(id)；源头补充为空',
 feedback_id BIGINT NULL COMMENT '实际追加的反馈，引用 intel_feedback(id)',
 supplement_id BIGINT NULL COMMENT '实际追加的源头补充，引用 intel_supplement(id)',
 shared_summary TEXT NOT NULL COMMENT '实际共享的结论摘要，最多4000字符',
 unit_id BIGINT NOT NULL COMMENT '共享单位，引用 sys_unit(id)',
 user_id BIGINT NOT NULL COMMENT '共享人，引用 sys_user(id)',
 unit_name VARCHAR(100) NOT NULL COMMENT '共享时单位名称快照',
 user_name VARCHAR(100) NOT NULL COMMENT '共享时人员名称快照',
 idempotency_key VARCHAR(80) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '同单位同意图请求键',
 request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范化命令SHA256摘要',
 created_at DATETIME(6) NOT NULL COMMENT '共享时刻，UTC',
 PRIMARY KEY(id), UNIQUE KEY uk_task_intel_return_intent(unit_id,idempotency_key),
 UNIQUE KEY uk_task_intel_return_feedback(feedback_id), UNIQUE KEY uk_task_intel_return_supplement(supplement_id),
 INDEX idx_task_intel_return_source(source_link_id,id), INDEX idx_task_intel_return_result(result_id),
 INDEX idx_task_intel_return_receipt(target_receipt_id), INDEX idx_task_intel_return_user(user_id),
 CONSTRAINT ck_task_intel_return_source CHECK((source_kind='BRANCH_RESULT' AND result_id IS NOT NULL) OR (source_kind='TASK_CONCLUSION' AND result_id IS NULL)),
 CONSTRAINT ck_task_intel_return_target CHECK((target_kind='RECEIPT_FEEDBACK' AND target_receipt_id IS NOT NULL AND feedback_id IS NOT NULL AND supplement_id IS NULL) OR (target_kind='SOURCE_SUPPLEMENT' AND target_receipt_id IS NULL AND feedback_id IS NULL AND supplement_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='task模块：人工选择正式结论的追加回流记录';
INSERT INTO sys_permission(permission_code,permission_name,description) VALUES('task:handling:share-result','任务处置 · 共享结论','手动将有权查看的正式结论回流来源情报');
INSERT INTO sys_menu(parent_id,menu_type,menu_name,permission_id,sort_order,status,description)
 SELECT m.id,'BUTTON','共享结论',p.id,100,'ENABLED',p.description FROM sys_menu m JOIN sys_permission p ON p.permission_code='task:handling:share-result' WHERE m.route_key='collaboration.tasks';
