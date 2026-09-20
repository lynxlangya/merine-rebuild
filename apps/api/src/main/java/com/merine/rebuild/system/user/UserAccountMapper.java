package com.merine.rebuild.system.user;

import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * sys_user 的读取入口。多表关联与聚合写在 mapper/system/UserAccountMapper.xml。
 * 本 Mapper 归 system 模块所有，跨模块调用请使用 {@link UserAccountLookup}。
 */
@Mapper
public interface UserAccountMapper {

    UserAccount findByLoginName(@Param("loginName") String loginName);

    UserAccountState findStateById(@Param("userId") long userId);

    @Update("UPDATE sys_user SET last_login_at = #{at} WHERE id = #{userId}")
    void touchLastLoginAt(@Param("userId") long userId, @Param("at") Instant at);
}
