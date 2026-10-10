package com.merine.rebuild.system.role;

import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.common.PageResult;
import com.merine.rebuild.system.audit.AuditEvent;
import com.merine.rebuild.system.audit.AuditTrail;
import com.merine.rebuild.system.permission.PermissionLookup;
import com.merine.rebuild.system.role.dto.RoleDetail;
import com.merine.rebuild.system.role.dto.RoleListItem;
import com.merine.rebuild.system.role.dto.RoleMember;
import com.merine.rebuild.system.role.dto.RoleRequests;
import com.merine.rebuild.system.role.persistence.RoleMapper;
import com.merine.rebuild.system.role.persistence.RoleQuery;
import com.merine.rebuild.system.menu.MenuLookup;
import com.merine.rebuild.system.menu.dto.MenuNode;
import com.merine.rebuild.system.permission.PermissionModules;
import com.merine.rebuild.system.permission.PermissionSummary;
import com.merine.rebuild.system.role.persistence.RoleRow;
import com.merine.rebuild.system.security.BuiltinAdminRoles;
import com.merine.rebuild.system.user.authorization.AdminCoverageGuard;
import com.merine.rebuild.system.user.authorization.UserAuthorizationCommands;
import com.merine.rebuild.system.user.usage.RoleUsageLookup;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色管理用例。
 *
 * 事务边界在这一层：一次编辑要同时改基本信息、权限授予关系与持有者授权版本，
 * 任一步失败都不能留下「权限换了但旧会话仍然有效」的中间状态。
 *
 * 写入前先取管理底线的串行锚点（{@link AdminCoverageGuard#lock()}），
 * 变更后再核对覆盖，见 {@link AdminCoverageGuard} 的说明。
 */
@Service
public class RoleService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private static final String ENABLED = "ENABLED";
    private static final String DISABLED = "DISABLED";

    /** 模块分组的节点 id 前缀：`module:<模块>`，界面只做勾选、不编辑这些节点。 */
    static final String MODULE_GROUP_PREFIX = "module:";

    private final RoleMapper mapper;
    private final PermissionLookup permissions;
    private final MenuLookup menus;
    private final BuiltinAdminRoles builtinRoles;
    private final RoleUsageLookup roleUsage;
    private final UserAuthorizationCommands authorization;
    private final AdminCoverageGuard coverage;
    private final AuditTrail audit;

    public RoleService(RoleMapper mapper, PermissionLookup permissions, MenuLookup menus,
                       BuiltinAdminRoles builtinRoles, RoleUsageLookup roleUsage,
                       UserAuthorizationCommands authorization, AdminCoverageGuard coverage,
                       AuditTrail audit) {
        this.mapper = mapper;
        this.permissions = permissions;
        this.menus = menus;
        this.builtinRoles = builtinRoles;
        this.roleUsage = roleUsage;
        this.authorization = authorization;
        this.coverage = coverage;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResult<RoleListItem> list(String keyword, String status, int page, int pageSize) {
        requirePage(page, pageSize);
        RoleQuery query = new RoleQuery(likePattern(keyword), normalizeStatus(status), page, pageSize);

        long total = mapper.count(query);
        if (total > 0 && query.offset() >= total) {
            throw pageOutOfRange();
        }
        List<RoleRow> rows = mapper.findPage(query, query.offset(), query.pageSize());
        return new PageResult<>(toListItems(rows), total, page, pageSize);
    }

    @Transactional(readOnly = true)
    public RoleDetail get(String rawCode) {
        return toDetail(requireRole(rawCode));
    }

    @Transactional(readOnly = true)
    public PageResult<RoleMember> members(String rawCode, int page, int pageSize) {
        requirePage(page, pageSize);
        RoleRow role = requireRole(rawCode);

        long total = roleUsage.countHolders(role.code());
        long offset = (long) (page - 1) * pageSize;
        if (total > 0 && offset >= total) {
            throw pageOutOfRange();
        }
        List<RoleMember> items = roleUsage.findHolders(role.code(), offset, pageSize).stream()
                .map(RoleMember::from)
                .toList();
        return new PageResult<>(items, total, page, pageSize);
    }

    @Transactional
    public RoleDetail create(RoleRequests.CreateRole request) {
        String code = request.code().strip();
        List<String> permissionCodes = requireKnownPermissions(request.permissionCodes());

        try {
            mapper.insert(code, request.name().strip(), blankToNull(request.description()));
        } catch (DuplicateKeyException error) {
            throw roleCodeTaken();
        }
        RoleRow created = mapper.findByCode(code);
        if (created == null) {
            throw new IllegalStateException("新建角色后未读到插入结果");
        }
        replacePermissions(created.id(), permissionCodes);
        RoleDetail createdDetail = toDetail(requireRole(code));
        audit.recordCurrent(AuditEvent.succeeded("system", "role:create", "ROLE", code,
                createdDetail.name(),
                "新建角色「%s」（%s），权限 %d 项".formatted(createdDetail.name(), code,
                        permissionCodes.size())));
        return createdDetail;
    }

    @Transactional
    public RoleDetail update(String rawCode, RoleRequests.UpdateRole request) {
        coverage.lock();

        String code = rawCode.strip();
        RoleRow current = requireRole(code);
        if (current.version() != request.version()) {
            throw versionConflict();
        }
        List<String> requestedPermissions = requireKnownPermissions(request.permissionCodes());
        if (isBuiltin(current) && !asSet(requestedPermissions).equals(asSet(permissions.listAllCodes()))) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLE_BUILTIN_PROTECTED",
                    "内置管理员角色恒拥有全部权限，权限不可修改");
        }

        if (mapper.updateBasic(current.id(), request.name().strip(),
                blankToNull(request.description()), request.version()) == 0) {
            throw versionConflict();
        }

        boolean permissionsChanged = !asSet(currentPermissions(current))
                .equals(asSet(requestedPermissions));
        if (permissionsChanged) {
            replacePermissions(current.id(), requestedPermissions);
            // 权限集合变了，持有者的旧 authority 必须失效；改名与改说明只是展示，不递增
            authorization.bumpVersionForRoleHolders(List.of(code));
            coverage.requireUsableAdminRemains("不能移除最后一个可用管理员的角色权限");
        }
        RoleDetail updated = toDetail(requireRole(code));
        audit.recordCurrent(AuditEvent.succeeded("system", "role:update", "ROLE", code,
                updated.name(), "修改角色「%s」的名称或说明".formatted(updated.name())));
        if (permissionsChanged) {
            audit.recordCurrent(AuditEvent.succeeded("system", "role:grant-permissions", "ROLE", code,
                    updated.name(),
                    "角色「%s」权限由 %d 项改为 %d 项".formatted(updated.name(),
                            currentPermissions(current).size(), requestedPermissions.size())));
        }
        return updated;
    }

    /**
     * 启用或停用一批角色。
     *
     * 只有状态确实变化的角色才递增版本并让持有者会话失效——重复点同一个动作是幂等成功，
     * 不该把在线用户踢下线。停用可能减少管理覆盖，启用只会增加，因此只在停用时核对覆盖。
     */
    @Transactional
    public List<RoleListItem> changeStatus(List<String> rawCodes, boolean enable) {
        coverage.lock();

        List<String> codes = parseCodes(rawCodes);
        List<RoleRow> roles = mapper.findByCodes(codes);
        if (roles.size() != codes.size()) {
            throw roleNotFound();
        }

        String target = enable ? ENABLED : DISABLED;
        List<String> changed = roles.stream()
                .filter(role -> !target.equals(role.status()))
                .map(RoleRow::code)
                .toList();
        if (!changed.isEmpty()) {
            mapper.updateStatus(changed, target);
            authorization.bumpVersionForRoleHolders(changed);
            if (!enable) {
                coverage.requireUsableAdminRemains("不能停用最后一个可用管理员的角色");
            }
        }
        List<RoleListItem> items = toListItems(mapper.findByCodes(codes));
        // 只给状态确实变化的角色留痕：重复点同一个动作是幂等成功，不该产生审计噪音
        items.stream()
                .filter(role -> changed.contains(role.code()))
                .forEach(role -> audit.recordCurrent(AuditEvent.succeeded("system",
                        "role:toggle-status", "ROLE", role.code(), role.name(),
                        "%s角色「%s」".formatted(enable ? "启用" : "停用", role.name()))));
        return items;
    }

    /**
     * 删除角色：只允许非内置、且已经没有任何账号持有。
     *
     * 删除前已确认无持有者，因此不可能减少管理覆盖，不再重复核对；
     * 版本冲突保护的是「基于过期信息误删刚被改过的角色」。
     */
    @Transactional
    public void delete(String rawCode, int version) {
        coverage.lock();

        if (version < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VERSION",
                    "缺少角色版本号，请刷新页面后重试");
        }
        RoleRow role = requireRole(rawCode);
        if (isBuiltin(role)) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLE_BUILTIN_PROTECTED",
                    "内置管理员角色不能删除");
        }
        if (role.version() != version) {
            throw versionConflict();
        }

        long holders = roleUsage.countHolders(role.code());
        if (holders > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLE_IN_USE",
                    "该角色还有 %d 个账号在用，不能删除".formatted(holders));
        }

        mapper.deletePermissions(role.id());
        mapper.deleteById(role.id());
        audit.recordCurrent(AuditEvent.succeeded("system", "role:delete", "ROLE", role.code(),
                role.name(), "删除角色「%s」（%s）".formatted(role.name(), role.code())));
    }

    private void replacePermissions(long roleId, List<String> permissionCodes) {
        mapper.deletePermissions(roleId);
        for (String permissionCode : permissionCodes) {
            if (mapper.insertPermissionByCode(roleId, permissionCode) == 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_PERMISSION",
                        "权限不存在：" + permissionCode);
            }
        }
    }

    private List<RoleListItem> toListItems(List<RoleRow> rows) {
        Map<String, Long> holderCounts =
                roleUsage.countHoldersByRoleCodes(rows.stream().map(RoleRow::code).toList());
        int allPermissionCount = permissions.listAllCodes().size();
        return rows.stream()
                .map(row -> new RoleListItem(
                        row.code(),
                        row.name(),
                        row.description(),
                        row.status(),
                        isBuiltin(row),
                        isBuiltin(row) ? allPermissionCount : row.permissionCount(),
                        holderCounts.getOrDefault(row.code(), 0L),
                        row.version(),
                        row.updatedAt()))
                .toList();
    }

    private RoleDetail toDetail(RoleRow row) {
        boolean builtin = isBuiltin(row);
        List<String> permissionCodes = builtin
                ? permissions.listAllCodes()
                : mapper.findPermissionCodesByRoleId(row.id());
        return new RoleDetail(row.code(), row.name(), row.description(), row.status(), builtin,
                permissionCodes, roleUsage.countHolders(row.code()), row.version(), row.updatedAt());
    }

    private List<String> currentPermissions(RoleRow row) {
        return isBuiltin(row) ? permissions.listAllCodes() : mapper.findPermissionCodesByRoleId(row.id());
    }

    private boolean isBuiltin(RoleRow row) {
        return builtinRoles.isBuiltin(row.code());
    }

    /** 勾选的权限码必须存在于权限清单里；重复项去掉，顺序按登记顺序。 */
    /**
     * 权限勾选树：菜单树 + 每个「有非菜单权限码」的模块一个分组。
     *
     * <p>有些权限码故意不挂菜单节点（例如智能体：它有自己的外壳，不占后台菜单），只查菜单树的话
     * 管理员在角色管理里看不到它们，只能改库授权。这些码按**模块**归组（组名取
     * {@link PermissionModules}），一个模块一组、顺序由注册表决定；不合并成跨模块的
     * 「其他权限」，否则归属不清、死码也藏得住。分组本身没有权限码，勾选子项与菜单权限一样生效。
     */
    @Transactional(readOnly = true)
    public List<MenuNode> permissionTree() {
        List<MenuNode> nodes = new ArrayList<>(menus.tree());
        Set<String> bound = new HashSet<>(menus.listBoundPermissionCodes());
        Map<String, List<PermissionSummary>> unboundByModule = new TreeMap<>(
                Comparator.comparingInt(PermissionModules::sortOrderOf).thenComparing(module -> module));
        permissions.listAll().stream()
                .filter(permission -> !bound.contains(permission.code()))
                .forEach(permission -> unboundByModule
                        .computeIfAbsent(PermissionModules.codeOf(permission.code()), module -> new ArrayList<>())
                        .add(permission));
        unboundByModule.forEach((module, permissions) -> nodes.add(moduleGroup(module, permissions)));
        return List.copyOf(nodes);
    }

    /** 模块分组：父节点无权限码，子节点逐个对应权限码，顺序按权限码排序稳定。 */
    private static MenuNode moduleGroup(String module, List<PermissionSummary> permissions) {
        List<MenuNode> children = new ArrayList<>();
        int order = 0;
        for (PermissionSummary permission : permissions.stream()
                .sorted(Comparator.comparing(PermissionSummary::code))
                .toList()) {
            children.add(new MenuNode(MODULE_GROUP_PREFIX + module + "." + permission.code(),
                    MODULE_GROUP_PREFIX + module, "BUTTON", permission.name(), null, null,
                    permission.code(), permission.description(), order, "ENABLED", 0, Instant.now(),
                    List.of()));
            order += 10;
        }
        return new MenuNode(MODULE_GROUP_PREFIX + module, null, "DIRECTORY",
                PermissionModules.nameOf(module), null, null, null,
                "这个模块的权限不出现在后台菜单里（例如智能体有自己的外壳）；勾选后与菜单权限一样生效",
                PermissionModules.sortOrderOf(module), "ENABLED", 0, Instant.now(),
                List.copyOf(children));
    }

    private List<String> requireKnownPermissions(List<String> requested) {
        Set<String> known = new HashSet<>(permissions.listAllCodes());
        Set<String> result = new LinkedHashSet<>();
        for (String raw : requested) {
            String code = raw.strip();
            if (!known.contains(code)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_PERMISSION",
                        "权限不存在：" + code);
            }
            result.add(code);
        }
        return List.copyOf(result);
    }

    private RoleRow requireRole(String rawCode) {
        RoleRow row = mapper.findByCode(rawCode.strip());
        if (row == null) {
            throw roleNotFound();
        }
        return row;
    }

    private static List<String> parseCodes(List<String> rawCodes) {
        Set<String> codes = new LinkedHashSet<>();
        for (String raw : rawCodes) {
            String code = raw.strip();
            if (code.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE_CODE", "角色编码不能为空");
            }
            codes.add(code);
        }
        if (codes.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ROLE_CODE", "请至少选择一个角色");
        }
        return List.copyOf(codes);
    }

    private static void requirePage(int page, int pageSize) {
        if (page < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "页码从 1 开始");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_SIZE",
                    "每页条数需在 1–%d 之间".formatted(MAX_PAGE_SIZE));
        }
    }

    /**
     * 关键字按字面匹配：转义 LIKE 的通配符，避免用户输入的 % 或 _ 变成模糊匹配。
     * MySQL 的 LIKE 默认以反斜杠为转义符，因此这里只需转义反斜杠与两个通配符。
     */
    private static String likePattern(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escaped = keyword.strip()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String upper = status.strip().toUpperCase();
        if (!upper.equals(ENABLED) && !upper.equals(DISABLED)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS",
                    "角色状态只能是 ENABLED 或 DISABLED");
        }
        return upper;
    }

    private static Set<String> asSet(List<String> values) {
        return new HashSet<>(values);
    }

    private static ApiException roleNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "角色不存在");
    }

    private static ApiException roleCodeTaken() {
        return new ApiException(HttpStatus.CONFLICT, "ROLE_CODE_TAKEN", "该角色编码已存在，请更换");
    }

    private static ApiException versionConflict() {
        return new ApiException(HttpStatus.CONFLICT, "ROLE_VERSION_CONFLICT",
                "该角色已被其他操作修改，请重新加载后核对并保存");
    }

    private static ApiException pageOutOfRange() {
        return new ApiException(HttpStatus.BAD_REQUEST, "PAGE_OUT_OF_RANGE", "请求的页码超出结果范围");
    }
}
