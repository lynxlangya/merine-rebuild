package com.merine.rebuild.system.user.authorization.persistence;

import java.util.Collection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** sys_user 的授权版本写入；SQL 在 mapper/system/user/authorization/UserAuthorizationMapper.xml。 */
@Mapper
public interface UserAuthorizationMapper {

    int bumpVersionForRoleHolders(@Param("roleCodes") Collection<String> roleCodes);
}
