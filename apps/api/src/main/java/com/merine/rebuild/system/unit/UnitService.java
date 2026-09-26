package com.merine.rebuild.system.unit;

import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.system.user.usage.UserUnitUsageLookup;
import com.merine.rebuild.task.TaskUnitUsageLookup;
import com.merine.rebuild.system.unit.dto.UnitRequests;
import com.merine.rebuild.system.unit.dto.UnitTreeNode;
import com.merine.rebuild.system.unit.dto.UnitView;
import com.merine.rebuild.system.unit.persistence.UnitMapper;
import com.merine.rebuild.system.unit.persistence.UnitRow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 单位管理用例。
 *
 * 写路径先锁定全部单位行，再校验单根、最多三级、父级存在、无循环和删除引用；
 * 当前组织规模约 50 行，串行化维护换来清楚的层级不变量。
 * 引用完整性不靠外键：这里的全表排他锁与写用户时取的单位引用锁互斥，
 * 删除前再校验下级与用户，越过用例的直接写入由巡检回归发现（见 docs/rules/database.md）。
 */
@Service
public class UnitService {
    private static final int MAX_LEVEL = 3;

    private final UnitMapper mapper;
    private final UserUnitUsageLookup userUsage;
    private final TaskUnitUsageLookup taskUsage;

    public UnitService(UnitMapper mapper, UserUnitUsageLookup userUsage, TaskUnitUsageLookup taskUsage) {
        this.mapper = mapper;
        this.userUsage = userUsage;
        this.taskUsage = taskUsage;
    }

    @Transactional(readOnly = true)
    public List<UnitTreeNode> tree() {
        List<UnitRow> rows = mapper.findAll();
        return buildTree(rows, userCounts(rows));
    }

    @Transactional
    public UnitView create(UnitRequests.CreateUnit request) {
        mapper.lockAllIds();
        // 锁后用锁定读：一致性读可能停在取锁之前的快照，会把刚提交的删除/新增看漏
        List<UnitRow> rows = mapper.findAllForShare();

        String code = request.code().strip();
        String name = request.name().strip();
        String parentCode = blankToNull(request.parentCode());
        String areaCode = request.areaCode().strip();

        requireCodeAvailable(rows, code);
        ParentChoice parent = resolveParent(rows, parentCode, null);

        mapper.insert(code, name, parent.id(), parent.level(), areaCode);
        UnitRow created = mapper.findByCode(code);
        if (created == null) {
            throw new IllegalStateException("新建单位后未读到插入结果");
        }
        return toView(created, 0, 0);
    }

    @Transactional
    public UnitView update(String rawCode, UnitRequests.UpdateUnit request) {
        mapper.lockAllIds();
        List<UnitRow> rows = mapper.findAllForShare();

        String code = rawCode.strip();
        UnitRow current = requireUnit(rows, code);
        if (current.version() != request.version()) {
            throw versionConflict();
        }

        String name = request.name().strip();
        String parentCode = blankToNull(request.parentCode());
        String areaCode = request.areaCode().strip();

        ParentChoice parent = resolveParent(rows, parentCode, current.id());
        if (!Objects.equals(parent.id(), current.parentId()) && taskUsage.hasReferences(current.id())) {
            throw new ApiException(HttpStatus.CONFLICT, "UNIT_HAS_TASKS", "该单位已有任务记录，不能调整上级");
        }
        if (parent.level() + maxRelativeDepth(rows, current.id()) > MAX_LEVEL) {
            throw levelLimit("调整上级后会产生第四级单位");
        }

        int updated = mapper.update(current.id(), name, parent.id(), parent.level(), areaCode,
                request.version());
        if (updated == 0) {
            throw versionConflict();
        }

        UnitRow changed = mapper.findByCode(code);
        if (changed == null) {
            throw unitNotFound();
        }
        long childCount = directChildCount(rows, current.id());
        long userCount = userCountsForWrite(rows).getOrDefault(current.id(), 0L);
        return toView(changed, childCount, userCount);
    }

    @Transactional
    public void delete(String rawCode) {
        mapper.lockAllIds();
        List<UnitRow> rows = mapper.findAllForShare();
        UnitRow target = requireUnit(rows, rawCode.strip());

        if (directChildCount(rows, target.id()) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "UNIT_HAS_CHILDREN",
                    "该单位还有下级单位，不能删除");
        }
        long userCount = userCountsForWrite(rows).getOrDefault(target.id(), 0L);
        if (userCount > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "UNIT_HAS_USERS",
                    "该单位还有用户，不能删除");
        }
        if (taskUsage.hasReferences(target.id())) {
            throw new ApiException(HttpStatus.CONFLICT, "UNIT_HAS_TASKS", "该单位已有任务记录，不能删除");
        }

        // 无外键兜底：并发安全来自上面的行锁与写用户侧的引用锁互斥，
        // 意外完整性错误交回统一 500 + 日志，不在这里翻译成业务冲突。
        mapper.deleteById(target.id());
    }

    private ParentChoice resolveParent(List<UnitRow> rows, String parentCode, Long movingId) {
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

        UnitRow parent = rows.stream()
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

    private static void requireCodeAvailable(List<UnitRow> rows, String code) {
        if (rows.stream().anyMatch(row -> row.code().equals(code))) {
            throw new ApiException(HttpStatus.CONFLICT, "UNIT_CODE_TAKEN",
                    "该单位编码已存在，请更换");
        }
    }

    private static UnitRow requireUnit(List<UnitRow> rows, String code) {
        return rows.stream()
                .filter(row -> row.code().equals(code))
                .findFirst()
                .orElseThrow(UnitService::unitNotFound);
    }

    private static boolean isDescendant(List<UnitRow> rows, long ancestorId, long candidateId) {
        Map<Long, UnitRow> byId = new HashMap<>();
        for (UnitRow row : rows) {
            byId.put(row.id(), row);
        }

        Set<Long> visited = new HashSet<>();
        Long currentId = candidateId;
        while (currentId != null && visited.add(currentId)) {
            if (currentId == ancestorId) {
                return true;
            }
            UnitRow current = byId.get(currentId);
            currentId = current == null ? null : current.parentId();
        }
        return false;
    }

    private static int maxRelativeDepth(List<UnitRow> rows, long rootId) {
        Map<Long, List<Long>> children = new HashMap<>();
        for (UnitRow row : rows) {
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

    private static long directChildCount(List<UnitRow> rows, long id) {
        return rows.stream().filter(row -> Objects.equals(row.parentId(), id)).count();
    }

    private Map<Long, Long> userCounts(List<UnitRow> rows) {
        return userUsage.countByUnitIds(rows.stream().map(UnitRow::id).toList());
    }

    /**
     * 写路径的用户计数必须走锁定读：一致性读会停在取锁之前的快照，
     * 「取锁前刚提交的用户」会被看漏，删除就会留下指向已删单位的用户。
     */
    private Map<Long, Long> userCountsForWrite(List<UnitRow> rows) {
        return userUsage.countByUnitIdsForShare(rows.stream().map(UnitRow::id).toList());
    }

    private static List<UnitTreeNode> buildTree(List<UnitRow> rows, Map<Long, Long> userCounts) {
        Map<Long, List<UnitRow>> childrenByParent = new HashMap<>();
        List<UnitRow> roots = new ArrayList<>();
        for (UnitRow row : rows) {
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

    private static UnitTreeNode buildNode(UnitRow row,
                                          Map<Long, List<UnitRow>> childrenByParent,
                                          Map<Long, Long> userCounts,
                                          Set<Long> path) {
        if (!path.add(row.id())) {
            throw hierarchyCorrupt();
        }
        List<UnitRow> childRows = childrenByParent.getOrDefault(row.id(), List.of());
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

    private static UnitView toView(UnitRow row, long childCount, long userCount) {
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
