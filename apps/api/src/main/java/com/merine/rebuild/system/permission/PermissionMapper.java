package com.merine.rebuild.system.permission;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 功能权限码字典的读写。
 * 归 system.permission 模块所有；跨模块读取走 {@link PermissionLookup}，
 * 写入只允许通过 {@link PermissionCommands}（菜单管理维护节点时调用）。
 */
@Mapper
public interface PermissionMapper {

    @Select("""
            SELECT id, permission_code AS code, permission_name AS name, description
              FROM sys_permission
             ORDER BY permission_code
            """)
    List<PermissionSummary> findAll();

    @Select("SELECT permission_code FROM sys_permission ORDER BY permission_code")
    List<String> findAllCodes();

    @Select("""
            <script>
            SELECT id, permission_code AS code, permission_name AS name, description
              FROM sys_permission
             WHERE permission_code IN
             <foreach item="code" collection="codes" open="(" separator="," close=")">
                 CONVERT(#{code} USING ascii) COLLATE ascii_bin
             </foreach>
            </script>
            """)
    List<PermissionSummary> findByCodes(@Param("codes") Collection<String> codes);

    @Select("SELECT id FROM sys_permission WHERE permission_code = CONVERT(#{code} USING ascii) COLLATE ascii_bin")
    Long findIdByCode(@Param("code") String code);

    @Insert("""
            INSERT INTO sys_permission (permission_code, permission_name, description)
            VALUES (#{code}, #{name}, #{description,jdbcType=VARCHAR})
            """)
    void insert(@Param("code") String code,
                @Param("name") String name,
                @Param("description") String description);

    @org.apache.ibatis.annotations.Update("""
            UPDATE sys_permission
               SET permission_name = #{name},
                   description = #{description,jdbcType=VARCHAR},
                   updated_at = CURRENT_TIMESTAMP(6)
             WHERE id = #{id}
            """)
    int updateName(@Param("id") long id,
                   @Param("name") String name,
                   @Param("description") String description);

    @org.apache.ibatis.annotations.Delete("""
            <script>
            DELETE FROM sys_permission
             WHERE id IN
             <foreach item="id" collection="ids" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int deleteByIds(@Param("ids") Collection<Long> ids);
}
