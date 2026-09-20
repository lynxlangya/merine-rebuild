-- owner: system 模块（字典）
--
-- 目的：撤掉「删除字典项」这个能力。字典项一旦被误删，页面的标签与选择项会立刻退化，
--       而字典是全局参考数据，错一次影响面很大——停用已经足够表达"不再可选"，
--       历史数据也仍能解析出标签。因此删除按钮、权限码与接口一起下线。
-- 边界：
--   * 只回收 V14 写入的删除权限码与「删除字典项」菜单节点，以及指向它的角色授权；
--     字典与字典项数据、其它权限码、其它菜单节点一律不动。
--   * V14 已执行，按迁移纪律不改写；本次用新迁移回退其中的删除部分。
--   * 字典类型与字典项此后都只有启停：不提供任何删除入口。

DELETE rp FROM sys_role_permission rp
 JOIN sys_permission p ON p.id = rp.permission_id
WHERE p.permission_code = 'system:dict:delete';

DELETE m FROM sys_menu m
 JOIN sys_permission p ON p.id = m.permission_id
WHERE p.permission_code = 'system:dict:delete';

DELETE FROM sys_permission WHERE permission_code = 'system:dict:delete';
