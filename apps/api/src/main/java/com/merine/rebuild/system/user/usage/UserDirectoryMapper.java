package com.merine.rebuild.system.user.usage;

import org.apache.ibatis.annotations.*;

@Mapper
interface UserDirectoryMapper {
    @Select("SELECT id,login_name AS loginName,display_name AS displayName,unit_id AS unitId,status FROM sys_user WHERE id=#{id}")
    UserDirectoryLookup.Person find(long id);
    @Select("SELECT id,login_name AS loginName,display_name AS displayName,unit_id AS unitId,status FROM sys_user WHERE login_name=#{loginName}")
    UserDirectoryLookup.Person findByLogin(String loginName);
    @Select("SELECT id,login_name AS loginName,display_name AS displayName,unit_id AS unitId,status FROM sys_user WHERE id=#{id} FOR UPDATE")
    UserDirectoryLookup.Person lock(long id);
}
