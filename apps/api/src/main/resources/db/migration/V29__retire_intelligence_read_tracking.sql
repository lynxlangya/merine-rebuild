-- 阅读记录功能退出；保留已产生的历史数据，不回写 V28，也不删除历史表。
START TRANSACTION;
SELECT id FROM sys_role WHERE role_code = 'SYSTEM_ADMIN' FOR UPDATE;
SELECT id FROM sys_menu ORDER BY id FOR UPDATE;

UPDATE sys_user u
   SET u.authorization_version = u.authorization_version + 1
 WHERE EXISTS (
    SELECT 1 FROM sys_user_role ur
    JOIN sys_role_permission rp ON rp.role_id = ur.role_id
    JOIN sys_permission p ON p.id = rp.permission_id
    WHERE ur.user_id = u.id AND p.permission_code = 'intelligence:topic:read-receipt'
 );

DELETE m FROM sys_menu m JOIN sys_permission p ON p.id = m.permission_id
 WHERE p.permission_code = 'intelligence:topic:read-receipt';
DELETE rp FROM sys_role_permission rp JOIN sys_permission p ON p.id = rp.permission_id
 WHERE p.permission_code = 'intelligence:topic:read-receipt';
DELETE FROM sys_permission WHERE permission_code = 'intelligence:topic:read-receipt';
COMMIT;

ALTER TABLE intel_read COMMENT = 'intelligence 模块：历史个人阅读记录，功能已停用，仅保留既有数据';
