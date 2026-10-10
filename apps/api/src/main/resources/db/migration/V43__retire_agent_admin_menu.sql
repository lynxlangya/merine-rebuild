-- 模型供应商已迁到助手设置（`/agent/settings`），后台不再保留“智能体管理 → 模型供应商”菜单。
-- 只删除引导创建的节点：三个按钮 → 页面 → 空目录；权限码保留（助手设置页仍按 agent:provider:* 判定），
-- 用户自行加在“智能体管理”下的子节点会让目录保留，不被连带删除。
DELETE FROM sys_menu
 WHERE parent_id IN (SELECT id FROM (SELECT id FROM sys_menu WHERE route_key = 'agent.providers') AS page);

DELETE FROM sys_menu WHERE route_key = 'agent.providers';

DELETE FROM sys_menu
 WHERE menu_type = 'DIRECTORY'
   AND menu_name = '智能体管理'
   AND parent_id IS NULL
   AND NOT EXISTS (SELECT 1 FROM (SELECT parent_id FROM sys_menu) AS child WHERE child.parent_id = sys_menu.id);
