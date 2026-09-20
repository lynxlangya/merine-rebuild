package com.merine.rebuild.system.permission;

/**
 * 权限码字典的一行。
 *
 * 权限码与菜单节点一一对应：分组、排序、层级都由 sys_menu 表达，字典只保留编码、
 * 名称与说明，避免同一份信息两处维护。
 */
public record PermissionSummary(long id, String code, String name, String description) {
}
