-- 清理历史遗留权限码：旧骨架页的 collaboration:task:read / collaboration:flow:read。
-- 正式页面改绑 task:handling:read / intelligence:topic:read（见 V22）之后，这两个码
-- 在菜单、Java 判定和前端都已无任何引用；留在权限表里只会出现在角色管理的
-- 「其他权限（未挂在菜单上）」分组里，让管理员以为它们在生效。
-- 先删授予关系，再删权限码本身。
DELETE rp
  FROM sys_role_permission rp
  JOIN sys_permission p ON p.id = rp.permission_id
 WHERE p.permission_code IN ('collaboration:task:read', 'collaboration:flow:read');

DELETE FROM sys_permission
 WHERE permission_code IN ('collaboration:task:read', 'collaboration:flow:read');
