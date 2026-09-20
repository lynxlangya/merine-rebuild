package com.merine.rebuild.system.role;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 归 system 模块所有；跨模块使用请走 {@link RoleLookup}。 */
@Mapper
public interface RoleMapper {

    @Select("""
            SELECT role_code AS code, role_name AS name, status
              FROM sys_role
             ORDER BY role_code
            """)
    List<RoleSummary> findAll();
}
