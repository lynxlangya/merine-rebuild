package com.merine.rebuild.system.user.usage;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 对业务模块公开最小人员事实，永不返回凭据、角色或权限集合。 */
@Service
public class UserDirectoryLookup {
    public record Person(long id, String loginName, String displayName, long unitId, String status) {}
    private final UserDirectoryMapper mapper;
    public UserDirectoryLookup(UserDirectoryMapper mapper) { this.mapper = mapper; }
    @Transactional(readOnly = true)
    public Person find(long id) { return mapper.find(id); }
    @Transactional(readOnly = true)
    public Person findByLogin(String loginName) { return mapper.findByLogin(loginName); }
    /** 调用方先锁单位，再锁用户；与账号调整归属及启停互斥。 */
    @Transactional
    public Person lock(long id) { return mapper.lock(id); }
}
