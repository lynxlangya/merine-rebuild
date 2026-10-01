-- owner: task；来源关系不授权原文，摘要随既有任务参与范围提供。
ALTER TABLE task_order ADD COLUMN background_summary TEXT NULL COMMENT '明确共享的任务背景，最多4000字符，发出后不可修改；历史普通任务为空';
CREATE TABLE task_intel_source (
 id BIGINT NOT NULL AUTO_INCREMENT COMMENT '来源关系主键',
 task_id BIGINT NOT NULL COMMENT '关联任务，引用 task_order(id)，每项最多一个来源',
 topic_id BIGINT NOT NULL COMMENT '来源情报，引用 intel_topic(id)，情报公开能力锁定校验',
 assessment_id BIGINT NOT NULL COMMENT '采用的发起单位研判，引用 intel_assessment(id)',
 last_supplement_id BIGINT NULL COMMENT '创建时最后源头说明检查点，引用 intel_supplement(id)；尚无说明为空',
 created_unit_id BIGINT NOT NULL COMMENT '建立单位，引用 sys_unit(id)',
 created_user_id BIGINT NOT NULL COMMENT '建立人，引用 sys_user(id)',
 created_unit_name VARCHAR(100) NOT NULL COMMENT '建立时单位名称快照',
 created_user_name VARCHAR(100) NOT NULL COMMENT '建立时人员名称快照',
 created_at DATETIME(6) NOT NULL COMMENT '建立时刻，UTC',
 PRIMARY KEY(id), UNIQUE KEY uk_task_intel_source_task(task_id), INDEX idx_task_intel_source_topic(topic_id,task_id),
 INDEX idx_task_intel_source_assessment(assessment_id), INDEX idx_task_intel_source_supplement(last_supplement_id),
 INDEX idx_task_intel_source_unit(created_unit_id), INDEX idx_task_intel_source_user(created_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='task模块：稳定情报来源关系，不复制原文';
INSERT INTO sys_permission(permission_code,permission_name,description) VALUES('intelligence:topic:create-task','信息流转 · 发起任务','结合本单位研判向直属下级发任务');
INSERT INTO sys_menu(parent_id,menu_type,menu_name,permission_id,sort_order,status,description)
 SELECT m.id,'BUTTON','发起任务',p.id,100,'ENABLED',p.description FROM sys_menu m JOIN sys_permission p ON p.permission_code='intelligence:topic:create-task' WHERE m.route_key='collaboration.flows';
