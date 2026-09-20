package com.merine.rebuild.system.user.usage;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 用户模块内部的统计 SQL；跨模块调用走 UserUnitUsageLookup。 */
@Mapper
public interface UserUnitUsageMapper {

    @Select("""
            <script>
            SELECT unit_id AS unitId, COUNT(*) AS userCount
              FROM sys_user
             WHERE unit_id IN
             <foreach item="id" collection="ids" open="(" separator="," close=")">
                 #{id}
             </foreach>
             GROUP BY unit_id
            </script>
            """)
    List<UserUnitCount> countByUnitIds(@Param("ids") Collection<Long> ids);
}
