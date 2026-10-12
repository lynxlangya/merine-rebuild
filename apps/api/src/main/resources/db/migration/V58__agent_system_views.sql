-- owner: agent.datasource。系统管理侧的 Agent 查询视图：用户、角色、单位与审计流水。
-- 与业务视图一致：关联与统计在视图内完成，Agent 仍只查单个允许视图。
-- 安全边界：不暴露 password_hash、会话/令牌与任何凭据；审计摘要本身已按白名单写入，不含密码或业务正文。
-- 本批字段面向本地合成数据的学习/调研，生产使用前需要按数据范围重新收窄。
-- 内置管理员角色的权限不逐条写入 sys_role_permission（登录时展开为全部权限码），
-- 相关视图与说明必须保留这一点，避免把 permission_count=0 误读为「没有权限」。

-- 用户账号：一行一个用户，含所属单位与角色摘要。
CREATE OR REPLACE SQL SECURITY DEFINER VIEW ai_sys_user AS
SELECT u.id AS user_id,
       u.login_name AS login_name,
       u.display_name AS display_name,
       u.status AS status,
       ds.item_label AS status_label,
       u.unit_id AS unit_id,
       unit.unit_code AS unit_code,
       unit.unit_name AS unit_name,
       unit.unit_level AS unit_level,
       dl.item_label AS unit_level_label,
       unit.area_code AS unit_area_code,
       unit.status AS unit_status,
       dsu.item_label AS unit_status_label,
       (SELECT COUNT(*) FROM sys_user_role ur WHERE ur.user_id = u.id) AS role_count,
       (SELECT GROUP_CONCAT(r.role_name ORDER BY r.role_name SEPARATOR '、')
          FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id
         WHERE ur.user_id = u.id) AS role_names,
       u.last_login_at AS last_login_at_utc,
       u.authorization_version AS authorization_version,
       u.created_at AS created_at_utc,
       u.updated_at AS updated_at_utc
  FROM sys_user u
  LEFT JOIN sys_unit unit ON unit.id = u.unit_id
  LEFT JOIN sys_dict_type dt ON dt.dict_code = 'common.status'
  LEFT JOIN sys_dict_item ds ON ds.dict_type_id = dt.id AND ds.item_value = u.status
  LEFT JOIN sys_dict_type dlt ON dlt.dict_code = 'system.unit.level'
  LEFT JOIN sys_dict_item dl ON dl.dict_type_id = dlt.id AND dl.item_value = CAST(unit.unit_level AS CHAR)
  LEFT JOIN sys_dict_item dsu ON dsu.dict_type_id = dt.id AND dsu.item_value = unit.status;

-- 用户角色关系：一行一个「用户 × 角色」授予，用于精确按角色筛选。
CREATE OR REPLACE SQL SECURITY DEFINER VIEW ai_sys_user_role AS
SELECT ur.user_id AS user_id,
       u.login_name AS login_name,
       u.display_name AS display_name,
       u.status AS user_status,
       u.unit_id AS unit_id,
       unit.unit_code AS unit_code,
       unit.unit_name AS unit_name,
       r.id AS role_id,
       r.role_code AS role_code,
       r.role_name AS role_name,
       r.status AS role_status,
       ds.item_label AS role_status_label,
       ur.created_at AS granted_at_utc
  FROM sys_user_role ur
  JOIN sys_user u ON u.id = ur.user_id
  JOIN sys_role r ON r.id = ur.role_id
  LEFT JOIN sys_unit unit ON unit.id = u.unit_id
  LEFT JOIN sys_dict_type dt ON dt.dict_code = 'common.status'
  LEFT JOIN sys_dict_item ds ON ds.dict_type_id = dt.id AND ds.item_value = r.status;

-- 角色定义：一行一个角色，含成员数与显式授予的权限数。
CREATE OR REPLACE SQL SECURITY DEFINER VIEW ai_sys_role AS
SELECT r.id AS role_id,
       r.role_code AS role_code,
       r.role_name AS role_name,
       r.description AS description,
       r.status AS status,
       ds.item_label AS status_label,
       (SELECT COUNT(*) FROM sys_user_role ur WHERE ur.role_id = r.id) AS member_count,
       (SELECT COUNT(*) FROM sys_role_permission rp WHERE rp.role_id = r.id) AS permission_count,
       r.version AS version,
       r.created_at AS created_at_utc,
       r.updated_at AS updated_at_utc
  FROM sys_role r
  LEFT JOIN sys_dict_type dt ON dt.dict_code = 'common.status'
  LEFT JOIN sys_dict_item ds ON ds.dict_type_id = dt.id AND ds.item_value = r.status;

-- 角色权限关系：一行一个「角色 × 权限码」，含权限归属模块与绑定的菜单节点名。
CREATE OR REPLACE SQL SECURITY DEFINER VIEW ai_sys_role_permission AS
SELECT rp.role_id AS role_id,
       r.role_code AS role_code,
       r.role_name AS role_name,
       r.status AS role_status,
       p.id AS permission_id,
       p.permission_code AS permission_code,
       p.permission_name AS permission_name,
       p.description AS permission_description,
       SUBSTRING_INDEX(p.permission_code, ':', 1) AS module_code,
       (SELECT GROUP_CONCAT(m.menu_name ORDER BY m.id SEPARATOR '、')
          FROM sys_menu m WHERE m.permission_id = p.id) AS menu_names,
       rp.created_at AS granted_at_utc
  FROM sys_role_permission rp
  JOIN sys_role r ON r.id = rp.role_id
  JOIN sys_permission p ON p.id = rp.permission_id;

-- 单位组织：一行一个单位，含上级、层级标签与直属下级/用户数。
CREATE OR REPLACE SQL SECURITY DEFINER VIEW ai_sys_unit AS
SELECT u.id AS unit_id,
       u.unit_code AS unit_code,
       u.unit_name AS unit_name,
       u.unit_level AS unit_level,
       dl.item_label AS unit_level_label,
       u.area_code AS area_code,
       u.status AS status,
       ds.item_label AS status_label,
       u.parent_id AS parent_id,
       parent.unit_code AS parent_code,
       parent.unit_name AS parent_name,
       (SELECT COUNT(*) FROM sys_unit c WHERE c.parent_id = u.id) AS child_count,
       (SELECT COUNT(*) FROM sys_user su WHERE su.unit_id = u.id) AS user_count,
       u.version AS version,
       u.created_at AS created_at_utc,
       u.updated_at AS updated_at_utc
  FROM sys_unit u
  LEFT JOIN sys_unit parent ON parent.id = u.parent_id
  LEFT JOIN sys_dict_type dt ON dt.dict_code = 'common.status'
  LEFT JOIN sys_dict_item ds ON ds.dict_type_id = dt.id AND ds.item_value = u.status
  LEFT JOIN sys_dict_type dlt ON dlt.dict_code = 'system.unit.level'
  LEFT JOIN sys_dict_item dl ON dl.dict_type_id = dlt.id AND dl.item_value = CAST(u.unit_level AS CHAR);

-- 操作审计流水：一行一条追加记录；不含密码、密钥与业务正文。
CREATE OR REPLACE SQL SECURITY DEFINER VIEW ai_audit_log AS
SELECT a.id AS audit_id,
       a.occurred_at AS occurred_at_utc,
       a.created_at AS stored_at_utc,
       a.actor_user_id AS actor_user_id,
       a.actor_login AS actor_login,
       a.actor_name AS actor_name,
       a.actor_unit_name AS actor_unit_name,
       a.module AS module,
       a.action AS action,
       a.result AS result,
       CASE CONVERT(a.result USING utf8mb4)
            WHEN _utf8mb4'SUCCEEDED' THEN _utf8mb4'成功'
            WHEN _utf8mb4'FAILED' THEN _utf8mb4'失败'
            ELSE CONVERT(a.result USING utf8mb4) END AS result_label,
       a.target_type AS target_type,
       a.target_id AS target_id,
       a.target_label AS target_label,
       a.summary AS summary,
       a.request_id AS request_id,
       a.client_ip AS client_ip
  FROM sys_audit_log a;
