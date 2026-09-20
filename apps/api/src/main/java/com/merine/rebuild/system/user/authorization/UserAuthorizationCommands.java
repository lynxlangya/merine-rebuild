package com.merine.rebuild.system.user.authorization;

import com.merine.rebuild.system.user.authorization.persistence.UserAuthorizationMapper;
import java.util.Collection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * system 模块对外提供的授权写命令。
 *
 * 角色的状态或权限集合变化后，持有该角色的账号必须重新登录才能取得新的 authority；
 * 角色管理用例调用这里递增这些账号的授权版本，而不是自己去写 sys_user——
 * sys_user 的写入 owner 始终是 user 模块。
 */
@Service
public class UserAuthorizationCommands {

    private final UserAuthorizationMapper mapper;

    public UserAuthorizationCommands(UserAuthorizationMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 让持有这批角色的账号旧会话失效（下次请求返回 401，需重新登录）。
     * 调用方在自己的事务里调用，因此这里的写入与角色变更同生共死。
     */
    @Transactional
    public int bumpVersionForRoleHolders(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return 0;
        }
        return mapper.bumpVersionForRoleHolders(roleCodes);
    }
}
