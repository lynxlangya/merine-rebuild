-- 助手对话使用权限：先只授给内置管理员角色（登录时展开为全部权限码）。
-- 暂不建菜单节点：权限码只用于接口判定，助手入口在独立页面，不进入后台导航树。
INSERT INTO sys_permission(permission_code, permission_name, description)
VALUES ('agent:chat:use', '海防助手 · 使用对话', '发起对话与追问；真实模型调用会产生费用');
