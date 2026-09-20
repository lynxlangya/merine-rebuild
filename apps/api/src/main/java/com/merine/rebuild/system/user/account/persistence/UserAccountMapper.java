package com.merine.rebuild.system.user.account.persistence;

import com.merine.rebuild.system.user.account.UserAccountLookup;
import com.merine.rebuild.system.user.account.UserAccountState;
import java.time.Instant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 认证所需的账号查询与登录时间登记；其余管理写入由同一 user 模块的管理用例负责。
 * 多表关联与聚合写在 mapper/system/user/account/UserAccountMapper.xml。
 * 跨模块查询请使用 {@link UserAccountLookup}。
 */
@Mapper
public interface UserAccountMapper {

    UserAccountRow findByLoginName(@Param("loginName") String loginName);

    UserAccountState findStateById(@Param("userId") long userId);

    @Update("UPDATE sys_user SET last_login_at = #{at} WHERE id = #{userId}")
    void touchLastLoginAt(@Param("userId") long userId, @Param("at") Instant at);
}
