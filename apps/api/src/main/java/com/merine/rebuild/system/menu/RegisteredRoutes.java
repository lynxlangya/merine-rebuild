package com.merine.rebuild.system.menu;

import java.util.List;

/**
 * 前端已注册的路由 key 清单（代码侧注册表）。
 *
 * 菜单里的页面节点只能绑定这里存在的 key：数据库可以决定「要不要显示、排第几、叫什么」，
 * 但决定不了「这个页面存在不存在」——新增页面仍然要前端发版并在这里登记。
 * 前端 app/routeRegistry.tsx 保存同一组 key 到页面组件与图标的映射；
 * 两端不一致时，前端会跳过未注册的 key 并在控制台告警。
 */
public final class RegisteredRoutes {

    public record Route(String key, String label) {
    }

    private static final List<Route> ALL = List.of(
            new Route("system.users", "用户管理"),
            new Route("system.roles", "角色管理"),
            new Route("system.menus", "菜单管理"),
            new Route("system.units", "单位管理"),
            new Route("system.dictionaries", "字典管理"),
            new Route("dev.diagnostics", "工程诊断"));

    private RegisteredRoutes() {
    }

    public static List<Route> all() {
        return ALL;
    }

    public static boolean contains(String routeKey) {
        return ALL.stream().anyMatch(route -> route.key().equals(routeKey));
    }
}
