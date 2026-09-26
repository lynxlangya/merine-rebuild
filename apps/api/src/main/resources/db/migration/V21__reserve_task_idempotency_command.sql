-- 幂等键在业务写入前占位，同行并发请求等待唯一键锁；事务提交前必须填入 task_id。
-- 失败事务整体回滚，不留下可见的空结果占位行。
ALTER TABLE task_command
    MODIFY COLUMN task_id BIGINT NULL COMMENT '幂等结果所属任务；事务内预占位可暂空，提交前由用例填入，引用 task_order(id)';
