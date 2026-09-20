package com.merine.rebuild.system.org;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 归 system 模块所有；跨模块使用请走 {@link UnitLookup}。 */
@Mapper
public interface UnitMapper {

    @Select("""
            SELECT unit_code AS code, unit_name AS name, status
              FROM sys_unit
             ORDER BY unit_code
            """)
    List<UnitSummary> findAll();

    /**
     * unit_code 是 ascii_bin 而 JDBC 参数是 utf8mb4：参数含非 ASCII 字符时
     * 直接比较会触发 collation 冲突并变成 500，因此把参数转成 ascii 再比。
     */
    @Select("""
            SELECT unit_code AS code, unit_name AS name, status
              FROM sys_unit
             WHERE unit_code = CONVERT(#{code} USING ascii) COLLATE ascii_bin
            """)
    UnitSummary findByCode(@Param("code") String code);
}
