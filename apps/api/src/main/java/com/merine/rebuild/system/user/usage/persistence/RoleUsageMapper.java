package com.merine.rebuild.system.user.usage.persistence;

import com.merine.rebuild.system.user.usage.RoleHolder;
import com.merine.rebuild.system.user.usage.RoleHolderCount;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 角色使用情况的只读 SQL；跨模块调用走 {@link com.merine.rebuild.system.user.usage.RoleUsageLookup}。
 * 动态条件与分页在 mapper/system/user/usage/RoleUsageMapper.xml。
 */
@Mapper
public interface RoleUsageMapper {

    List<RoleHolderCount> countHoldersByRoleCodes(@Param("roleCodes") Collection<String> roleCodes);

    long countHolders(@Param("roleCode") String roleCode);

    List<RoleHolder> findHolders(@Param("roleCode") String roleCode,
                                 @Param("offset") long offset,
                                 @Param("limit") int limit);
}
