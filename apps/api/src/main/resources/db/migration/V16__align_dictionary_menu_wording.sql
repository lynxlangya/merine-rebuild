-- owner: system 模块（字典 / 菜单）
--
-- 目的：V13 引导数据里还写着「内置枚举 / 业务字典」这套已经取消的措辞，菜单说明不该再
--       暗示存在两类字典。这里把说明收敛到现在的口径：所有字典同源同形，只停用不删除。
-- 边界：只在文本与 V13 原文完全一致时更新——管理员自己改过的说明一律保留。
--       已执行的 V13 不改写，因此用新迁移收敛；V14/V15 只动权限码与菜单节点，不涉及文案。
-- 范围：字典管理页说明、新建字典按钮说明，以及对应权限码说明。

UPDATE sys_menu
   SET description = '字典类型与字典项维护'
 WHERE menu_type = 'PAGE'
   AND route_key = 'system.dictionaries'
   AND description = '内置枚举与业务字典维护';

UPDATE sys_menu m
  JOIN sys_permission p ON p.id = m.permission_id
   SET m.description = '新建字典类型与字典项'
 WHERE p.permission_code = 'system:dict:create'
   AND m.description = '新建业务字典类型与字典项';

UPDATE sys_permission
   SET description = '新建字典类型与字典项'
 WHERE permission_code = 'system:dict:create'
   AND description = '新建业务字典类型与字典项';
