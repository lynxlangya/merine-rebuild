package com.merine.rebuild.system.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * system 模块对外的账号查询能力。
 *
 * 跨模块（当前只有 auth）通过它读取账号事实，不导入本模块的 Mapper 或持久化细节。
 * 这里只做查询，sys_user 的写入 owner 仍是本模块，本轮没有写入用例。
 */
@Service
public class UserAccountLookup {
    private final UserAccountMapper mapper;

    public UserAccountLookup(UserAccountMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public UserAccount findByLoginName(String loginName) {
        return mapper.findByLoginName(loginName);
    }

    @Transactional(readOnly = true)
    public UserAccountState findStateById(long userId) {
        return mapper.findStateById(userId);
    }
}
