package com.merine.rebuild.system.user.authorization;

import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.system.security.BuiltinAdminRoles;
import com.merine.rebuild.system.security.PermissionCodes;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 管理底线守卫：系统里必须始终至少有一个「可用管理账号」。
 *
 * 可用管理账号 = 账号启用 + 所属单位启用 +（持有内置管理员角色，
 * 或同时持有 {@link PermissionCodes#ADMIN_BASELINE} 里的全部权限）。
 *
 * 使用协议（两步，都在调用方的事务里，顺序不能颠倒）：
 * <ol>
 *   <li>{@link #lock()}：以内置管理员角色行作为串行锚点，把并发的授权写入排队；</li>
 *   <li>执行本次写入后调用 {@link #requireUsableAdminRemains(String)}：统计为 0 就抛 409，
 *       整个事务回滚。</li>
 * </ol>
 *
 * 为什么先写后查：改一次用户角色、批量停用账号、调整角色权限，对覆盖的影响形状各不相同。
 * 与其为每种形状各推一套「变更后是否仍然覆盖」，不如直接看变更后的真实状态。
 * 并发正确性来自第 1 步：所有可能减少覆盖的写入都先锁同一组行，后来的事务会阻塞到前者提交，
 * 之后的锁定读看到的是最新已提交状态，因此不会两个请求各自读到旧数据双双放行。
 */
@Component
public class AdminCoverageGuard {

    private final AdminCoverageMapper mapper;
    private final BuiltinAdminRoles builtinRoles;

    public AdminCoverageGuard(AdminCoverageMapper mapper, BuiltinAdminRoles builtinRoles) {
        this.mapper = mapper;
        this.builtinRoles = builtinRoles;
    }

    /**
     * 取得授权写入的串行锚点。必须是事务里的第一条语句：
     * 在它之前做普通读取会把一致性快照定在锁之前，后面的统计就可能看不到并发事务的提交。
     */
    public void lock() {
        mapper.lockBuiltinRoleRows(builtinRoles.codes());
    }

    /**
     * 变更完成后核对覆盖。失败即抛 409，由调用方的事务回滚所有写入。
     */
    public void requireUsableAdminRemains(String message) {
        long remaining = mapper.countUsableAdminAccounts(builtinRoles.codes(),
                PermissionCodes.ADMIN_BASELINE, PermissionCodes.ADMIN_BASELINE.size());
        if (remaining == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_USER_ADMIN", message);
        }
    }
}
