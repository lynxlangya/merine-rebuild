package com.merine.rebuild.system.seed;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 演示账号初始化用的读写。只插入缺失的行，不更新已有行——
 * 尤其不覆盖已存在账号的密码与归属，保证重复执行不改变现有账号。
 * 单位是组织参考数据，由迁移维护；这里只按编码查找，不创建单位。
 */
@Mapper
public interface DemoAccountMapper {

    /**
     * 取单位时一并加共享锁：账号要引用这个单位，和写用户路径同一条规则——
     * 没有外键，引用前必须锁住被引用行（见 docs/rules/database.md）。
     */
    @Select("""
            SELECT id
              FROM sys_unit
             WHERE unit_code = CONVERT(#{code} USING ascii) COLLATE ascii_bin
              FOR SHARE
            """)
    Long findUnitId(@Param("code") String code);

    @Select("SELECT id FROM sys_role WHERE role_code = CONVERT(#{code} USING ascii) COLLATE ascii_bin")
    Long findRoleId(@Param("code") String code);

    @Insert("INSERT INTO sys_role (role_code, role_name) VALUES (#{code}, #{name})")
    void insertRole(@Param("code") String code, @Param("name") String name);

    @Select("SELECT id FROM sys_user WHERE login_name = #{loginName}")
    Long findUserId(@Param("loginName") String loginName);

    @Insert("""
            INSERT INTO sys_user (login_name, display_name, password_hash, unit_id)
            VALUES (#{loginName}, #{displayName}, #{passwordHash}, #{unitId})
            """)
    void insertUser(@Param("loginName") String loginName,
                    @Param("displayName") String displayName,
                    @Param("passwordHash") String passwordHash,
                    @Param("unitId") long unitId);

    @Select("SELECT COUNT(*) FROM sys_user_role WHERE user_id = #{userId} AND role_id = #{roleId}")
    int countUserRole(@Param("userId") long userId, @Param("roleId") long roleId);

    @Insert("INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    void insertUserRole(@Param("userId") long userId, @Param("roleId") long roleId);
}
