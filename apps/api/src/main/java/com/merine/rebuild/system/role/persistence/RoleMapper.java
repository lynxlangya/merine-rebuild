package com.merine.rebuild.system.role.persistence;

import com.merine.rebuild.system.role.dto.RoleSummary;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 角色的读写。动态筛选与多表查询在 mapper/system/role/RoleMapper.xml。
 *
 * 角色与权限一律用业务编码对外，id 只在 SQL 内部用于关联 sys_role_permission；
 * 成员数不在这里统计——sys_user_role 的 owner 是 user 模块，走 RoleUsageLookup。
 */
@Mapper
public interface RoleMapper {

    /** 角色选项：用户管理的筛选与表单选择共用，按编码稳定排序。 */
    @Select("""
            SELECT role_code AS code, role_name AS name, status
              FROM sys_role
             ORDER BY role_code
            """)
    List<RoleSummary> findAllSummaries();

    List<RoleRow> findPage(@Param("q") RoleQuery query,
                           @Param("offset") long offset,
                           @Param("limit") int limit);

    long count(@Param("q") RoleQuery query);

    RoleRow findByCode(@Param("code") String code);

    List<RoleRow> findByCodes(@Param("codes") Collection<String> codes);

    List<String> findPermissionCodesByRoleId(@Param("roleId") long roleId);

    /** 哪些角色授予了这批权限码；菜单删除时的预览与回执都用它。 */
    List<String> findRoleCodesByPermissionIds(@Param("permissionIds") Collection<Long> permissionIds);

    int deleteGrantsByPermissionIds(@Param("permissionIds") Collection<Long> permissionIds);

    void insert(@Param("code") String code,
                @Param("name") String name,
                @Param("description") String description);

    int updateBasic(@Param("id") long id,
                    @Param("name") String name,
                    @Param("description") String description,
                    @Param("version") int version);

    int updateStatus(@Param("codes") Collection<String> codes, @Param("status") String status);

    void deletePermissions(@Param("roleId") long roleId);

    void deleteById(@Param("id") long id);

    /** 权限码不存在时插入 0 行；服务层已先校验，这里作为兜底。 */
    int insertPermissionByCode(@Param("roleId") long roleId,
                               @Param("permissionCode") String permissionCode);
}
