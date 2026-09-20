package com.merine.rebuild.system.menu;

import com.merine.rebuild.system.security.PermissionCodes;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 引导菜单清单：迁移写入的同一份默认结构，供「恢复默认菜单」补齐缺失项使用。
 *
 * 与迁移保持同一份内容由回归测试核对（页面 route key、按钮权限码、目录名称）。
 * 恢复只补缺失：已存在的节点与权限码一律保持原样，不覆盖用户改过的名称、排序与状态。
 */
@Component
public class MenuBootstrap {

    public enum Type {
        DIRECTORY, PAGE, BUTTON
    }

    /** 引导项：key 表达清单内部的父子关系，routeKey / permissionCode 是它对外的稳定标识。 */
    public record Entry(String key, String parentKey, Type type, String name, String routeKey,
                        String permissionCode, int sortOrder, String description) {
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry("dir:system", null, Type.DIRECTORY, "系统管理", null, null, 10,
                    "账号、角色、菜单与单位维护"),
            new Entry("dir:dev", null, Type.DIRECTORY, "开发工具", null, null, 90,
                    "本地联调与诊断入口"),

            new Entry("page:users", "dir:system", Type.PAGE, "用户管理", "system.users",
                    PermissionCodes.USER_READ, 10, "账号、所属单位与角色维护"),
            new Entry("page:roles", "dir:system", Type.PAGE, "角色管理", "system.roles",
                    PermissionCodes.ROLE_READ, 20, "角色定义与功能权限分配"),
            new Entry("page:menus", "dir:system", Type.PAGE, "菜单管理", "system.menus",
                    PermissionCodes.MENU_READ, 30, "菜单树与按钮权限维护"),
            new Entry("page:units", "dir:system", Type.PAGE, "单位管理", "system.units",
                    PermissionCodes.UNIT_READ, 40, "三级组织树维护"),
            new Entry("page:dictionaries", "dir:system", Type.PAGE, "字典管理",
                    "system.dictionaries", PermissionCodes.DICT_READ, 50, "字典类型与字典项维护"),
            new Entry("page:diagnostics", "dir:dev", Type.PAGE, "工程诊断", "dev.diagnostics",
                    PermissionCodes.DIAGNOSTICS_READ, 10, "页面 → API → MySQL 的最小链路"),

            new Entry("btn:user:create", "page:users", Type.BUTTON, "新建用户",
                    null, PermissionCodes.USER_CREATE, 10, "新建账号并设置初始密码"),
            new Entry("btn:user:update", "page:users", Type.BUTTON, "编辑用户",
                    null, PermissionCodes.USER_UPDATE, 20, "修改姓名、所属单位与角色"),
            new Entry("btn:user:toggle", "page:users", Type.BUTTON, "启停账号",
                    null, PermissionCodes.USER_TOGGLE_STATUS, 30, "启用或停用账号"),
            new Entry("btn:user:reset", "page:users", Type.BUTTON, "重置密码",
                    null, PermissionCodes.USER_RESET_PASSWORD, 40, "为账号设置新密码"),
            new Entry("btn:role:create", "page:roles", Type.BUTTON, "新建角色",
                    null, PermissionCodes.ROLE_CREATE, 10, "新建角色"),
            new Entry("btn:role:update", "page:roles", Type.BUTTON, "编辑角色",
                    null, PermissionCodes.ROLE_UPDATE, 20, "修改角色名称、说明与功能权限"),
            new Entry("btn:role:toggle", "page:roles", Type.BUTTON, "启停角色",
                    null, PermissionCodes.ROLE_TOGGLE_STATUS, 30, "启用或停用角色"),
            new Entry("btn:role:delete", "page:roles", Type.BUTTON, "删除角色",
                    null, PermissionCodes.ROLE_DELETE, 40, "删除无成员的角色"),
            new Entry("btn:menu:create", "page:menus", Type.BUTTON, "新增菜单项",
                    null, PermissionCodes.MENU_CREATE, 10, "新增目录、页面、页签或按钮节点"),
            new Entry("btn:menu:update", "page:menus", Type.BUTTON, "编辑菜单项",
                    null, PermissionCodes.MENU_UPDATE, 20, "修改节点名称、上级、排序与状态"),
            new Entry("btn:menu:delete", "page:menus", Type.BUTTON, "删除菜单项",
                    null, PermissionCodes.MENU_DELETE, 30, "删除节点及其子树，并解除相关授权"),
            new Entry("btn:menu:restore", "page:menus", Type.BUTTON, "恢复默认菜单",
                    null, PermissionCodes.MENU_RESTORE, 40, "补齐缺失的引导菜单与权限码"),
            new Entry("btn:unit:create", "page:units", Type.BUTTON, "新增单位",
                    null, PermissionCodes.UNIT_CREATE, 10, "新增单位"),
            new Entry("btn:unit:update", "page:units", Type.BUTTON, "编辑单位",
                    null, PermissionCodes.UNIT_UPDATE, 20, "修改单位名称、上级与行政区划"),
            new Entry("btn:unit:delete", "page:units", Type.BUTTON, "删除单位",
                    null, PermissionCodes.UNIT_DELETE, 30, "删除无下级且无用户的单位"),
            new Entry("btn:dict:create", "page:dictionaries", Type.BUTTON, "新建字典",
                    null, PermissionCodes.DICT_CREATE, 10, "新建字典类型与字典项"),
            new Entry("btn:dict:update", "page:dictionaries", Type.BUTTON, "编辑字典",
                    null, PermissionCodes.DICT_UPDATE, 20, "修改字典名称、说明、排序与启停状态"),
            new Entry("btn:diagnostics:write", "page:diagnostics", Type.BUTTON, "写入探针记录",
                    null, PermissionCodes.DIAGNOSTICS_WRITE, 10, "写入探针记录"));

    public List<Entry> entries() {
        return ENTRIES;
    }
}
