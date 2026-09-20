package com.merine.rebuild.system.user;

/**
 * 每次已认证请求用到的账号当前状态。
 * 只取判定失效所需的列，不重复读取密码哈希。
 */
public record UserAccountState(String accountStatus, String unitStatus, int authorizationVersion) {

    public boolean enabled() {
        return "ENABLED".equals(accountStatus) && "ENABLED".equals(unitStatus);
    }
}
