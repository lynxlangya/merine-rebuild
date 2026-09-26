-- 旧骨架页沿用 collaboration:task:read，正式任务 API 使用 task:handling:read。
-- 让页面导航与接口读取使用同一个权限码；保留旧权限记录及既有角色授权。
UPDATE sys_menu m
  JOIN sys_permission previous ON previous.id = m.permission_id
  JOIN sys_permission current_read ON current_read.permission_code = 'task:handling:read'
   SET m.permission_id = current_read.id
 WHERE m.route_key = 'collaboration.tasks'
   AND previous.permission_code = 'collaboration:task:read';
