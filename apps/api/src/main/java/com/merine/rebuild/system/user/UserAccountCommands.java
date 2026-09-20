package com.merine.rebuild.system.user;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * system 模块对外提供的账号写命令。
 *
 * sys_user 的写入 owner 是本模块，认证流程只通过这里登记登录时间，
 * 不直接持有本模块的 Mapper。
 */
@Service
public class UserAccountCommands {
    private final UserAccountMapper mapper;

    public UserAccountCommands(UserAccountMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 登记一次成功登录。失败直接抛出，不静默吞掉——否则界面上的“最近登录时间”
     * 会长期为空而看不出原因。
     */
    @Transactional
    public void recordLogin(long userId, Instant at) {
        mapper.touchLastLoginAt(userId, at);
    }
}
