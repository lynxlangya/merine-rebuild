-- owner: system 模块（角色）
--
-- 目的：把演示角色的编码 DEMO-ADMIN 改为可长期沿用的 SYSTEM_ADMIN。
-- 背景：用户管理接口按角色编码判定「谁能管理用户」（见 merine.security.user-admin-role-codes）。
--       角色编码是授权依据，不应带演示前缀；改名只影响编码，不改变任何授予关系
--       （sys_user_role 引用 role_id，不引用 role_code），也不改变权限语义。
-- 范围：仅更新角色编码；空库或没有该角色时影响 0 行。

UPDATE sys_role
   SET role_code = 'SYSTEM_ADMIN',
       updated_at = CURRENT_TIMESTAMP(6)
 WHERE role_code = 'DEMO-ADMIN';
