-- owner: agent.datasource。首批业务查询视图：任务处置、信息流转、涉海要素。
-- 只读视图由 Flyway 迁移负责定义；数据库账号授权与查询范围/说明配置另行由部署管理员执行。
-- 视图内完成关联与预聚合，Agent 只能查单个允许视图，不开放底表、JOIN、写 SQL 或 DDL。
-- 说明：本批视图不含正文、结论、联系方式、登录名与内部样例键；单位名称列是事件时点快照，
-- 不代表当前实体名称，也不能当作精确筛选条件。
-- 引用同库的 sys_dict_type/sys_dict_item 只为提供可维护的中文标签（SQL SECURITY DEFINER）。

-- 任务汇总：一项任务一行。保留 V54 已有字段与语义，补充期限、责任展示和分支统计。
CREATE OR REPLACE SQL SECURITY DEFINER VIEW ai_task_summary AS
SELECT t.id AS task_id,
       t.issuer_unit_id AS issuer_unit_id,
       t.issuer_unit_name_snapshot AS issuer_unit_name,
       t.status AS status,
       t.created_at AS issued_at_utc,
       t.completed_at AS completed_at_utc,
       t.task_no AS task_no,
       t.title AS title,
       t.initial_due_at AS initial_due_at_utc,
       t.current_due_at AS current_due_at_utc,
       (SELECT GROUP_CONCAT(DISTINCT a.to_unit_name_snapshot ORDER BY a.to_unit_name_snapshot SEPARATOR '、')
          FROM task_branch b
          JOIN task_assignment a ON a.id = b.current_assignment_id
         WHERE b.task_id = t.id AND b.status = 'OPEN'
           AND a.status IN ('PENDING_ACCEPT', 'IN_PROGRESS')) AS current_responsible_units,
       (SELECT COUNT(*) FROM task_branch b
         WHERE b.task_id = t.id AND b.status = 'OPEN') AS open_branch_count,
       (SELECT COUNT(*) FROM task_branch b
          JOIN task_assignment a ON a.id = b.current_assignment_id
         WHERE b.task_id = t.id AND b.status = 'OPEN'
           AND a.status IN ('PENDING_ACCEPT', 'IN_PROGRESS')
           AND a.due_at < UTC_TIMESTAMP(6)) AS overdue_branch_count,
       (SELECT MAX(h.occurred_at) FROM task_action h
         WHERE h.task_id = t.id) AS last_action_at_utc
  FROM task_order t;

-- 任务分支：一条责任分支一行。当前承办只取活跃承办段（PENDING_ACCEPT/IN_PROGRESS），
-- 已答复或已撤回的分支当前承办字段为 NULL，历史承办段不混入。
CREATE SQL SECURITY DEFINER VIEW ai_task_branch AS
SELECT b.id AS branch_id,
       b.parent_branch_id AS parent_branch_id,
       b.task_id AS task_id,
       t.task_no AS task_no,
       t.title AS task_title,
       b.status AS branch_status,
       dbs.item_label AS branch_status_label,
       a.to_unit_id AS current_unit_id,
       a.to_unit_name_snapshot AS current_unit_name,
       a.status AS current_assignment_status,
       a.accepted_at AS accepted_at_utc,
       a.due_at AS due_at_utc,
       b.completed_at AS completed_at_utc,
       r.outcome_code AS result_outcome_code,
       r.submitted_at AS result_submitted_at_utc
  FROM task_branch b
  JOIN task_order t ON t.id = b.task_id
  LEFT JOIN task_assignment a ON a.id = b.current_assignment_id
       AND a.status IN ('PENDING_ACCEPT', 'IN_PROGRESS')
  LEFT JOIN task_result r ON r.branch_id = b.id
  LEFT JOIN sys_dict_type dbt ON dbt.dict_code = 'task.branch.status'
  LEFT JOIN sys_dict_item dbs ON dbs.dict_type_id = dbt.id AND dbs.item_value = b.status;

-- 信息主档：一条原始信息一行，草稿与已发出都在内，published_at_utc 为首次发布时刻。
CREATE SQL SECURITY DEFINER VIEW ai_intel_topic AS
SELECT t.id AS topic_id,
       t.topic_no AS topic_no,
       t.title AS title,
       t.status AS status,
       t.source_unit_id AS origin_unit_id,
       t.source_unit_name AS origin_unit_name,
       t.created_at AS created_at_utc,
       t.published_at AS first_published_at_utc
  FROM intel_topic t;

-- 信息送达：一条接收记录一行；反馈按接收记录预聚合，不展开成多行。
-- 发送时间与签收时间分开；signed_at_utc 为空表示尚未单位签收。
CREATE SQL SECURITY DEFINER VIEW ai_intel_receipt AS
SELECT r.id AS receipt_id,
       r.send_id AS send_id,
       s.parent_receipt_id AS parent_receipt_id,
       t.id AS topic_id,
       t.topic_no AS topic_no,
       t.title AS title,
       t.source_unit_id AS origin_unit_id,
       t.source_unit_name AS origin_unit_name,
       s.from_unit_id AS sender_unit_id,
       s.from_unit_name AS sender_unit_name,
       r.to_unit_id AS receiver_unit_id,
       r.to_unit_name AS receiver_unit_name,
       s.sent_at AS sent_at_utc,
       r.signed_at AS signed_at_utc,
       CASE WHEN r.signed_at IS NULL THEN 'PENDING' ELSE 'SIGNED' END AS receipt_status,
       (SELECT COUNT(*) FROM intel_feedback f WHERE f.receipt_id = r.id) AS feedback_count,
       (SELECT MAX(f.created_at) FROM intel_feedback f WHERE f.receipt_id = r.id) AS last_feedback_at_utc
  FROM intel_receipt r
  JOIN intel_send s ON s.id = r.send_id
  JOIN intel_topic t ON t.id = s.topic_id;

-- 涉海档案：每类对象一张视图，不用一张大表混合不同对象。
CREATE SQL SECURITY DEFINER VIEW ai_port AS
SELECT p.id AS port_id,
       p.name AS name,
       p.region AS region,
       p.location AS location,
       p.purpose AS purpose,
       p.status AS status,
       ds.item_label AS status_label,
       p.created_at AS created_at_utc,
       p.updated_at AS updated_at_utc
  FROM archive_port p
  LEFT JOIN sys_dict_type dt ON dt.dict_code = 'common.status'
  LEFT JOIN sys_dict_item ds ON ds.dict_type_id = dt.id AND ds.item_value = p.status;

CREATE SQL SECURITY DEFINER VIEW ai_anchorage AS
SELECT a.id AS anchorage_id,
       a.name AS name,
       a.region AS region,
       a.location AS location,
       a.purpose AS purpose,
       a.status AS status,
       ds.item_label AS status_label,
       a.created_at AS created_at_utc,
       a.updated_at AS updated_at_utc
  FROM archive_anchorage a
  LEFT JOIN sys_dict_type dt ON dt.dict_code = 'common.status'
  LEFT JOIN sys_dict_item ds ON ds.dict_type_id = dt.id AND ds.item_value = a.status;

CREATE SQL SECURITY DEFINER VIEW ai_island AS
SELECT i.id AS island_id,
       i.name AS name,
       i.region AS region,
       i.location AS location,
       i.inhabitation_type AS inhabitation_type,
       dl.item_label AS inhabitation_label,
       i.status AS status,
       ds.item_label AS status_label,
       i.created_at AS created_at_utc,
       i.updated_at AS updated_at_utc
  FROM archive_island i
  LEFT JOIN sys_dict_type dlt ON dlt.dict_code = 'maritime.island.inhabitation'
  LEFT JOIN sys_dict_item dl ON dl.dict_type_id = dlt.id AND dl.item_value = i.inhabitation_type
  LEFT JOIN sys_dict_type dt ON dt.dict_code = 'common.status'
  LEFT JOIN sys_dict_item ds ON ds.dict_type_id = dt.id AND ds.item_value = i.status;

CREATE SQL SECURITY DEFINER VIEW ai_police_station AS
SELECT s.id AS police_station_id,
       s.name AS name,
       s.region AS region,
       s.location AS location,
       s.status AS status,
       ds.item_label AS status_label,
       u.id AS unit_id,
       u.unit_code AS unit_code,
       u.unit_name AS unit_name,
       s.created_at AS created_at_utc,
       s.updated_at AS updated_at_utc
  FROM archive_police_station s
  LEFT JOIN sys_unit u ON u.id = s.unit_id
  LEFT JOIN sys_dict_type dt ON dt.dict_code = 'common.status'
  LEFT JOIN sys_dict_item ds ON ds.dict_type_id = dt.id AND ds.item_value = s.status;

-- 民警档案：姓名与所属单位来自关联账号（页面同源），不暴露登录名与联系方式。
CREATE SQL SECURITY DEFINER VIEW ai_port_officer AS
SELECT o.id AS officer_id,
       p.display_name AS name,
       p.id AS user_id,
       p.status AS user_status,
       ds.item_label AS user_status_label,
       u.id AS unit_id,
       u.unit_code AS unit_code,
       u.unit_name AS unit_name,
       s.id AS police_station_id,
       s.name AS police_station_name,
       s.status AS police_station_status,
       dss.item_label AS police_station_status_label,
       o.duty AS duty,
       o.status AS status,
       dso.item_label AS status_label,
       o.created_at AS created_at_utc,
       o.updated_at AS updated_at_utc
  FROM archive_port_officer o
  LEFT JOIN sys_user p ON p.id = o.user_id
  LEFT JOIN sys_unit u ON u.id = p.unit_id
  LEFT JOIN archive_police_station s ON s.id = o.police_station_id
  LEFT JOIN sys_dict_type dts ON dts.dict_code = 'common.status'
  LEFT JOIN sys_dict_item ds ON ds.dict_type_id = dts.id AND ds.item_value = p.status
  LEFT JOIN sys_dict_item dss ON dss.dict_type_id = dts.id AND dss.item_value = s.status
  LEFT JOIN sys_dict_item dso ON dso.dict_type_id = dts.id AND dso.item_value = o.status;

-- 码头：所有关联都是 LEFT JOIN；独立码头、未指定责任民警或关联对象停用时记录仍保留。
CREATE SQL SECURITY DEFINER VIEW ai_wharf AS
SELECT w.id AS wharf_id,
       w.name AS name,
       w.region AS region,
       w.location AS location,
       w.purpose AS purpose,
       w.status AS status,
       dsw.item_label AS status_label,
       w.port_id AS port_id,
       p.name AS port_name,
       p.status AS port_status,
       dsp.item_label AS port_status_label,
       w.police_station_id AS police_station_id,
       s.name AS police_station_name,
       s.status AS police_station_status,
       dss.item_label AS police_station_status_label,
       w.responsible_officer_id AS officer_id,
       ou.display_name AS officer_name,
       o.status AS officer_status,
       dso.item_label AS officer_status_label,
       w.created_at AS created_at_utc,
       w.updated_at AS updated_at_utc
  FROM archive_wharf w
  LEFT JOIN archive_port p ON p.id = w.port_id
  LEFT JOIN archive_police_station s ON s.id = w.police_station_id
  LEFT JOIN archive_port_officer o ON o.id = w.responsible_officer_id
  LEFT JOIN sys_user ou ON ou.id = o.user_id
  LEFT JOIN sys_dict_type dts ON dts.dict_code = 'common.status'
  LEFT JOIN sys_dict_item dsw ON dsw.dict_type_id = dts.id AND dsw.item_value = w.status
  LEFT JOIN sys_dict_item dsp ON dsp.dict_type_id = dts.id AND dsp.item_value = p.status
  LEFT JOIN sys_dict_item dss ON dss.dict_type_id = dts.id AND dss.item_value = s.status
  LEFT JOIN sys_dict_item dso ON dso.dict_type_id = dts.id AND dso.item_value = o.status;
