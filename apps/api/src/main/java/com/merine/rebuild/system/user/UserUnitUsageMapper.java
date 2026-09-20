package com.merine.rebuild.system.user;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 跨模块只读能力：单位模块只关心某个单位下有多少直属用户。 */
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
