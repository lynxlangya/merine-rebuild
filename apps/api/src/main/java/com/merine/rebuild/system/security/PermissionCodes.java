package com.merine.rebuild.system.security;

import java.util.List;

/**
 * 功能权限码常量：代码里的唯一真源。
 *
 * 同一份清单由迁移与「恢复默认菜单」写进 sys_permission，两者必须一致——
 * 一致性由回归测试核对，而不是靠这里与 SQL 各自记忆。
 *
 * 一个权限项对应一个菜单节点：页面一个码、按钮一个码（页签同理）；
 * 目录只是导航分组，不携带权限码。
 */
public final class PermissionCodes {

    public static final String USER_READ = "system:user:read";
    public static final String USER_CREATE = "system:user:create";
    public static final String USER_UPDATE = "system:user:update";
    public static final String USER_TOGGLE_STATUS = "system:user:toggle-status";
    public static final String USER_RESET_PASSWORD = "system:user:reset-password";

    public static final String ROLE_READ = "system:role:read";
    public static final String ROLE_CREATE = "system:role:create";
    public static final String ROLE_UPDATE = "system:role:update";
    public static final String ROLE_TOGGLE_STATUS = "system:role:toggle-status";
    public static final String ROLE_DELETE = "system:role:delete";

    public static final String MENU_READ = "system:menu:read";
    public static final String MENU_CREATE = "system:menu:create";
    public static final String MENU_UPDATE = "system:menu:update";
    public static final String MENU_DELETE = "system:menu:delete";
    public static final String MENU_RESTORE = "system:menu:restore";

    public static final String UNIT_READ = "system:unit:read";
    public static final String UNIT_CREATE = "system:unit:create";
    public static final String UNIT_UPDATE = "system:unit:update";
    public static final String UNIT_DELETE = "system:unit:delete";

    public static final String DIAGNOSTICS_READ = "system:diagnostics:read";
    public static final String DIAGNOSTICS_WRITE = "system:diagnostics:write";

    public static final String DICT_READ = "system:dict:read";
    public static final String DICT_CREATE = "system:dict:create";
    public static final String DICT_UPDATE = "system:dict:update";

    public static final String TASK_READ = "task:handling:read";
    public static final String TASK_CREATE = "task:handling:create";
    public static final String TASK_ACCEPT = "task:handling:accept";
    public static final String TASK_PROGRESS = "task:handling:progress";
    public static final String TASK_DISPATCH = "task:handling:dispatch";
    public static final String TASK_RETURN = "task:handling:return";
    public static final String TASK_SUBMIT_RESULT = "task:handling:submit-result";
    public static final String TASK_TRANSFER_REQUEST = "task:handling:transfer-request";
    public static final String TASK_TRANSFER_RESPOND = "task:handling:transfer-respond";
    public static final String TASK_TRANSFER_DECIDE = "task:handling:transfer-decide";

    /**
     * 管理底线：一个账号要么持有内置管理员角色，要么**同时**持有这两个权限，
     * 才算「还能把系统管理恢复回来」——改用户角色与改角色权限是恢复入口，
     * 少了任何一半都会留下「谁也救不回来」的中间状态。
     */
    public static final List<String> ADMIN_BASELINE = List.of(USER_UPDATE, ROLE_UPDATE);

    private PermissionCodes() {
    }

    /** 全部权限码，供与迁移/恢复清单比对的一致性测试与内置角色展开使用。 */
    public static List<String> all() {
        return List.of(
                USER_READ, USER_CREATE, USER_UPDATE, USER_TOGGLE_STATUS, USER_RESET_PASSWORD,
                ROLE_READ, ROLE_CREATE, ROLE_UPDATE, ROLE_TOGGLE_STATUS, ROLE_DELETE,
                MENU_READ, MENU_CREATE, MENU_UPDATE, MENU_DELETE, MENU_RESTORE,
                UNIT_READ, UNIT_CREATE, UNIT_UPDATE, UNIT_DELETE,
                DIAGNOSTICS_READ, DIAGNOSTICS_WRITE,
                DICT_READ, DICT_CREATE, DICT_UPDATE,
                TASK_READ, TASK_CREATE, TASK_ACCEPT, TASK_PROGRESS, TASK_DISPATCH,
                TASK_RETURN, TASK_SUBMIT_RESULT, TASK_TRANSFER_REQUEST,
                TASK_TRANSFER_RESPOND, TASK_TRANSFER_DECIDE);
    }
}
