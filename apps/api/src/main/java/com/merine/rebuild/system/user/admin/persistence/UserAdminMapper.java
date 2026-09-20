package com.merine.rebuild.system.user.admin.persistence;

import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户管理的读写。动态筛选与多表查询写在 mapper/system/user/admin/UserAdminMapper.xml。
 *
 * 单位与角色一律用业务编码定位，id 通过子查询在 SQL 内解析：
 * 调用方不需要知道自增主键，也不会把主键泄露到接口上。
 */
@Mapper
public interface UserAdminMapper {

    List<UserRow> findPage(@Param("q") UserQuery query,
                           @Param("offset") long offset,
                           @Param("limit") int limit);

    /** 按 id 批量回读，供批量动作一次取回变更后的状态，避免逐行查询。 */
    List<UserRow> findByIds(@Param("ids") List<Long> ids);

    long count(@Param("q") UserQuery query);

    UserRow findById(@Param("id") long id);

    UserRow findByLoginName(@Param("loginName") String loginName);

    void insert(@Param("loginName") String loginName,
                @Param("displayName") String displayName,
                @Param("passwordHash") String passwordHash,
                @Param("unitCode") String unitCode);

    int updateBasic(@Param("id") long id,
                    @Param("displayName") String displayName,
                    @Param("unitCode") String unitCode,
                    @Param("version") int version);

    int updatePassword(@Param("id") long id, @Param("passwordHash") String passwordHash);

    /** 角色或所属单位变化时递增；旧会话凭旧版本在下次请求失效。 */
    int bumpAuthorizationVersion(@Param("id") long id);

    int updateStatus(@Param("ids") List<Long> ids, @Param("status") String status);

    /**
     * 统计排除这批账号之后，还剩几个**真正可用**的管理员。
     *
     * 判定口径必须与登录放行条件一致：账号启用、所属单位启用、角色启用，三者缺一
     * 该账号就登不进来（见 UserAccount.enabled() 与登录时的角色聚合）。
     * 只看 sys_user.status 会把“已停用单位下的管理员”算成可用，导致守卫放行了
     * 停用最后一个管理员的请求。
     *
     * 加 FOR UPDATE 是为了让并发的停用/改角色请求在这组行上串行：
     * 否则两个请求各自读到 2、各自排除自己后都得到 1，双双通过检查。
     */
    long countRemainingEnabledAdmins(@Param("adminRoleCodes") Set<String> adminRoleCodes,
                                    @Param("excludingIds") List<Long> excludingIds);

    int deleteRoles(@Param("userId") long userId);

    int insertRoleByCode(@Param("userId") long userId, @Param("roleCode") String roleCode);
}
