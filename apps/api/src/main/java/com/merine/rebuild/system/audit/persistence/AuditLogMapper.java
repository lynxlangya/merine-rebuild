package com.merine.rebuild.system.audit.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 审计流水的读写：只追加 + 只读列表。
 *
 * 流水不可改：没有单行 update/delete。唯一的删除是保留期清理（deleteExpired），
 * 只按时间窗批量删过期行，窗口内的记录删不到。
 * 列表筛选由 XML 动态拼装，全部条件服务端执行（前端只提交条件与页码）。
 */
@Mapper
public interface AuditLogMapper {

    @Insert("""
            INSERT INTO sys_audit_log(occurred_at,actor_user_id,actor_login,actor_name,
                                      actor_unit_name,module,action,result,
                                      target_type,target_id,target_label,summary,request_id,client_ip)
            VALUES(#{row.occurredAt},#{row.actorUserId},#{row.actorLogin},#{row.actorName},
                   #{row.actorUnitName},#{row.module},#{row.action},#{row.result},
                   #{row.targetType},#{row.targetId},#{row.targetLabel},#{row.summary},
                   #{row.requestId},#{row.clientIp})
            """)
    void insert(@Param("row") AuditLogRow row);

    List<AuditLogRow> list(@Param("from") Instant from, @Param("to") Instant to,
                           @Param("actor") String actor, @Param("module") String module,
                           @Param("action") String action, @Param("result") String result,
                           @Param("targetType") String targetType, @Param("targetId") String targetId,
                           @Param("limit") int limit, @Param("offset") long offset);

    /** 过期区间概览：清理摘要要写清删了多少、覆盖哪段时间。走 occurred_at 索引的范围扫描。 */
    @Select("SELECT COUNT(*) AS total, MIN(occurred_at) AS earliest, MAX(occurred_at) AS latest "
            + "FROM sys_audit_log WHERE occurred_at < #{before}")
    Map<String, Object> expiredSummary(@Param("before") Instant before);

    /**
     * 按批次删除过期流水：单条 SQL 只删一批，避免大事务与长时间持锁；调用方循环到返回 0。
     * occurred_at 的严格小于就是硬边界——正好等于边界的记录保留。
     */
    @Delete("DELETE FROM sys_audit_log WHERE occurred_at < #{before} LIMIT #{limit}")
    int deleteExpired(@Param("before") Instant before, @Param("limit") int limit);

    long count(@Param("from") Instant from, @Param("to") Instant to,
               @Param("actor") String actor, @Param("module") String module,
               @Param("action") String action, @Param("result") String result,
               @Param("targetType") String targetType, @Param("targetId") String targetId);
}
