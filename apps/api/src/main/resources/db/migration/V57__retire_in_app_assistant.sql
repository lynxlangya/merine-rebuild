-- owner: system/agent。移除已废弃的站内助手：助手相关的权限码、菜单节点与库表。
-- 独立 Agent 的受控查询链路（ai_data_source*）不受影响；历史迁移与审计流水保持原样。
-- 删除顺序：角色授权 → 菜单节点（按权限码与遗留 route key）→ 权限码 → 数据表。

-- 1. 解除角色对这些权限码的授权。
DELETE rp
  FROM sys_role_permission rp
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE p.permission_code IN ('agent:chat:use', 'agent:provider:read', 'agent:provider:create',
                             'agent:provider:update', 'agent:provider:delete');

-- 2. 删除引用这些权限码的菜单节点（含用户后来手工添加的节点）。
DELETE m
  FROM sys_menu m
  JOIN sys_permission p ON p.id = m.permission_id
 WHERE p.permission_code IN ('agent:chat:use', 'agent:provider:read', 'agent:provider:create',
                             'agent:provider:update', 'agent:provider:delete');

-- 兜底：V43 已移除引导创建的“模型供应商”页，这里清掉任何遗留页面（普通删除，无级联）。
DELETE FROM sys_menu WHERE route_key = 'agent.providers';

-- 3. 删除权限码本身；「智能体管理」目录由数据源管理/查询范围继续使用，不在此删除。
DELETE FROM sys_permission
 WHERE permission_code IN ('agent:chat:use', 'agent:provider:read', 'agent:provider:create',
                           'agent:provider:update', 'agent:provider:delete');

-- 4. 删除站内助手的会话、运行与模型供应商表；数据不迁移到独立 Agent（其会话在 DSH_HOME）。
DROP TABLE IF EXISTS ai_chat_run;
DROP TABLE IF EXISTS ai_conversation_message;
DROP TABLE IF EXISTS ai_conversation;
DROP TABLE IF EXISTS ai_model_provider_model;
DROP TABLE IF EXISTS ai_model_provider;
