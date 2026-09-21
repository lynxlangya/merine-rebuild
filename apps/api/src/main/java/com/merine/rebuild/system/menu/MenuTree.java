package com.merine.rebuild.system.menu;

import com.merine.rebuild.system.menu.dto.MenuNode;
import com.merine.rebuild.system.menu.dto.NavigationNode;
import com.merine.rebuild.system.menu.persistence.MenuRow;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 菜单树的纯函数工具：组装、授权过滤与子树推导。
 *
 * 抽出来有两个原因：这些判断没有数据库依赖，可以直接用普通单元测试覆盖；
 * 管理树、导航树与删除影响三处必须用同一份「谁是谁的子节点」的口径。
 */
public final class MenuTree {

    private static final Comparator<MenuRow> SIBLING_ORDER =
            Comparator.comparingInt(MenuRow::sortOrder).thenComparingLong(MenuRow::id);

    private MenuTree() {
    }

    /** 组装完整的管理树（含停用节点），供菜单管理页与角色授权树使用。 */
    public static List<MenuNode> build(List<MenuRow> rows) {
        Map<Long, List<MenuRow>> childrenByParent = childrenByParent(rows);
        return roots(rows).stream()
                .map(row -> toMenuNode(row, childrenByParent, new HashSet<>()))
                .toList();
    }

    /**
     * 按当前账号的权限码过滤导航树。
     *
     * 规则：停用节点不下发；页面/页签/按钮必须有权限码且在授权集合里；
     * 目录本身不需要权限，但没有任何可见子节点时一并去掉。
     */
    public static List<NavigationNode> visible(List<MenuRow> rows, Collection<String> permissionCodes) {
        Set<String> granted = Set.copyOf(permissionCodes);
        Map<Long, List<MenuRow>> childrenByParent = childrenByParent(rows);
        return roots(rows).stream()
                .map(row -> toNavigationNode(row, childrenByParent, granted, new HashSet<>()))
                .filter(node -> node != null)
                .toList();
    }

    /** 子树 id（含自身），按深度从深到浅排序：删除时先删叶子，避免中途留下悬空的子节点。 */
    public static List<Long> subtreeIdsDeepestFirst(List<MenuRow> rows, long rootId) {
        Map<Long, List<MenuRow>> childrenByParent = childrenByParent(rows);
        Map<Long, Integer> depthById = new HashMap<>();
        collectDepth(childrenByParent, rootId, 0, depthById);
        List<Long> ids = new ArrayList<>(depthById.keySet());
        ids.sort(Comparator.comparingInt((Long id) -> depthById.get(id)).reversed());
        return ids;
    }

    /** candidate 是否是 ancestor 的后代（用于阻止把节点挂到自己的子树下）。 */
    public static boolean isDescendant(List<MenuRow> rows, long ancestorId, long candidateId) {
        if (ancestorId == candidateId) {
            return true;
        }
        Map<Long, Long> parentById = new HashMap<>();
        for (MenuRow row : rows) {
            parentById.put(row.id(), row.parentId());
        }
        Set<Long> visited = new HashSet<>();
        Long current = parentById.get(candidateId);
        while (current != null && visited.add(current)) {
            if (current == ancestorId) {
                return true;
            }
            current = parentById.get(current);
        }
        return false;
    }

    private static void collectDepth(Map<Long, List<MenuRow>> childrenByParent, long id, int depth,
                                     Map<Long, Integer> depthById) {
        depthById.put(id, depth);
        for (MenuRow child : childrenByParent.getOrDefault(id, List.of())) {
            collectDepth(childrenByParent, child.id(), depth + 1, depthById);
        }
    }

    private static Map<Long, List<MenuRow>> childrenByParent(List<MenuRow> rows) {
        Map<Long, List<MenuRow>> childrenByParent = new HashMap<>();
        for (MenuRow row : rows) {
            if (row.parentId() != null) {
                childrenByParent.computeIfAbsent(row.parentId(), ignored -> new ArrayList<>()).add(row);
            }
        }
        childrenByParent.values().forEach(list -> list.sort(SIBLING_ORDER));
        return childrenByParent;
    }

    private static List<MenuRow> roots(List<MenuRow> rows) {
        return rows.stream()
                .filter(row -> row.parentId() == null)
                .sorted(SIBLING_ORDER)
                .toList();
    }

    private static MenuNode toMenuNode(MenuRow row, Map<Long, List<MenuRow>> childrenByParent,
                                       Set<Long> path) {
        if (!path.add(row.id())) {
            throw new IllegalStateException("菜单树存在循环，节点 id=" + row.id());
        }
        List<MenuNode> children = childrenByParent.getOrDefault(row.id(), List.of()).stream()
                .map(child -> toMenuNode(child, childrenByParent, path))
                .toList();
        path.remove(row.id());
        return new MenuNode(Long.toString(row.id()),
                row.parentId() == null ? null : Long.toString(row.parentId()),
                row.type(), row.name(), row.routeKey(), row.permissionCode(), row.description(),
                row.sortOrder(), row.status(), row.version(), row.updatedAt(), children);
    }

    private static NavigationNode toNavigationNode(MenuRow row,
                                                   Map<Long, List<MenuRow>> childrenByParent,
                                                   Set<String> granted, Set<Long> path) {
        if (!"ENABLED".equals(row.status()) || !path.add(row.id())) {
            return null;
        }
        List<NavigationNode> children = childrenByParent.getOrDefault(row.id(), List.of()).stream()
                .map(child -> toNavigationNode(child, childrenByParent, granted, path))
                .filter(node -> node != null)
                .toList();
        path.remove(row.id());

        if ("DIRECTORY".equals(row.type())) {
            return children.isEmpty()
                    ? null
                    : new NavigationNode(Long.toString(row.id()), row.name(), row.type(), null,
                            row.description(), children);
        }
        if (row.permissionCode() == null || !granted.contains(row.permissionCode())) {
            return null;
        }
        return new NavigationNode(Long.toString(row.id()), row.name(), row.type(), row.routeKey(),
                row.description(), children);
    }
}
