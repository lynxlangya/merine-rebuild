package com.merine.rebuild.system.user.account;

import com.merine.rebuild.system.user.account.persistence.UserAccountMapper;
import com.merine.rebuild.system.user.account.persistence.UserAccountRow;
import com.merine.rebuild.system.user.support.AggregatedColumns;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * system 模块对外的账号查询能力。
 *
 * 跨模块（当前只有 auth）通过它读取账号事实，不导入本模块的 Mapper 或持久化细节。
 * 在这里把数据库投射转换为公开账号事实，SQL 聚合格式不传播到认证模块。
 */
@Service
public class UserAccountLookup {
    private final UserAccountMapper mapper;

    public UserAccountLookup(UserAccountMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public UserAccount findByLoginName(String loginName) {
        UserAccountRow row = mapper.findByLoginName(loginName);
        if (row == null) {
            return null;
        }
        return new UserAccount(row.id(), row.loginName(), row.displayName(), row.passwordHash(),
                row.unitName(), row.unitStatus(), row.accountStatus(), row.authorizationVersion(),
                AggregatedColumns.split(row.aggregatedRoleCodes()),
                AggregatedColumns.split(row.aggregatedRoleNames()),
                AggregatedColumns.split(row.aggregatedPermissionCodes()));
    }

    @Transactional(readOnly = true)
    public UserAccountState findStateById(long userId) {
        return mapper.findStateById(userId);
    }
}
