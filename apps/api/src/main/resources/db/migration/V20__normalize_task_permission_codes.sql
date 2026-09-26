-- 与既有「模块:资源:动作」权限码格式一致；保留 sys_permission.id，角色授权和菜单关联不丢失。
UPDATE sys_permission
   SET permission_code = REPLACE(permission_code, 'task:', 'task:handling:')
 WHERE permission_code IN (
    'task:read', 'task:create', 'task:accept', 'task:progress', 'task:dispatch',
    'task:return', 'task:submit-result', 'task:transfer-request',
    'task:transfer-respond', 'task:transfer-decide'
 );
