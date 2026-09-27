-- owner: task。总体结论与办结人由显式办结写入，历史数据保持 NULL。
ALTER TABLE task_order
    ADD COLUMN conclusion TEXT NULL COMMENT '总体结论，接口限 4000 字；未办结或办结流程上线前自动办结的任务为 NULL',
    ADD COLUMN closed_by_user_id BIGINT NULL COMMENT '办结人，引用 sys_user(id)，任务用例校验；未显式办结为 NULL',
    ADD INDEX idx_task_order_closed_by_user (closed_by_user_id);
ALTER TABLE task_assignment
    ADD COLUMN end_reason_code VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL
        COMMENT '退回原因编码：WRONG_TARGET 派错单位，NOT_OUR_DUTY 不属本单位职责，UNCLEAR_REQUIREMENT 要求不明确；非退回或旧记录为 NULL',
    ADD CONSTRAINT ck_task_assignment_end_reason CHECK
        (end_reason_code IS NULL OR end_reason_code IN ('WRONG_TARGET', 'NOT_OUR_DUTY', 'UNCLEAR_REQUIREMENT'));
