-- 仅整理用户管理中的本地角色显示名称；角色编码、成员与权限保持不变。
UPDATE sys_role SET role_name='任务处置员', version=version+1, updated_at=UTC_TIMESTAMP(6)
WHERE role_code='TASK_DEMO' AND role_name='任务处置演示员';
