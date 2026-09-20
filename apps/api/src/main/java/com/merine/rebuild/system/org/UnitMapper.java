package com.merine.rebuild.system.org;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 归 system 模块所有；跨模块使用请走 {@link UnitLookup}。 */
@Mapper
public interface UnitMapper {

    @Select("""
            SELECT u.unit_code AS code,
                   u.unit_name AS name,
                   u.status,
                   p.unit_code AS parentCode,
                   u.unit_level AS level
              FROM sys_unit u
              LEFT JOIN sys_unit p ON p.id = u.parent_id
             ORDER BY u.unit_level, u.unit_code
            """)
    List<UnitSummary> findAll();

    /**
     * unit_code 是 ascii_bin 而 JDBC 参数是 utf8mb4：参数含非 ASCII 字符时
     * 直接比较会触发 collation 冲突并变成 500，因此把参数转成 ascii 再比。
     */
    @Select("""
            SELECT u.unit_code AS code,
                   u.unit_name AS name,
                   u.status,
                   p.unit_code AS parentCode,
                   u.unit_level AS level
              FROM sys_unit u
              LEFT JOIN sys_unit p ON p.id = u.parent_id
             WHERE u.unit_code = CONVERT(#{code} USING ascii) COLLATE ascii_bin
            """)
    UnitSummary findByCode(@Param("code") String code);
}
