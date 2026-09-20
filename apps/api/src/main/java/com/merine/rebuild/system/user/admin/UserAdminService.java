package com.merine.rebuild.system.user.admin;

import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.common.PageResult;
import com.merine.rebuild.system.security.SystemAdminGuard;
import com.merine.rebuild.system.user.account.PasswordLimits;
import com.merine.rebuild.system.user.admin.dto.UserRequests;
import com.merine.rebuild.system.user.admin.dto.UserSummary;
import com.merine.rebuild.system.user.admin.persistence.UserAdminMapper;
import com.merine.rebuild.system.user.admin.persistence.UserQuery;
import com.merine.rebuild.system.user.admin.persistence.UserRow;
import com.merine.rebuild.system.unit.UnitLookup;
import com.merine.rebuild.system.unit.dto.UnitSummary;
import com.merine.rebuild.system.role.RoleLookup;
import com.merine.rebuild.system.role.RoleSummary;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户管理用例。
 *
 * 事务边界在这一层：一次编辑要同时改基本信息、角色关系和身份版本，
 * 任一步失败都不能留下“角色换了但版本没升”的中间状态。
 */
@Service
public class UserAdminService {
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private final UserAdminMapper mapper;
    private final UnitLookup units;
    private final RoleLookup roles;
    private final SystemAdminGuard guard;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(UserAdminMapper mapper, UnitLookup units, RoleLookup roles,
                            SystemAdminGuard guard, PasswordEncoder passwordEncoder) {
        this.mapper = mapper;
        this.units = units;
        this.roles = roles;
        this.guard = guard;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public PageResult<UserSummary> list(String keyword, String unitCode, String roleCode,
                                        String status, int page, int pageSize) {
        if (page < 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "页码从 1 开始");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE_SIZE",
                    "每页条数需在 1–%d 之间".formatted(MAX_PAGE_SIZE));
        }
        UserQuery query = new UserQuery(likePattern(keyword), blankToNull(unitCode),
                blankToNull(roleCode), normalizeStatus(status), page, pageSize);

        long total = mapper.count(query);
        if (total > 0 && query.offset() >= total) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PAGE_OUT_OF_RANGE", "请求的页码超出结果范围");
        }
        List<UserSummary> items = mapper.findPage(query, query.offset(), query.pageSize())
                .stream()
                .map(UserSummary::from)
                .toList();
        return new PageResult<>(items, total, page, pageSize);
    }

    @Transactional(readOnly = true)
    public UserSummary get(long id) {
        return UserSummary.from(requireUser(id));
    }

    @Transactional
    public UserSummary create(UserRequests.CreateUser request) {
        PasswordLimits.requireSupportedLength(request.password(), "password");
        String loginName = request.loginName().strip();
        // 不做「先查再插」：那既多一次查询，也留下查与插之间的竞态窗口。
        // 唯一约束是唯一判据，冲突统一翻译成 409。
        requireEnabledUnit(request.unitCode());
        List<String> roleCodes = requireEnabledRoles(request.roleCodes());

        try {
            mapper.insert(loginName, request.displayName().strip(),
                    passwordEncoder.encode(request.password()), request.unitCode());
        } catch (DuplicateKeyException error) {
            throw duplicateLoginName();
        }
        long createdId = mapper.findByLoginName(loginName).id();
        replaceRoles(createdId, roleCodes);
        return UserSummary.from(requireUser(createdId));
    }

    @Transactional
    public UserSummary update(long id, UserRequests.UpdateUser request) {
        UserRow current = requireUser(id);
        if (current.version() != request.version()) {
            throw editConflict();
        }
        boolean passwordChanged = request.newPassword() != null && !request.newPassword().isBlank();
        if (passwordChanged) {
            PasswordLimits.requireSupportedLength(request.newPassword(), "newPassword");
        }
        requireEnabledUnit(request.unitCode());
        List<String> roleCodes = requireEnabledRoles(request.roleCodes());

        // WHERE version 也覆盖读取之后的竞态；冲突时整个用例回滚，不替换角色或密码。
        if (mapper.updateBasic(id, request.displayName().strip(), request.unitCode(), request.version()) == 0) {
            throw editConflict();
        }

        boolean rolesChanged = !asSet(current.roleCodes()).equals(asSet(roleCodes));
        boolean unitChanged = !current.unitCode().equals(request.unitCode());

        // 把最后一个管理员的角色摘掉，后果与停用账号一样是全员锁死
        boolean wasAdmin = current.roleCodes().stream().anyMatch(guard.adminRoleCodes()::contains);
        boolean staysAdmin = roleCodes.stream().anyMatch(guard.adminRoleCodes()::contains);
        if (wasAdmin && !staysAdmin) {
            requireUsableAdminRemains(List.of(id), "不能移除最后一个可用管理员的角色");
        }

        if (rolesChanged) {
            replaceRoles(id, roleCodes);
        }
        if (passwordChanged) {
            mapper.updatePassword(id, passwordEncoder.encode(request.newPassword()));
        }
        // 角色、所属单位与密码的变化都必须让旧会话失效：统一靠身份与授权版本判定
        if (rolesChanged || unitChanged || passwordChanged) {
            mapper.bumpAuthorizationVersion(id);
        }
        return UserSummary.from(requireUser(id));
    }

    /**
     * 启用或停用一批账号。
     *
     * 停用前先确认不会把最后一个可管理用户的账号停掉——界面上有这个批量按钮，
     * 误点一次就会把所有人挡在用户管理之外。
     */
    @Transactional
    public List<UserSummary> changeStatus(List<String> rawIds, boolean enable) {
        List<Long> ids = parseIds(rawIds);
        if (!enable) {
            requireUsableAdminRemains(ids, "不能停用最后一个可用管理员的账号");
        }
        mapper.updateStatus(ids, enable ? "ENABLED" : "DISABLED");
        // 已经处于目标状态是幂等成功，不重复递增版本；仍区分全部对象不存在的情形。
        List<UserSummary> updated = mapper.findByIds(ids).stream().map(UserSummary::from).toList();
        if (updated.isEmpty()) {
            throw userNotFound();
        }
        return updated;
    }

    /**
     * 守住「系统里始终至少有一个能登录、且能管理用户的管理员」。
     *
     * 停用账号与移除管理角色是两条不同的写入路径，但会通向同一个后果：
     * 没有任何账号能再调用用户管理接口，只能靠 seed 或直接改库恢复。
     * 因此两条路径都走这里判定，且都按与登录一致的可用性口径统计。
     */
    private void requireUsableAdminRemains(List<Long> excludedIds, String message) {
        if (mapper.countRemainingEnabledAdmins(guard.adminRoleCodes(), excludedIds) == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_USER_ADMIN", message);
        }
    }

    private void replaceRoles(long userId, List<String> roleCodes) {
        mapper.deleteRoles(userId);
        for (String roleCode : roleCodes) {
            if (mapper.insertRoleByCode(userId, roleCode) == 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_ROLE", "角色不存在：" + roleCode);
            }
        }
    }

    private UserRow requireUser(long id) {
        UserRow row = mapper.findById(id);
        if (row == null) {
            throw userNotFound();
        }
        return row;
    }

    private void requireEnabledUnit(String unitCode) {
        UnitSummary unit = units.findByCode(unitCode);
        if (unit == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_UNIT", "所属单位不存在");
        }
        if (!"ENABLED".equals(unit.status())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNIT_DISABLED", "所属单位已停用");
        }
    }

    private List<String> requireEnabledRoles(List<String> requested) {
        Map<String, RoleSummary> known = roles.listAll().stream()
                .collect(Collectors.toMap(RoleSummary::code, Function.identity()));
        Set<String> result = new LinkedHashSet<>();
        for (String code : requested) {
            RoleSummary role = known.get(code);
            if (role == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_ROLE", "角色不存在：" + code);
            }
            if (!"ENABLED".equals(role.status())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_DISABLED", "角色已停用：" + role.name());
            }
            result.add(code);
        }
        return List.copyOf(result);
    }

    private List<Long> parseIds(List<String> rawIds) {
        Set<Long> ids = new LinkedHashSet<>();
        for (String raw : rawIds) {
            try {
                ids.add(Long.parseLong(raw.strip()));
            } catch (NumberFormatException error) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USER_ID", "用户 id 格式不正确");
            }
        }
        if (ids.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USER_ID", "请至少选择一个用户");
        }
        return new ArrayList<>(ids);
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
        if (!upper.equals("ENABLED") && !upper.equals("DISABLED")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "账号状态只能是 ENABLED 或 DISABLED");
        }
        return upper;
    }

    private static Set<String> asSet(List<String> values) {
        return new HashSet<>(values);
    }

    private static ApiException duplicateLoginName() {
        return new ApiException(HttpStatus.CONFLICT, "LOGIN_NAME_TAKEN", "该账号已存在，请更换");
    }

    private static ApiException editConflict() {
        return new ApiException(HttpStatus.CONFLICT, "USER_VERSION_CONFLICT",
                "该用户已被其他操作修改，请重新加载后核对并保存");
    }

    private static ApiException userNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
    }
}
