/**
 * 功能权限码清单：与后端 `system/security/PermissionCodes` 和迁移写入的 sys_permission
 * 保持同一份编码。一个权限项对应一个菜单节点（页面 / 页签 / 按钮），目录只是分组。
 *
 * 前端的判断只决定「显示什么入口与按钮」，不承担授权职责——
 * 每个受保护接口都在后端按同一份权限码独立判定。
 */
export const PERMISSIONS = {
  userRead: 'system:user:read',
  userCreate: 'system:user:create',
  userUpdate: 'system:user:update',
  userToggleStatus: 'system:user:toggle-status',
  userResetPassword: 'system:user:reset-password',

  roleRead: 'system:role:read',
  roleCreate: 'system:role:create',
  roleUpdate: 'system:role:update',
  roleToggleStatus: 'system:role:toggle-status',
  roleDelete: 'system:role:delete',

  menuRead: 'system:menu:read',
  menuCreate: 'system:menu:create',
  menuUpdate: 'system:menu:update',
  menuDelete: 'system:menu:delete',
  menuRestore: 'system:menu:restore',

  unitRead: 'system:unit:read',
  unitCreate: 'system:unit:create',
  unitUpdate: 'system:unit:update',
  unitDelete: 'system:unit:delete',

  dictRead: 'system:dict:read',
  dictCreate: 'system:dict:create',
  dictUpdate: 'system:dict:update',

  diagnosticsRead: 'system:diagnostics:read',
  diagnosticsWrite: 'system:diagnostics:write',
} as const;

export type PermissionCode = (typeof PERMISSIONS)[keyof typeof PERMISSIONS];

/** 当前身份是否持有某个权限码。权限事实来自服务端会话，不从本地存储推断。 */
export function hasPermission(
  permissionCodes: readonly string[] | undefined,
  code: PermissionCode,
): boolean {
  return permissionCodes?.includes(code) ?? false;
}

export function hasAnyPermission(
  permissionCodes: readonly string[] | undefined,
  codes: readonly PermissionCode[],
): boolean {
  return codes.some((code) => hasPermission(permissionCodes, code));
}

/** 全部权限码，供前端自检使用。 */
export const ALL_PERMISSION_CODES: readonly string[] = Object.values(PERMISSIONS);
