-- owner: task/system。移除结论回流操作权限，保留全部业务历史与引用。
START TRANSACTION;
SELECT id FROM sys_role WHERE role_code = 'SYSTEM_ADMIN' FOR UPDATE;
SELECT id FROM sys_menu ORDER BY id FOR UPDATE;

UPDATE sys_user u
   SET u.authorization_version = u.authorization_version + 1
 WHERE EXISTS (
    SELECT 1 FROM sys_user_role ur
    JOIN sys_role_permission rp ON rp.role_id = ur.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE ur.user_id = u.id AND p.permission_code = 'task:handling:share-result'
 );

DELETE m FROM sys_menu m JOIN sys_permission p ON p.id = m.permission_id
 WHERE p.permission_code = 'task:handling:share-result';
DELETE rp FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
 WHERE p.permission_code = 'task:handling:share-result';
DELETE FROM sys_permission WHERE permission_code = 'task:handling:share-result';
COMMIT;

ALTER TABLE task_intel_return
    COMMENT = 'task模块：历史正式结论回流记录，功能已移除，仅保留既有数据和引用';
ALTER TABLE task_order MODIFY source_result_id BIGINT NULL
    COMMENT '历史后续任务的来源结果，引用 task_result(id)；新任务不再写入';
ALTER TABLE task_result MODIFY suggested_unit_id BIGINT NULL
    COMMENT '历史建议后续办理单位，引用 sys_unit(id)；新结果不再写入';
