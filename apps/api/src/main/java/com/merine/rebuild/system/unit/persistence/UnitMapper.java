package com.merine.rebuild.system.unit.persistence;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 单位用例的持久化入口。
 *
 * 查询、组织树读取和写路径共用同一份 SQL 入口；
 * 需要跨模块读取单位选项时走 UnitLookup，不直接导入本 Mapper。
 */
@Mapper
public interface UnitMapper {

    List<UnitRow> findAll();

    UnitRow findByCode(@Param("code") String code);

    /**
     * 锁住当前全部单位行；后续读写都在这组行锁内完成。
     * 返回主键只是为了让 MyBatis 执行一条明确的 SELECT ... FOR UPDATE。
     */
    List<Long> lockAllIds();

    int insert(@Param("code") String code,
               @Param("name") String name,
               @Param("parentId") Long parentId,
               @Param("level") int level,
               @Param("areaCode") String areaCode);

    int update(@Param("id") long id,
               @Param("name") String name,
               @Param("parentId") Long parentId,
               @Param("level") int level,
               @Param("areaCode") String areaCode,
               @Param("version") int version);

    int deleteById(@Param("id") long id);
}
