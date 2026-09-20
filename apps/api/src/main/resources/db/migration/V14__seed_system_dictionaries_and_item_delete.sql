-- owner: system 模块（字典）
--
-- 目的：取消「内置枚举」这套特殊化。状态、菜单类型、单位层级三本字典改为普通字典数据，
--       和其它业务字典一样可以在线编辑标签、说明、排序、状态，字典项也可以删除；
--       同时给「删除字典项」补上权限码与菜单按钮节点。
-- 边界：
--   * 只补缺失的字典与字典项，不覆盖已有行的名称、说明、排序与状态（有人改过就保留）。
--   * 取值（dict_code / item_value）仍是稳定标识，创建后不可修改：业务数据与代码判定的
--     都是这个值，改值等于改语义。
--   * 删除字典项后，业务数据与历史行只保留原始取值；需要时按同一取值重新添加即可。
-- 范围：只写 sys_dict_type / sys_dict_item 的引导数据与一个权限码、一个菜单节点。

INSERT INTO sys_dict_type (dict_code, dict_name, description)
SELECT 'common.status', '通用状态', '用户、角色、单位与菜单共用的启停状态'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_code = 'common.status');

INSERT INTO sys_dict_type (dict_code, dict_name, description)
SELECT 'system.menu.type', '菜单类型', '菜单资源树的节点类型'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_code = 'system.menu.type');

INSERT INTO sys_dict_type (dict_code, dict_name, description)
SELECT 'system.unit.level', '单位层级', '总队、支队、大队三级'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type WHERE dict_code = 'system.unit.level');

INSERT INTO sys_dict_item (dict_type_id, item_value, item_label, description, sort_order)
SELECT t.id, seed.item_value, seed.item_label, seed.description, seed.sort_order
  FROM (
      SELECT 'common.status' AS dict_code, 'ENABLED' AS item_value, '启用' AS item_label,
             '正常可用' AS description, 10 AS sort_order
      UNION ALL SELECT 'common.status', 'DISABLED', '停用', '不可登录或不可分配，历史数据保留', 20

      UNION ALL SELECT 'system.menu.type', 'DIRECTORY', '目录', '只做导航分组，不携带权限码', 10
      UNION ALL SELECT 'system.menu.type', 'PAGE', '页面', '绑定前端已注册的 route key', 20
      UNION ALL SELECT 'system.menu.type', 'TAB', '页签', '页面内部的页签权限', 30
      UNION ALL SELECT 'system.menu.type', 'BUTTON', '按钮', '页面或页签内的操作权限', 40

      UNION ALL SELECT 'system.unit.level', '1', '总队', '一级单位，全局唯一', 10
      UNION ALL SELECT 'system.unit.level', '2', '支队', '二级单位，挂在总队下', 20
      UNION ALL SELECT 'system.unit.level', '3', '大队', '三级单位，挂在支队下', 30
  ) AS seed
  JOIN sys_dict_type t ON t.dict_code = seed.dict_code
 WHERE NOT EXISTS (
       SELECT 1 FROM sys_dict_item i
        WHERE i.dict_type_id = t.id AND i.item_value = seed.item_value
 );

-- 删除字典项是写操作，按现有约定单独一个按钮权限码（与用户/角色/菜单/单位的删除同形）
INSERT INTO sys_permission (permission_code, permission_name, description)
VALUES ('system:dict:delete', '字典管理 · 删除',
        '删除字典项；删除后业务数据与历史行只保留原始取值');

INSERT INTO sys_menu (parent_id, menu_type, menu_name, permission_id, sort_order, status,
                      description)
SELECT m.id, 'BUTTON', '删除字典项', p.id, 30, 'ENABLED', p.description
  FROM sys_menu m
  JOIN sys_permission p ON p.permission_code = 'system:dict:delete'
 WHERE m.route_key = 'system.dictionaries';
