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
            new Entry("dir:maritime", null, Type.DIRECTORY, "涉海要素", null, null, 20,
                    "港口停泊点、海岛与警务基础档案"),
            new Entry("page:maritime:harbor-sites", "dir:maritime", Type.PAGE, "港口与停泊点", "maritime.harbor-sites", PermissionCodes.MARITIME_HARBOR_READ, 10, "港口与停泊点档案维护"),
            new Entry("page:maritime:islands", "dir:maritime", Type.PAGE, "海岛", "maritime.islands", PermissionCodes.MARITIME_ISLAND_READ, 20, "海岛档案维护"),
            new Entry("page:maritime:police-resources", "dir:maritime", Type.PAGE, "警务资源", "maritime.police-resources", PermissionCodes.MARITIME_POLICING_READ, 30, "警务资源档案维护"),
            new Entry("btn:maritime:port:create", "page:maritime:harbor-sites", Type.BUTTON, "新建港口", null, PermissionCodes.MARITIME__PORT_CREATE, 40, "港口 · 新建"),
            new Entry("btn:maritime:port:update", "page:maritime:harbor-sites", Type.BUTTON, "编辑与启停港口", null, PermissionCodes.MARITIME__PORT_UPDATE, 50, "港口 · 编辑与启停"),
            new Entry("btn:maritime:port:delete", "page:maritime:harbor-sites", Type.BUTTON, "删除港口", null, PermissionCodes.MARITIME__PORT_DELETE, 60, "港口 · 删除"),
            new Entry("btn:maritime:wharf:create", "page:maritime:harbor-sites", Type.BUTTON, "新建码头", null, PermissionCodes.MARITIME__WHARF_CREATE, 70, "码头 · 新建"),
            new Entry("btn:maritime:wharf:update", "page:maritime:harbor-sites", Type.BUTTON, "编辑与启停码头", null, PermissionCodes.MARITIME__WHARF_UPDATE, 80, "码头 · 编辑与启停"),
            new Entry("btn:maritime:wharf:delete", "page:maritime:harbor-sites", Type.BUTTON, "删除码头", null, PermissionCodes.MARITIME__WHARF_DELETE, 90, "码头 · 删除"),
            new Entry("btn:maritime:anchorage:create", "page:maritime:harbor-sites", Type.BUTTON, "新建锚地", null, PermissionCodes.MARITIME__ANCHORAGE_CREATE, 100, "锚地 · 新建"),
            new Entry("btn:maritime:anchorage:update", "page:maritime:harbor-sites", Type.BUTTON, "编辑与启停锚地", null, PermissionCodes.MARITIME__ANCHORAGE_UPDATE, 110, "锚地 · 编辑与启停"),
            new Entry("btn:maritime:anchorage:delete", "page:maritime:harbor-sites", Type.BUTTON, "删除锚地", null, PermissionCodes.MARITIME__ANCHORAGE_DELETE, 120, "锚地 · 删除"),
            new Entry("btn:maritime:island:create", "page:maritime:islands", Type.BUTTON, "新建海岛", null, PermissionCodes.MARITIME__ISLAND_CREATE, 130, "海岛 · 新建"),
            new Entry("btn:maritime:island:update", "page:maritime:islands", Type.BUTTON, "编辑与启停海岛", null, PermissionCodes.MARITIME__ISLAND_UPDATE, 140, "海岛 · 编辑与启停"),
            new Entry("btn:maritime:island:delete", "page:maritime:islands", Type.BUTTON, "删除海岛", null, PermissionCodes.MARITIME__ISLAND_DELETE, 150, "海岛 · 删除"),
            new Entry("btn:maritime:police-station:create", "page:maritime:police-resources", Type.BUTTON, "新建派出所", null, PermissionCodes.MARITIME__POLICE_STATION_CREATE, 160, "派出所 · 新建"),
            new Entry("btn:maritime:police-station:update", "page:maritime:police-resources", Type.BUTTON, "编辑与启停派出所", null, PermissionCodes.MARITIME__POLICE_STATION_UPDATE, 170, "派出所 · 编辑与启停"),
            new Entry("btn:maritime:police-station:delete", "page:maritime:police-resources", Type.BUTTON, "删除派出所", null, PermissionCodes.MARITIME__POLICE_STATION_DELETE, 180, "派出所 · 删除"),
            new Entry("btn:maritime:port-officer:create", "page:maritime:police-resources", Type.BUTTON, "新建民警", null, PermissionCodes.MARITIME__PORT_OFFICER_CREATE, 190, "民警 · 新建"),
            new Entry("btn:maritime:port-officer:update", "page:maritime:police-resources", Type.BUTTON, "编辑与启停民警", null, PermissionCodes.MARITIME__PORT_OFFICER_UPDATE, 200, "民警 · 编辑与启停"),
            new Entry("btn:maritime:port-officer:delete", "page:maritime:police-resources", Type.BUTTON, "删除民警", null, PermissionCodes.MARITIME__PORT_OFFICER_DELETE, 210, "民警 · 删除"),
            new Entry("dir:system", null, Type.DIRECTORY, "系统管理", null, null, 10,
                    "账号、角色、菜单与单位维护"),
            new Entry("dir:collaboration", null, Type.DIRECTORY, "业务协同", null, null, 30,
                    "任务处置与业务协同"),
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
            new Entry("page:audit", "dir:system", Type.PAGE, "审计日志",
                    "system.audit", PermissionCodes.AUDIT_READ, 60, "操作审计流水：谁在什么时候对什么对象做了什么"),
            new Entry("page:diagnostics", "dir:dev", Type.PAGE, "工程诊断", "dev.diagnostics",
                    PermissionCodes.DIAGNOSTICS_READ, 10, "页面 → API → MySQL 的最小链路"),
            new Entry("page:tasks", "dir:collaboration", Type.PAGE, "任务处置",
                    "collaboration.tasks", PermissionCodes.TASK_READ, 10, "任务受理、下发与结果交付"),

            new Entry("page:flows", "dir:collaboration", Type.PAGE, "信息流转",
                    "collaboration.flows", PermissionCodes.INTEL_READ, 20, "情报共享、签收与线索反馈"),

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
                    null, PermissionCodes.DIAGNOSTICS_WRITE, 10, "写入探针记录"),
            new Entry("btn:task:create", "page:tasks", Type.BUTTON, "新建任务",
                    null, PermissionCodes.TASK_CREATE, 10, "向直属下级创建并下发任务"),
            new Entry("btn:task:accept", "page:tasks", Type.BUTTON, "承接任务",
                    null, PermissionCodes.TASK_ACCEPT, 20, "承接本单位当前待办分支"),
            new Entry("btn:task:progress", "page:tasks", Type.BUTTON, "追加进展",
                    null, PermissionCodes.TASK_PROGRESS, 30, "追加本单位办理进展"),
            new Entry("btn:task:dispatch", "page:tasks", Type.BUTTON, "向下下发",
                    null, PermissionCodes.TASK_DISPATCH, 40, "向直属下级新增独立责任分支"),
            new Entry("btn:task:return", "page:tasks", Type.BUTTON, "退回任务",
                    null, PermissionCodes.TASK_RETURN, 50, "承接前说明原因退回原发送单位"),
            new Entry("btn:task:result", "page:tasks", Type.BUTTON, "提交结果",
                    null, PermissionCodes.TASK_SUBMIT_RESULT, 60, "提交本单位分支正式处置结果"),
            new Entry("btn:task:transfer-request", "page:tasks", Type.BUTTON, "申请支队交接",
                    null, PermissionCodes.TASK_TRANSFER_REQUEST, 70, "支队向另一支队发起责任交接申请"),
            new Entry("btn:task:transfer-respond", "page:tasks", Type.BUTTON, "回应支队交接",
                    null, PermissionCodes.TASK_TRANSFER_RESPOND, 80, "目标支队回应交接申请"),
            new Entry("btn:task:transfer-decide", "page:tasks", Type.BUTTON, "决定支队交接",
                    null, PermissionCodes.TASK_TRANSFER_DECIDE, 90, "总队批准或拒绝支队交接并确定期限"),
            new Entry("btn:intel:create", "page:flows", Type.BUTTON, "新建情报",
                    null, PermissionCodes.INTEL_CREATE, 10, "保存情报草稿"),
            new Entry("btn:intel:update", "page:flows", Type.BUTTON, "修改草稿",
                    null, PermissionCodes.INTEL_UPDATE, 20, "修改未发出情报"),
            new Entry("btn:intel:send", "page:flows", Type.BUTTON, "发送情报",
                    null, PermissionCodes.INTEL_SEND, 30, "源头单位发送情报"),
            new Entry("btn:intel:sign", "page:flows", Type.BUTTON, "单位签收",
                    null, PermissionCodes.INTEL_SIGN, 40, "签收本单位一次接收记录"),
            new Entry("btn:intel:feedback", "page:flows", Type.BUTTON, "反馈线索",
                    null, PermissionCodes.INTEL_FEEDBACK, 60, "签收后追加线索反馈"),
            new Entry("btn:intel:forward", "page:flows", Type.BUTTON, "继续共享",
                    null, PermissionCodes.INTEL_FORWARD, 70, "签收后沿合法关系继续共享"),
            new Entry("btn:intel:supplement", "page:flows", Type.BUTTON, "补充更正",
                    null, PermissionCodes.INTEL_SUPPLEMENT, 80, "源头追加补充或更正说明"),
            new Entry("btn:intel:assess", "page:flows", Type.BUTTON, "记录研判",
                    null, PermissionCodes.INTEL_ASSESS, 90, "签收后记录本单位研判"),
            new Entry("btn:intel:create-task", "page:flows", Type.BUTTON, "发起任务",
                    null, PermissionCodes.INTEL_CREATE_TASK, 100, "结合本单位研判向直属下级发任务"));

    public List<Entry> entries() {
        return ENTRIES;
    }
}
