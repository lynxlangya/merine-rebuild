package com.merine.rebuild.system.permission;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 权限模块注册表（代码侧清单）。
 *
 * 权限码按模块命名空间组织：`<模块>:<资源>:<动作>`，第一段就是模块。菜单只是权限资源的
 * 一种呈现方式，不挂菜单的权限码（例如智能体：它有自己的外壳，不占后台菜单）在角色管理的
 * 权限勾选树里**按模块各自成组**，组名取自这里——不做跨模块的「其他权限」合并分组，
 * 否则不同模块的东西混在一起，既看不出归属，也容易把死码藏起来。
 *
 * 显示名与顺序是结构性清单，和 {@code RegisteredIcons}、{@code RegisteredRoutes} 同类，
 * 放代码里维护；回归会断言库里不存在未登记前缀，新增模块必须在这里登记。
 */
public final class PermissionModules {

    /** 模块：前缀、显示名、权限勾选树里的排序（菜单树之后按此升序）。 */
    public record Module(String code, String name, int sortOrder) {}

    private static final List<Module> ALL = List.of(
            new Module("system", "系统管理", 100),
            new Module("maritime", "涉海要素", 200),
            new Module("task", "任务处置", 300),
            new Module("intelligence", "情报与信息流转", 400),
            new Module("agent", "智能体", 900));

    private static final Map<String, Module> BY_CODE = index();

    private PermissionModules() {}

    /** 权限码的模块名：取第一个 `:` 之前的前缀；没有前缀时整段都是模块名。 */
    public static String codeOf(String permissionCode) {
        int separator = permissionCode.indexOf(':');
        return separator > 0 ? permissionCode.substring(0, separator) : permissionCode;
    }

    public static Optional<Module> find(String moduleCode) {
        return Optional.ofNullable(BY_CODE.get(moduleCode));
    }

    /** 已登记的模块名；未登记时显式标出来，界面能看出「这里少了一次登记」。 */
    public static String nameOf(String moduleCode) {
        return find(moduleCode).map(Module::name).orElse("未登记模块（" + moduleCode + "）");
    }

    /** 未登记模块排在已登记之后，组间顺序稳定。 */
    public static int sortOrderOf(String moduleCode) {
        return find(moduleCode).map(Module::sortOrder).orElse(10_000);
    }

    /** 供回归使用：库里出现的模块前缀都必须登记。 */
    public static boolean isRegistered(String moduleCode) {
        return BY_CODE.containsKey(moduleCode);
    }

    private static Map<String, Module> index() {
        Map<String, Module> index = new LinkedHashMap<>();
        ALL.forEach(module -> index.put(module.code(), module));
        return Map.copyOf(index);
    }
}
