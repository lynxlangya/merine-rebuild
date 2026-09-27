-- owner: task。按 V19 种子方式只补缺失；状态项停用不影响流程和历史显示。

INSERT INTO sys_dict_type (dict_code, dict_name, description)
SELECT 'task.order.status', '任务整单状态', '流程状态的显示标签，启停不改变流程判定'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_code = 'task.order.status');

INSERT INTO sys_dict_item (dict_type_id, item_value, item_label, sort_order)
SELECT t.id, seed.item_value, seed.item_label, seed.sort_order
  FROM (SELECT 'OPEN' AS item_value, '办理中' AS item_label, 10 AS sort_order
        UNION ALL SELECT 'AWAITING_CLOSE', '待办结', 20
        UNION ALL SELECT 'COMPLETED', '已办结', 30) seed
  JOIN sys_dict_type t ON t.dict_code = 'task.order.status'
 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_type_id = t.id AND i.item_value = seed.item_value);

INSERT INTO sys_dict_type (dict_code, dict_name, description)
SELECT 'task.branch.status', '任务分支状态', '流程状态的显示标签，启停不改变流程判定'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_code = 'task.branch.status');

INSERT INTO sys_dict_item (dict_type_id, item_value, item_label, sort_order)
SELECT t.id, seed.item_value, seed.item_label, seed.sort_order
  FROM (SELECT 'OPEN' AS item_value, '办理中' AS item_label, 10 AS sort_order
        UNION ALL SELECT 'COMPLETED', '已答复', 20
        UNION ALL SELECT 'RECALLED', '已撤回', 30) seed
  JOIN sys_dict_type t ON t.dict_code = 'task.branch.status'
 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_type_id = t.id AND i.item_value = seed.item_value);

INSERT INTO sys_dict_type (dict_code, dict_name, description)
SELECT 'task.assignment.status', '任务承办状态', '流程状态的显示标签，启停不改变流程判定'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_code = 'task.assignment.status');

INSERT INTO sys_dict_item (dict_type_id, item_value, item_label, sort_order)
SELECT t.id, seed.item_value, seed.item_label, seed.sort_order
  FROM (SELECT 'PENDING_ACCEPT' AS item_value, '待承接' AS item_label, 10 AS sort_order
        UNION ALL SELECT 'IN_PROGRESS', '办理中', 20
        UNION ALL SELECT 'RETURNED', '已退回', 30
        UNION ALL SELECT 'TRANSFERRED', '已交接', 40
        UNION ALL SELECT 'REASSIGNED', '已重新派发', 50
        UNION ALL SELECT 'COMPLETED', '已答复', 60
        UNION ALL SELECT 'RECALLED', '已撤回', 70) seed
  JOIN sys_dict_type t ON t.dict_code = 'task.assignment.status'
 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_type_id = t.id AND i.item_value = seed.item_value);

INSERT INTO sys_dict_type (dict_code, dict_name, description)
SELECT 'task.transfer.status', '任务交接状态', '流程状态的显示标签，启停不改变流程判定'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_code = 'task.transfer.status');

INSERT INTO sys_dict_item (dict_type_id, item_value, item_label, sort_order)
SELECT t.id, seed.item_value, seed.item_label, seed.sort_order
  FROM (SELECT 'AWAITING_TARGET' AS item_value, '待接收支队确认' AS item_label, 10 AS sort_order
        UNION ALL SELECT 'AWAITING_ISSUER', '待总队审批', 20
        UNION ALL SELECT 'APPROVED', '已批准', 30
        UNION ALL SELECT 'TARGET_DECLINED', '接收支队已拒绝', 40
        UNION ALL SELECT 'ISSUER_REJECTED', '总队未批准', 50
        UNION ALL SELECT 'WITHDRAWN', '已撤回', 60) seed
  JOIN sys_dict_type t ON t.dict_code = 'task.transfer.status'
 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_type_id = t.id AND i.item_value = seed.item_value);

INSERT INTO sys_dict_type (dict_code, dict_name, description)
SELECT 'task.return.reason', '任务退回原因', '退回原因的固定取值与可选标签'
  FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_code = 'task.return.reason');

INSERT INTO sys_dict_item (dict_type_id, item_value, item_label, sort_order)
SELECT t.id, seed.item_value, seed.item_label, seed.sort_order
  FROM (SELECT 'WRONG_TARGET' AS item_value, '派错单位' AS item_label, 10 AS sort_order
        UNION ALL SELECT 'NOT_OUR_DUTY', '不属本单位职责', 20
        UNION ALL SELECT 'UNCLEAR_REQUIREMENT', '要求不明确', 30) seed
  JOIN sys_dict_type t ON t.dict_code = 'task.return.reason'
 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_type_id = t.id AND i.item_value = seed.item_value);

INSERT INTO sys_dict_item (dict_type_id, item_value, item_label, description, sort_order)
SELECT t.id, 'NOT_FOUND', '经核查未发现', '已按要求核查，未发现目标或相关情况', 15
  FROM sys_dict_type t WHERE t.dict_code = 'task.result.outcome'
   AND NOT EXISTS (SELECT 1 FROM sys_dict_item i WHERE i.dict_type_id = t.id AND i.item_value = 'NOT_FOUND');
