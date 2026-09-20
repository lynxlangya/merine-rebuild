-- owner: system 模块（单位）
--
-- 目的：把当前重建库的本地演示单位从单位树中退出，同时保留演示账号可用。
-- 行为：账号先改挂真实总队并递增授权版本，已有会话在下次请求失效；
--       单位行在确认无用户后删除，不触碰旧项目或任何其他数据库。
-- 范围：只处理编码 DEMO-UNIT-A；没有该单位时影响 0 行。

UPDATE sys_user u
  JOIN sys_unit demo ON demo.id = u.unit_id AND demo.unit_code = 'DEMO-UNIT-A'
  JOIN sys_unit root ON root.unit_code = 'ORG_001'
   SET u.unit_id = root.id,
       u.authorization_version = u.authorization_version + 1,
       u.updated_at = CURRENT_TIMESTAMP(6);

DELETE FROM sys_unit
 WHERE unit_code = 'DEMO-UNIT-A';
