-- 仅供本项目本地 merine_rebuild 合成环境的跨账号验收，禁止对真实用户执行。
-- 只给已具有 TASK_DEMO 角色且账号以 demo. 开头的现有合成账号追加信息流转权限。
-- 不创建账号，不读取或修改密码；重复执行不会重复赋权或持续使会话失效。
START TRANSACTION;
SELECT id FROM sys_role WHERE role_code='SYSTEM_ADMIN' FOR UPDATE;
INSERT INTO sys_role (role_code,role_name,description)
SELECT 'INFORMATION_FLOW_DEMO','信息流转员','本地合成账号的独立信息共享功能权限'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_code='INFORMATION_FLOW_DEMO');
-- 新操作权限加入既有演示角色前，使持有者旧会话失效；重复执行没有缺失权限时不再更新。
UPDATE sys_user u JOIN sys_user_role ur ON ur.user_id=u.id
JOIN sys_role r ON r.id=ur.role_id AND r.role_code='INFORMATION_FLOW_DEMO'
SET u.authorization_version=u.authorization_version+1
WHERE u.login_name LIKE 'demo.%' AND EXISTS (
 SELECT 1 FROM sys_permission p
 WHERE p.permission_code LIKE 'intelligence:topic:%'
 AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=r.id AND rp.permission_id=p.id)
);
INSERT INTO sys_role_permission (role_id,permission_id)
SELECT r.id,p.id FROM sys_role r JOIN sys_permission p
 ON p.permission_code LIKE 'intelligence:topic:%'
WHERE r.role_code='INFORMATION_FLOW_DEMO' AND NOT EXISTS (SELECT 1 FROM sys_role_permission existing WHERE existing.role_id=r.id AND existing.permission_id=p.id);
UPDATE sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role existing ON existing.id=ur.role_id AND existing.role_code='TASK_DEMO'
JOIN sys_role target ON target.role_code='INFORMATION_FLOW_DEMO'
SET u.authorization_version=u.authorization_version+1
WHERE u.login_name LIKE 'demo.%' AND NOT EXISTS (SELECT 1 FROM sys_user_role assigned WHERE assigned.user_id=u.id AND assigned.role_id=target.id);
INSERT INTO sys_user_role (user_id,role_id)
SELECT u.id,target.id FROM sys_user u JOIN sys_user_role ur ON ur.user_id=u.id JOIN sys_role existing ON existing.id=ur.role_id AND existing.role_code='TASK_DEMO'
JOIN sys_role target ON target.role_code='INFORMATION_FLOW_DEMO'
WHERE u.login_name LIKE 'demo.%' AND NOT EXISTS (SELECT 1 FROM sys_user_role assigned WHERE assigned.user_id=u.id AND assigned.role_id=target.id);
COMMIT;
