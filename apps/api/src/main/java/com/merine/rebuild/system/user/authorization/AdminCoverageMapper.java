package com.merine.rebuild.system.user.authorization;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 管理覆盖统计的 SQL。
 *
 * owner 是 user 模块（sys_user / sys_user_role 的写入 owner）：需要判断「谁还能管理系统」的
 * 调用方（用户管理与角色管理）都走 {@link AdminCoverageGuard}。查询只读 sys_role /
 * sys_role_permission / sys_permission，不写它们——那三张表归 role 模块所有。
 */
@Mapper
public interface AdminCoverageMapper {

    /** 锁定内置管理员角色行，作为授权写入的串行锚点。 */
    List<Long> lockBuiltinRoleRows(@Param("builtinRoleCodes") Collection<String> builtinRoleCodes);

    /**
     * 统计变更后仍可用的管理账号数。锁定读：始终看最新已提交状态，不受一致性快照影响。
     */
    long countUsableAdminAccounts(@Param("builtinRoleCodes") Collection<String> builtinRoleCodes,
                                  @Param("baselinePermissionCodes") Collection<String> baselinePermissionCodes,
                                  @Param("baselineCount") int baselineCount);
}
