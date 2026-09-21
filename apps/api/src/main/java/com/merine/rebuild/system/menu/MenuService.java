package com.merine.rebuild.system.menu;

import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.system.menu.dto.MenuDeleteImpact;
import com.merine.rebuild.system.menu.dto.MenuNode;
import com.merine.rebuild.system.menu.dto.MenuRequests;
import com.merine.rebuild.system.menu.dto.NavigationNode;
import com.merine.rebuild.system.menu.dto.RestoreMenusResult;
import com.merine.rebuild.system.menu.dto.RouteKeyOption;
import com.merine.rebuild.system.menu.persistence.MenuMapper;
import com.merine.rebuild.system.menu.persistence.MenuRow;
import com.merine.rebuild.system.permission.PermissionCommands;
import com.merine.rebuild.system.permission.PermissionLookup;
import com.merine.rebuild.system.permission.PermissionSummary;
import com.merine.rebuild.system.role.RolePermissionCommands;
import com.merine.rebuild.system.user.authorization.AdminCoverageGuard;
import com.merine.rebuild.system.user.authorization.UserAuthorizationCommands;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 菜单管理用例。
 *
 * 菜单表只有几十行，写路径统一「读全树 → 在内存里校验层级/循环/重名 → 单行写入」，
 * 比把层级规则拆成一堆碎片 SQL 更容易核对。
 *
 * 删除节点会连带动到三样东西：子树、权限码、角色授权关系。它们在同一个事务里完成，
 * 并且先取管理底线的串行锚点——删掉某个权限码可能让「最后一个可用管理员」消失。
 */
@Service
public class MenuService {

    private static final String DIRECTORY = "DIRECTORY";
    private static final String PAGE = "PAGE";
    private static final String TAB = "TAB";
    private static final String BUTTON = "BUTTON";
    private static final String ENABLED = "ENABLED";

    private final MenuMapper mapper;
    private final PermissionLookup permissions;
    private final PermissionCommands permissionCommands;
    private final RolePermissionCommands rolePermissions;
    private final UserAuthorizationCommands authorization;
    private final AdminCoverageGuard coverage;
    private final MenuBootstrap bootstrap;

    public MenuService(MenuMapper mapper, PermissionLookup permissions,
                       PermissionCommands permissionCommands, RolePermissionCommands rolePermissions,
                       UserAuthorizationCommands authorization, AdminCoverageGuard coverage,
                       MenuBootstrap bootstrap) {
        this.mapper = mapper;
        this.permissions = permissions;
        this.permissionCommands = permissionCommands;
        this.rolePermissions = rolePermissions;
        this.authorization = authorization;
        this.coverage = coverage;
        this.bootstrap = bootstrap;
    }

    @Transactional(readOnly = true)
    public List<MenuNode> tree() {
        return MenuTree.build(mapper.findAll());
    }

    @Transactional(readOnly = true)
    public List<RouteKeyOption> routeKeys() {
        return RegisteredRoutes.all().stream()
                .map(route -> new RouteKeyOption(route.key(), route.label()))
                .toList();
    }

    /** 当前账号可见的导航树：停用节点不下发，未授权的页面/按钮不下发，空目录自动收敛。 */
    @Transactional(readOnly = true)
    public List<NavigationNode> navigation(Collection<String> permissionCodes) {
        return MenuTree.visible(mapper.findAll(), permissionCodes);
    }

    @Transactional(readOnly = true)
    public MenuDeleteImpact deleteImpact(String rawId) {
        List<MenuRow> rows = mapper.findAll();
        MenuRow root = requireRow(rows, parseId(rawId));
        List<Long> subtree = MenuTree.subtreeIdsDeepestFirst(rows, root.id());
        List<Long> permissionIds = permissionIdsIn(rows, subtree);
        return new MenuDeleteImpact(subtree.size(),
                rolePermissions.roleCodesGrantedPermissionIds(permissionIds).size());
    }

    @Transactional
    public MenuNode create(MenuRequests.CreateMenu request) {
        // 引用锁先于读树：校验依据不能落后于锁（取锁顺序见 docs/rules/database.md）
        mapper.lockAllIds();
        // 锁后用锁定读：一致性读可能停在取锁之前的快照，会把刚提交的删除看漏
        List<MenuRow> rows = mapper.findAllForShare();
        String type = request.type();
        Long parentId = parseIdOrNull(request.parentId());
        MenuRow parent = parentId == null ? null : requireRow(rows, parentId);
        requireHierarchy(parent, type);

        String routeKey = blankToNull(request.routeKey());
        String permissionCode = blankToNull(request.permissionCode());
        if (PAGE.equals(type)) {
            requireRegisteredRoute(routeKey);
            requireRouteKeyFree(rows, routeKey, null);
        } else if (routeKey != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MENU_FIELD_NOT_ALLOWED",
                    "只有页面节点可以绑定路由 key");
        }
        if (DIRECTORY.equals(type) && permissionCode != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MENU_FIELD_NOT_ALLOWED",
                    "目录只是导航分组，不携带权限码");
        }
        if (!DIRECTORY.equals(type) && permissionCode == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MENU_PERMISSION_REQUIRED",
                    "页面、页签与按钮必须填写权限码");
        }

        Long permissionId = null;
        if (permissionCode != null) {
            requirePermissionCodeFree(permissionCode);
            permissionId = ensurePermission(permissionCode, request.name(), request.description()).id();
        }
        mapper.insert(parentId, type, request.name().strip(), routeKey, permissionId,
                request.sortOrder() == null ? 0 : request.sortOrder(), ENABLED,
                blankToNull(request.description()));
        return requireNode(Long.toString(mapper.lastInsertId()));
    }

    @Transactional
    public MenuNode update(String rawId, MenuRequests.UpdateMenu request) {
        long id = parseId(rawId);
        // 引用锁先于读树：改父节点与并发删除子树必须互斥
        mapper.lockAllIds();
        List<MenuRow> rows = mapper.findAllForShare();
        MenuRow current = requireRow(rows, id);
        if (current.version() != request.version()) {
            throw versionConflict();
        }

        Long parentId = parseIdOrNull(request.parentId());
        if (parentId != null) {
            MenuRow parent = requireRow(rows, parentId);
            requireHierarchy(parent, current.type());
            if (MenuTree.isDescendant(rows, current.id(), parent.id())) {
                throw new ApiException(HttpStatus.CONFLICT, "MENU_PARENT_CYCLE",
                        "上级节点不能是自己或自己的下级");
            }
        }

        String routeKey = blankToNull(request.routeKey());
        if (PAGE.equals(current.type())) {
            requireRegisteredRoute(routeKey);
            requireRouteKeyFree(rows, routeKey, current.id());
        } else if (routeKey != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MENU_FIELD_NOT_ALLOWED",
                    "只有页面节点可以绑定路由 key");
        }

        String name = request.name().strip();
        String description = blankToNull(request.description());
        if (mapper.updateBasic(current.id(), parentId, name, routeKey, request.sortOrder(),
                request.status(), description, request.version()) == 0) {
            throw versionConflict();
        }
        // 权限名称跟着节点名称走，避免字典里显示旧名字；权限码本身不可改。
        if (current.permissionId() != null) {
            permissionCommands.rename(current.permissionId(), name, description);
        }
        return requireNode(rawId);
    }

    /**
     * 删除节点（含子树）：先解除相关角色授权并让持有者会话失效，再删节点与权限码。
     * 顺序不能反：反了会留下指向已删权限的授权关系，也说不清「谁因为这次删除失去了权限」。
     * 先取授权锚点再锁整棵菜单树：删除期间不会有新节点挂到被删子树上。
     */
    @Transactional
    public MenuDeleteImpact delete(String rawId, int version) {
        coverage.lock();
        mapper.lockAllIds();

        if (version < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VERSION",
                    "缺少菜单版本号，请刷新页面后重试");
        }
        List<MenuRow> rows = mapper.findAllForShare();
        MenuRow root = requireRow(rows, parseId(rawId));
        if (root.version() != version) {
            throw versionConflict();
        }

        List<Long> subtree = MenuTree.subtreeIdsDeepestFirst(rows, root.id());
        List<Long> permissionIds = permissionIdsIn(rows, subtree);
        List<String> affectedRoles = rolePermissions.roleCodesGrantedPermissionIds(permissionIds);
        rolePermissions.removeGrantsByPermissionIds(permissionIds);
        if (!affectedRoles.isEmpty()) {
            authorization.bumpVersionForRoleHolders(affectedRoles);
        }
        deleteDeepestFirst(rows, root.id(), subtree);
        permissionCommands.deleteByIds(permissionIds);

        coverage.requireUsableAdminRemains("不能删除最后一个可用管理员的权限");
        return new MenuDeleteImpact(subtree.size(), affectedRoles.size());
    }

    /**
     * 恢复默认菜单：只补缺失的引导节点与权限码，不删除、不覆盖已有内容。
     *
     * 目录没有权限码与 route key，只能按名称识别：如果你把默认目录改了名，
     * 恢复会新建一个默认名称的目录，可在菜单管理里再删除。
     */
    @Transactional
    public RestoreMenusResult restore() {
        int createdMenus = 0;
        int createdPermissions = 0;
        Map<String, Long> idByKey = new HashMap<>();
        // 引用锁先于读树：补齐的节点不能挂到正在被删除的父节点上
        mapper.lockAllIds();
        List<MenuRow> rows = mapper.findAllForShare();

        for (MenuBootstrap.Entry entry : bootstrap.entries()) {
            Long parentId = entry.parentKey() == null ? null : idByKey.get(entry.parentKey());
            if (entry.type() == MenuBootstrap.Type.DIRECTORY) {
                Optional<MenuRow> existing = rows.stream()
                        .filter(row -> DIRECTORY.equals(row.type()))
                        .filter(row -> java.util.Objects.equals(row.parentId(), parentId))
                        .filter(row -> row.name().equals(entry.name()))
                        .findFirst();
                if (existing.isPresent()) {
                    idByKey.put(entry.key(), existing.get().id());
                    continue;
                }
                mapper.insert(parentId, DIRECTORY, entry.name(), null, null, entry.sortOrder(),
                        ENABLED, entry.description());
                idByKey.put(entry.key(), mapper.lastInsertId());
                createdMenus++;
                continue;
            }

            MenuRow existing = PAGE.equals(entry.type().name())
                    ? mapper.findByRouteKey(entry.routeKey())
                    : mapper.findByPermissionCode(entry.permissionCode());
            if (existing != null) {
                idByKey.put(entry.key(), existing.id());
                continue;
            }

            PermissionChoice permission = ensurePermission(entry.permissionCode(), entry.name(),
                    entry.description());
            if (permission.created()) {
                createdPermissions++;
            }
            String type = entry.type().name();
            mapper.insert(parentId, type, entry.name(), entry.routeKey(), permission.id(),
                    entry.sortOrder(), ENABLED, entry.description());
            idByKey.put(entry.key(), mapper.lastInsertId());
            createdMenus++;
        }
        return new RestoreMenusResult(createdMenus, createdPermissions);
    }

    /** 找到权限码；不存在就建一个。created 标记这次是不是新建，恢复接口据此统计数量。 */
    private PermissionChoice ensurePermission(String code, String name, String description) {
        List<PermissionSummary> existing = permissions.findByCodes(List.of(code));
        if (!existing.isEmpty()) {
            return new PermissionChoice(existing.getFirst().id(), false);
        }
        long id = permissionCommands.create(code, name, description);
        return new PermissionChoice(id, true);
    }

    private record PermissionChoice(long id, boolean created) {
    }

    /**
     * 按深度从深到浅分批删除：同一深度的节点互不为父子，可以一次删掉；
     * 先把叶子删完再删上层，避免中途留下指向不存在父节点的子节点。
     */
    private void deleteDeepestFirst(List<MenuRow> rows, long rootId, List<Long> subtreeIds) {
        Map<Long, Long> parentById = new HashMap<>();
        for (MenuRow row : rows) {
            parentById.put(row.id(), row.parentId());
        }
        Map<Integer, List<Long>> byDepth = new java.util.TreeMap<>(java.util.Comparator.reverseOrder());
        for (Long id : subtreeIds) {
            byDepth.computeIfAbsent(depthFrom(id, rootId, parentById),
                            ignored -> new java.util.ArrayList<>())
                    .add(id);
        }
        byDepth.values().forEach(batch -> mapper.deleteByIds(batch));
    }

    private static int depthFrom(long id, long rootId, Map<Long, Long> parentById) {
        int depth = 0;
        Long current = id;
        while (current != null && current != rootId) {
            depth++;
            current = parentById.get(current);
        }
        return depth;
    }

    private List<Long> permissionIdsIn(List<MenuRow> rows, List<Long> subtreeIds) {
        Set<Long> subtree = Set.copyOf(subtreeIds);
        return rows.stream()
                .filter(row -> subtree.contains(row.id()))
                .map(MenuRow::permissionId)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private MenuNode requireNode(String rawId) {
        long id = parseId(rawId);
        return MenuTree.build(mapper.findAll()).stream()
                .flatMap(node -> flatten(node).stream())
                .filter(node -> node.id().equals(Long.toString(id)))
                .findFirst()
                .orElseThrow(MenuService::menuNotFound);
    }

    private static List<MenuNode> flatten(MenuNode node) {
        List<MenuNode> all = new java.util.ArrayList<>();
        all.add(node);
        node.children().forEach(child -> all.addAll(flatten(child)));
        return all;
    }

    private static MenuRow requireRow(List<MenuRow> rows, long id) {
        return rows.stream()
                .filter(row -> row.id() == id)
                .findFirst()
                .orElseThrow(MenuService::menuNotFound);
    }

    private static void requireHierarchy(MenuRow parent, String childType) {
        String parentType = parent == null ? null : parent.type();
        boolean allowed = switch (childType) {
            case DIRECTORY, PAGE -> parentType == null || DIRECTORY.equals(parentType);
            case TAB -> PAGE.equals(parentType);
            case BUTTON -> PAGE.equals(parentType) || TAB.equals(parentType);
            default -> false;
        };
        if (!allowed) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MENU_TYPE_NOT_ALLOWED",
                    "「%s」下不能新建%s".formatted(describe(parentType), describe(childType)));
        }
    }

    private static String describe(String type) {
        if (type == null) {
            return "顶层";
        }
        return switch (type) {
            case DIRECTORY -> "目录";
            case PAGE -> "页面";
            case TAB -> "页签";
            case BUTTON -> "按钮";
            default -> type;
        };
    }

    private static void requireRegisteredRoute(String routeKey) {
        if (routeKey == null || !RegisteredRoutes.contains(routeKey)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MENU_ROUTE_KEY_UNKNOWN",
                    "该路由未在前端注册，无法绑定：" + routeKey);
        }
    }

    /** 一个权限码只能挂一个节点：否则角色授权树里会出现两行同一个码，勾选语义也就含糊了。 */
    private void requirePermissionCodeFree(String permissionCode) {
        if (mapper.findByPermissionCode(permissionCode) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "MENU_PERMISSION_TAKEN",
                    "该权限码已挂在其它菜单节点上：" + permissionCode);
        }
    }

    private static void requireRouteKeyFree(List<MenuRow> rows, String routeKey, Long selfId) {
        boolean taken = rows.stream()
                .anyMatch(row -> routeKey.equals(row.routeKey())
                        && (selfId == null || row.id() != selfId));
        if (taken) {
            throw new ApiException(HttpStatus.CONFLICT, "MENU_ROUTE_KEY_TAKEN",
                    "该页面已挂在其它菜单节点上：" + routeKey);
        }
    }

    private static long parseId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MENU_ID", "缺少菜单 id");
        }
        try {
            return Long.parseLong(rawId.strip());
        } catch (NumberFormatException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_MENU_ID", "菜单 id 格式不正确");
        }
    }

    /** 上级节点可以为空（顶层节点），因此空值走 null 而不是报错。 */
    private static Long parseIdOrNull(String rawId) {
        return rawId == null || rawId.isBlank() ? null : parseId(rawId);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static ApiException menuNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "MENU_NOT_FOUND", "菜单节点不存在");
    }

    private static ApiException versionConflict() {
        return new ApiException(HttpStatus.CONFLICT, "MENU_VERSION_CONFLICT",
                "该菜单节点已被其他操作修改，请重新加载后核对并保存");
    }
}
