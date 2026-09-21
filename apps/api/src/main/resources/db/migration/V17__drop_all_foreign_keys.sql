-- owner: 全库结构（引用完整性策略）
--
-- 目的：移除全部物理外键。引用完整性改由应用层保证，规则见 docs/rules/database.md。
-- 代价与交换：失去数据库级兜底，换来写路径对分库、批量导入、在线 DDL 与归档的自由；
--   因此写用例必须自己守住引用：
--   * 取锁顺序：授权锚点（内置角色行）→ 引用锁（sys_unit 目标行 / sys_menu 全表）→ 关系表；
--   * 删除前校验引用（单位有下级或有用户、角色有成员一律拒绝）；
--   * 悬空引用由 ReferenceIntegrityRegressionTest 巡检，结构回加由
--     SchemaForeignKeyRegressionTest 挡住（迁移文本静态扫描 + 实际库断言）。
-- 保留：关联列上的显式索引（idx_sys_unit_parent、idx_sys_user_unit、idx_sys_user_role_role、
--   idx_sys_menu_parent、idx_sys_menu_permission、idx_sys_role_permission_permission、
--   idx_sys_dict_item_type）与唯一约束全部保留，不依赖外键隐式创建的索引。
-- 历史：V2/V7/V12/V13 中的外键定义保持原文不改写，本迁移是唯一移除点。

ALTER TABLE sys_unit
    DROP FOREIGN KEY fk_sys_unit_parent;

ALTER TABLE sys_user
    DROP FOREIGN KEY fk_sys_user_unit;

ALTER TABLE sys_user_role
    DROP FOREIGN KEY fk_sys_user_role_user,
    DROP FOREIGN KEY fk_sys_user_role_role;

ALTER TABLE sys_menu
    DROP FOREIGN KEY fk_sys_menu_parent,
    DROP FOREIGN KEY fk_sys_menu_permission;

ALTER TABLE sys_role_permission
    DROP FOREIGN KEY fk_sys_role_permission_role,
    DROP FOREIGN KEY fk_sys_role_permission_permission;

ALTER TABLE sys_dict_item
    DROP FOREIGN KEY fk_sys_dict_item_type;
