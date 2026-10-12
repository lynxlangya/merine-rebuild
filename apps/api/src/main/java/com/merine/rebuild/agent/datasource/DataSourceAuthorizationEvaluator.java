package com.merine.rebuild.agent.datasource;

import com.merine.rebuild.agent.datasource.dto.DataSourceCheckOutcome;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 只读授权判定：纯函数，输入是从 MySQL 授权元数据收集到的授权事实，输出检查结论。
 *
 * <p>规则是白名单式 fail closed：
 * <ul>
 *   <li>只允许 USAGE/SELECT/SHOW VIEW，其余权限（含可转授的 GRANT OPTION）一律视为写权限；</li>
 *   <li>账号持有任何可用角色时，因为当前连接读不到角色自身的授权行，直接判「授权无法确认」；</li>
 *   <li>元数据任一部分读取失败或无法归属，同样不能通过；</li>
 *   <li>勾选对象必须命中表级/库级/全局 SELECT，且必须是当前账号实际可读的 VIEW：
 *       列级 SELECT 不算整视图可读，底表或其他对象名也不允许进入白名单。</li>
 * </ul>
 */
final class DataSourceAuthorizationEvaluator {
    /** 判定为只读的权限白名单；不在其中的权限都按写权限处理。 */
    static final Set<String> READ_ONLY_PRIVILEGES = Set.of("USAGE", "SELECT", "SHOW VIEW");

    /** 一条已归属于目标账号（或其在用角色）的授权事实；schema/table/column 为空表示更粗粒度。 */
    record Privilege(String schema, String table, String column, String type, boolean grantable) {}

    record Decision(DataSourceCheckOutcome outcome, String message) {}

    private DataSourceAuthorizationEvaluator() {}

    static Decision evaluate(boolean metadataComplete, List<String> applicableRoles,
                             List<Privilege> privileges, String database, List<String> detectedViews,
                             List<String> selectedViews) {
        if (!metadataComplete) {
            return new Decision(DataSourceCheckOutcome.AUTHORIZATION_UNCONFIRMED,
                    "无法完整读取授权元数据，不能确认只读状态；请由数据库管理员核对账号权限");
        }
        if (!applicableRoles.isEmpty()) {
            return new Decision(DataSourceCheckOutcome.AUTHORIZATION_UNCONFIRMED,
                    "账号持有角色（" + String.join("、", applicableRoles)
                            + "），当前连接无法读取角色授权，不能确认只读状态；"
                            + "请为数据源使用不带角色的专用只读账号");
        }
        List<String> writes = privileges.stream()
                .filter(privilege -> !READ_ONLY_PRIVILEGES.contains(privilege.type()) || privilege.grantable())
                .map(DataSourceAuthorizationEvaluator::describe)
                .distinct()
                .sorted()
                .toList();
        if (!writes.isEmpty()) {
            return new Decision(DataSourceCheckOutcome.WRITE_PRIVILEGE,
                    "检测到账号持有非只读权限：" + String.join("、", writes)
                            + "；只读账号不得有写权限或转授权限");
        }
        for (String view : selectedViews) {
            // 业务上只允许查询视图：必须同时命中信息模式发现的 VIEW 集合与 SELECT 授权，
            // 避免账号拥有底表或库级 SELECT 时把底表当作“视图”加入白名单。
            if (!detectedViews.contains(view) || !hasSelect(privileges, database, view)) {
                return new Decision(DataSourceCheckOutcome.VIEW_DENIED,
                        "勾选对象「" + view + "」不是可查询的视图，或当前账号没有该视图的 SELECT 授权；"
                                + "勾选、授权都不会修改数据库对象类型或权限");
            }
        }
        if (selectedViews.isEmpty() && detectedViews.isEmpty()) {
            return new Decision(DataSourceCheckOutcome.PASSED,
                    "连接成功，未检测到写权限；该账号在目标库没有可查询的视图，请核对数据库授权");
        }
        if (selectedViews.isEmpty()) {
            return new Decision(DataSourceCheckOutcome.PASSED,
                    "连接成功，未检测到写权限；请从检测结果中勾选允许查询的视图");
        }
        return new Decision(DataSourceCheckOutcome.PASSED,
                "连接成功，未检测到写权限；勾选的 " + selectedViews.size() + " 个视图都有 SELECT 授权");
    }

    /** 表级 SELECT、库级 SELECT 或全局 SELECT 都覆盖该视图；列级只读不视为整视图可读。 */
    static boolean hasSelect(List<Privilege> privileges, String database, String view) {
        return privileges.stream().anyMatch(privilege ->
                "SELECT".equals(privilege.type()) && privilege.column() == null
                        && (privilege.schema() == null || privilege.schema().equals(database))
                        && (privilege.table() == null || privilege.table().equals(view)));
    }

    private static String describe(Privilege privilege) {
        return privilege.grantable() ? privilege.type() + "（可转授）" : privilege.type();
    }
}
