package com.merine.rebuild.system.org;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 单位管理的读写 SQL。
 *
 * 写路径先锁全部单位行，再在 Service 里校验单根、层级和循环引用；
 * 当前组织规模约 50 行，串行化单位维护换取清楚的不变量是合适的取舍。
 */
@Mapper
public interface UnitAdminMapper {

    List<UnitAdminRow> findAll();

    /**
     * 锁住当前全部单位行；后续读写都在这组行锁内完成。
     * 返回主键只是为了让 MyBatis 执行一条明确的 SELECT ... FOR UPDATE。
     */
    List<Long> lockAllIds();

    UnitAdminRow findByCode(@Param("code") String code);

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
