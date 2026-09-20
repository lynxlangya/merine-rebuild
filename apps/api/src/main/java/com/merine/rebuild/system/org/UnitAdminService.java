package com.merine.rebuild.system.org;

import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.system.user.UserUnitUsageLookup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 单位管理用例。
 *
 * 写路径先锁定全部单位行，再校验单根、最多三级、父级存在、无循环和删除引用；
 * 当前组织规模约 50 行，串行化维护换来清楚的层级不变量。
 */
@Service
public class UnitAdminService {
    private static final int MAX_LEVEL = 3;

    private final UnitAdminMapper mapper;
    private final UserUnitUsageLookup userUsage;

    public UnitAdminService(UnitAdminMapper mapper, UserUnitUsageLookup userUsage) {
        this.mapper = mapper;
        this.userUsage = userUsage;
    }

    @Transactional(readOnly = true)
    public List<UnitTreeNode> tree() {
        List<UnitAdminRow> rows = mapper.findAll();
        return buildTree(rows, userCounts(rows));
    }

    @Transactional
    public UnitView create(UnitRequests.CreateUnit request) {
        mapper.lockAllIds();
        List<UnitAdminRow> rows = mapper.findAll();

        String code = request.code().strip();
        String name = request.name().strip();
        String parentCode = blankToNull(request.parentCode());
        String areaCode = request.areaCode().strip();

        requireCodeAvailable(rows, code);
        ParentChoice parent = resolveParent(rows, parentCode, null);

        mapper.insert(code, name, parent.id(), parent.level(), areaCode);
        UnitAdminRow created = mapper.findByCode(code);
        if (created == null) {
            throw new IllegalStateException("新建单位后未读到插入结果");
        }
        return toView(created, 0, 0);
    }

    @Transactional
    public UnitView update(String rawCode, UnitRequests.UpdateUnit request) {
        mapper.lockAllIds();
        List<UnitAdminRow> rows = mapper.findAll();

        String code = rawCode.strip();
        UnitAdminRow current = requireUnit(rows, code);
        if (current.version() != request.version()) {
            throw versionConflict();
        }

        String name = request.name().strip();
        String parentCode = blankToNull(request.parentCode());
        String areaCode = request.areaCode().strip();

        ParentChoice parent = resolveParent(rows, parentCode, current.id());
        if (parent.level() + maxRelativeDepth(rows, current.id()) > MAX_LEVEL) {
            throw levelLimit("调整上级后会产生第四级单位");
        }

        int updated = mapper.update(current.id(), name, parent.id(), parent.level(), areaCode,
                request.version());
        if (updated == 0) {
            throw versionConflict();
        }

        UnitAdminRow changed = mapper.findByCode(code);
        if (changed == null) {
            throw unitNotFound();
        }
        long childCount = directChildCount(rows, current.id());
        long userCount = userCounts(rows).getOrDefault(current.id(), 0L);
        return toView(changed, childCount, userCount);
    }

    @Transactional
    public void delete(String rawCode) {
        mapper.lockAllIds();
        List<UnitAdminRow> rows = mapper.findAll();
        UnitAdminRow target = requireUnit(rows, rawCode.strip());

        if (directChildCount(rows, target.id()) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "UNIT_HAS_CHILDREN",
                    "该单位还有下级单位，不能删除");
        }
        long userCount = userCounts(rows).getOrDefault(target.id(), 0L);
        if (userCount > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "UNIT_HAS_USERS",
                    "该单位还有用户，不能删除");
        }

        try {
            mapper.deleteById(target.id());
        } catch (DataIntegrityViolationException error) {
            // 行锁已经把普通并发串行化；这里兜底处理越过接口约束写入的引用。
            throw new ApiException(HttpStatus.CONFLICT, "UNIT_IN_USE",
                    "该单位仍被下级或用户引用，不能删除");
        }
    }

    private ParentChoice resolveParent(List<UnitAdminRow> rows, String parentCode, Long movingId) {
        if (parentCode == null) {
            boolean anotherRootExists = rows.stream()
                    .anyMatch(row -> row.level() == 1
                            && (movingId == null || row.id() != movingId));
            if (anotherRootExists) {
                throw new ApiException(HttpStatus.CONFLICT, "ROOT_ALREADY_EXISTS",
                        "系统只允许一个一级单位");
            }
            return new ParentChoice(null, 1);
        }

        UnitAdminRow parent = rows.stream()
                .filter(row -> row.code().equals(parentCode))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_UNIT_PARENT",
                        "所选上级单位不存在"));

        if (movingId != null) {
            if (parent.id() == movingId) {
                throw new ApiException(HttpStatus.CONFLICT, "UNIT_PARENT_CYCLE",
                        "上级单位不能选择自身");
            }
            if (isDescendant(rows, movingId, parent.id())) {
                throw new ApiException(HttpStatus.CONFLICT, "UNIT_PARENT_CYCLE",
                        "上级单位不能选择当前单位的后代");
            }
        }

        int level = parent.level() + 1;
        if (level > MAX_LEVEL) {
            throw levelLimit("单位最多支持三级");
        }
        return new ParentChoice(parent.id(), level);
    }

    private static void requireCodeAvailable(List<UnitAdminRow> rows, String code) {
        if (rows.stream().anyMatch(row -> row.code().equals(code))) {
            throw new ApiException(HttpStatus.CONFLICT, "UNIT_CODE_TAKEN",
                    "该单位编码已存在，请更换");
        }
    }

    private static UnitAdminRow requireUnit(List<UnitAdminRow> rows, String code) {
        return rows.stream()
                .filter(row -> row.code().equals(code))
                .findFirst()
                .orElseThrow(UnitAdminService::unitNotFound);
    }

    private static boolean isDescendant(List<UnitAdminRow> rows, long ancestorId, long candidateId) {
        Map<Long, UnitAdminRow> byId = new HashMap<>();
        for (UnitAdminRow row : rows) {
            byId.put(row.id(), row);
        }

        Set<Long> visited = new HashSet<>();
        Long currentId = candidateId;
        while (currentId != null && visited.add(currentId)) {
            if (currentId == ancestorId) {
                return true;
            }
            UnitAdminRow current = byId.get(currentId);
            currentId = current == null ? null : current.parentId();
        }
        return false;
    }

    private static int maxRelativeDepth(List<UnitAdminRow> rows, long rootId) {
        Map<Long, List<Long>> children = new HashMap<>();
        for (UnitAdminRow row : rows) {
            if (row.parentId() != null) {
                children.computeIfAbsent(row.parentId(), ignored -> new ArrayList<>()).add(row.id());
            }
        }
        return depth(rootId, children, new HashSet<>());
    }

    private static int depth(long id, Map<Long, List<Long>> children, Set<Long> path) {
        if (!path.add(id)) {
            throw hierarchyCorrupt();
        }
        int max = 0;
        for (long childId : children.getOrDefault(id, List.of())) {
            max = Math.max(max, 1 + depth(childId, children, path));
        }
        path.remove(id);
        return max;
    }

    private static long directChildCount(List<UnitAdminRow> rows, long id) {
        return rows.stream().filter(row -> Objects.equals(row.parentId(), id)).count();
    }

    private Map<Long, Long> userCounts(List<UnitAdminRow> rows) {
        return userUsage.countByUnitIds(rows.stream().map(UnitAdminRow::id).toList());
    }

    private static List<UnitTreeNode> buildTree(List<UnitAdminRow> rows, Map<Long, Long> userCounts) {
        Map<Long, List<UnitAdminRow>> childrenByParent = new HashMap<>();
        List<UnitAdminRow> roots = new ArrayList<>();
        for (UnitAdminRow row : rows) {
            if (row.parentId() == null) {
                roots.add(row);
            } else {
                childrenByParent.computeIfAbsent(row.parentId(), ignored -> new ArrayList<>()).add(row);
            }
        }

        roots.sort((left, right) -> left.code().compareTo(right.code()));
        childrenByParent.values().forEach(list ->
                list.sort((left, right) -> left.code().compareTo(right.code())));
        return roots.stream()
                .map(row -> buildNode(row, childrenByParent, userCounts, new HashSet<>()))
                .toList();
    }

    private static UnitTreeNode buildNode(UnitAdminRow row,
                                          Map<Long, List<UnitAdminRow>> childrenByParent,
                                          Map<Long, Long> userCounts,
                                          Set<Long> path) {
        if (!path.add(row.id())) {
            throw hierarchyCorrupt();
        }
        List<UnitAdminRow> childRows = childrenByParent.getOrDefault(row.id(), List.of());
        List<UnitTreeNode> children = childRows.stream()
                .map(child -> buildNode(child, childrenByParent, userCounts, path))
                .toList();
        path.remove(row.id());
        return new UnitTreeNode(
                row.code(),
                row.name(),
                row.status(),
                row.parentCode(),
                row.level(),
                row.areaCode(),
                children.size(),
                userCounts.getOrDefault(row.id(), 0L),
                row.version(),
                row.updatedAt(),
                children);
    }

    private static UnitView toView(UnitAdminRow row, long childCount, long userCount) {
        return new UnitView(
                row.code(),
                row.name(),
                row.status(),
                row.parentCode(),
                row.level(),
                row.areaCode(),
                childCount,
                userCount,
                row.version(),
                row.updatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static ApiException unitNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "UNIT_NOT_FOUND", "单位不存在");
    }

    private static ApiException versionConflict() {
        return new ApiException(HttpStatus.CONFLICT, "UNIT_VERSION_CONFLICT",
                "该单位已被其他操作修改，请重新加载后核对并保存");
    }

    private static ApiException levelLimit(String message) {
        return new ApiException(HttpStatus.CONFLICT, "UNIT_LEVEL_LIMIT", message);
    }

    private static ApiException hierarchyCorrupt() {
        return new ApiException(HttpStatus.CONFLICT, "UNIT_HIERARCHY_CORRUPT",
                "单位层级数据存在循环，请先修复后重试");
    }

    private record ParentChoice(Long id, int level) {
    }
}
