package com.merine.rebuild.system.user.admin.persistence;

import java.util.List;
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

    int deleteRoles(@Param("userId") long userId);

    int insertRoleByCode(@Param("userId") long userId, @Param("roleCode") String roleCode);
}
