-- owner: task。保留 V19 的类型、字符集、可空性与默认值，只扩展状态及准确注释。
ALTER TABLE task_order DROP CHECK ck_task_order_status;
ALTER TABLE task_order ADD CONSTRAINT ck_task_order_status CHECK (status IN ('OPEN', 'AWAITING_CLOSE', 'COMPLETED'));
ALTER TABLE task_order MODIFY status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'OPEN'
    COMMENT '整单状态：OPEN 办理中，AWAITING_CLOSE 全部分支已答复或撤回待发起单位办结，COMPLETED 已办结';
ALTER TABLE task_order MODIFY completed_at DATETIME(6) NULL
    COMMENT '发起单位显式办结时刻，UTC；上线前自动办结时刻沿用；未办结为 NULL';

ALTER TABLE task_branch DROP CHECK ck_task_branch_status;
ALTER TABLE task_branch ADD CONSTRAINT ck_task_branch_status CHECK (status IN ('OPEN', 'COMPLETED', 'RECALLED'));
ALTER TABLE task_branch MODIFY status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'OPEN'
    COMMENT '分支状态：OPEN 办理中，COMPLETED 已有唯一正式结果，RECALLED 退回后已说明原因撤回';
ALTER TABLE task_branch MODIFY completed_at DATETIME(6) NULL
    COMMENT '正式提交分支结果的时刻，UTC；未答复或已撤回为 NULL';

ALTER TABLE task_assignment DROP CHECK ck_task_assignment_status;
ALTER TABLE task_assignment ADD CONSTRAINT ck_task_assignment_status CHECK
    (status IN ('PENDING_ACCEPT', 'IN_PROGRESS', 'RETURNED', 'TRANSFERRED', 'REASSIGNED', 'COMPLETED', 'RECALLED'));
ALTER TABLE task_assignment MODIFY status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
    COMMENT '承办状态：PENDING_ACCEPT 待承接，IN_PROGRESS 办理中，RETURNED 已退回，TRANSFERRED 已交接，REASSIGNED 已重新派发，COMPLETED 已答复，RECALLED 已撤回';
ALTER TABLE task_assignment MODIFY end_reason VARCHAR(1000) NULL
    COMMENT '结束本段的退回、交接、重派或撤回原因说明；未结束或无补充原因时为 NULL';
ALTER TABLE task_assignment MODIFY ended_at DATETIME(6) NULL
    COMMENT '退回、交接、重新派发、撤回或提交结果时刻，UTC；活跃段为 NULL';
