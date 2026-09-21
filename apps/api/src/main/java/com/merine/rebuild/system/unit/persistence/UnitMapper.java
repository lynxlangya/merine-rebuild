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
     * 引用锁：按主键共享锁住目标单位行，供其他模块的写路径在事务里调用。
     * 与 {@link #lockAllIds()} 的全表排他锁互斥，是已移除的外键父行锁的替代。
     * 按主键而不是按 unit_code 取锁：只锁这一行的主键记录，
     * 不会像二级索引上的锁定读那样与「按主键删行」形成反向加锁而死锁。
     */
    UnitRow findByIdForShare(@Param("id") long id);

    /**
     * 写路径的树读取：结果与 {@link #findAll()} 相同，但走锁定读。
     * 一致性读可能停在取锁之前的快照，父级存在、层级与引用校验必须拿最新已提交状态。
     */
    List<UnitRow> findAllForShare();

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
