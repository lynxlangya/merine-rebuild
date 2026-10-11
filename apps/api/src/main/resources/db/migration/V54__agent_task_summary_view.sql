-- 仅暴露任务统计字段；账号授权单独由部署管理员执行，不在迁移内保存密码。
-- 本视图不代表单位数据授权：首版仅限本地合成数据、单操作员固定范围。
CREATE SQL SECURITY DEFINER VIEW ai_task_summary AS
SELECT id AS task_id, issuer_unit_id, issuer_unit_name_snapshot AS issuer_unit_name,
       status, created_at AS issued_at_utc, completed_at AS completed_at_utc
FROM task_order;
