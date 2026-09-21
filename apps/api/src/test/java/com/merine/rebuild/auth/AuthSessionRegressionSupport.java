package com.merine.rebuild.auth;

import com.merine.rebuild.support.MockMvcRegressionSupport;
import com.merine.rebuild.system.security.PermissionCodes;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 认证与会话回归测试的公共基类：真实 Spring 上下文 + 真实 MySQL 测试库 + MockMvc。
 *
 * 这里刻意不替换任何 Bean（不用 H2、不用 mock Mapper）：要证明的是查询、密码校验、
 * 会话写入与失效判定在 MySQL 上的真实结果，mock 掉数据访问就只剩自证。
 *
 * 数据隔离分两层：
 * 1. profile 只从环境变量取测试库连接，且每个测试方法先核对实际连接的库名；
 * 2. 每个测试方法自己清理并插入带 {@value #LOGIN_NAME_PREFIX} 命名空间的合成行。
 * 不用 {@code @Transactional} 包住测试：会话与身份要跨多个 HTTP 请求写入，
 * 外层测试事务回滚盖不住这些写入，也会让应用读到未提交的数据。
 */
abstract class AuthSessionRegressionSupport extends MockMvcRegressionSupport {

    /** 合成数据命名空间：清理与插入都只涉及带这些前缀的行，不触碰库里的其他数据。 */
    protected static final String LOGIN_NAME_PREFIX = "regr.auth.";
    protected static final String UNIT_CODE_PREFIX = "REGR-AUTH-";
    protected static final String ROLE_CODE_PREFIX = "REGR-AUTH-";

    protected static final String LOGIN_NAME = LOGIN_NAME_PREFIX + "analyst";
    protected static final String RAW_PASSWORD = "regr-auth-secret-1";
    protected static final String DISPLAY_NAME = "回归测试账号";
    protected static final String UNIT_NAME = "回归测试单位";
    protected static final String ROLE_NAME = "回归测试角色";

    /** 本次测试插入的合成行主键。JUnit 每个测试方法新建实例，字段不会互相污染。 */
    protected long unitId;
    protected long roleId;
    protected long userId;

    @BeforeEach
    void resetSyntheticData() throws Exception {
        deleteSyntheticRows();
        insertSyntheticAccount();
    }

    /** 删除顺序固定为 sys_user_role → sys_user → sys_role → sys_unit，保持引用关系自洽。 */
    private void deleteSyntheticRows() {
        jdbcTemplate.update("""
                DELETE ur FROM sys_user_role ur
                 JOIN sys_user u ON u.id = ur.user_id
                WHERE u.login_name LIKE ?
                """, LOGIN_NAME_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE rp FROM sys_role_permission rp
                 JOIN sys_role r ON r.id = rp.role_id
                WHERE r.role_code LIKE ?
                """, ROLE_CODE_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE ur FROM sys_user_role ur
                 JOIN sys_role r ON r.id = ur.role_id
                WHERE r.role_code LIKE ?
                """, ROLE_CODE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_user WHERE login_name LIKE ?", LOGIN_NAME_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code LIKE ?", ROLE_CODE_PREFIX + "%");
        jdbcTemplate.update("UPDATE sys_unit SET parent_id = NULL WHERE unit_code LIKE ?",
                UNIT_CODE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_unit WHERE unit_code LIKE ?", UNIT_CODE_PREFIX + "%");
    }

    /**
     * 插入本次测试的合成账号：启用状态的单位、角色、账号与一条授予关系。
     * 密码用应用自己的 {@link PasswordEncoder} 编码，保证库里就是 BCrypt 哈希。
     */
    private void insertSyntheticAccount() {
        jdbcTemplate.update("INSERT INTO sys_unit (unit_code, unit_name, status) VALUES (?, ?, 'ENABLED')",
                UNIT_CODE_PREFIX + "UNIT", UNIT_NAME);
        unitId = jdbcTemplate.queryForObject("SELECT id FROM sys_unit WHERE unit_code = ?", Long.class,
                UNIT_CODE_PREFIX + "UNIT");

        jdbcTemplate.update("INSERT INTO sys_role (role_code, role_name, status) VALUES (?, ?, 'ENABLED')",
                ROLE_CODE_PREFIX + "ROLE", ROLE_NAME);
        roleId = jdbcTemplate.queryForObject("SELECT id FROM sys_role WHERE role_code = ?", Long.class,
                ROLE_CODE_PREFIX + "ROLE");
        // 认证回归用 /api/bootstrap 证明「已认证请求」的读写行为，因此这个角色要有工程诊断的两个权限码；
        // 权限码在按钮级模型下也是普通权限，认证测试不关心菜单结构。
        for (String code : List.of(PermissionCodes.DIAGNOSTICS_READ, PermissionCodes.DIAGNOSTICS_WRITE)) {
            jdbcTemplate.update("""
                    INSERT INTO sys_role_permission (role_id, permission_id)
                    SELECT ?, id FROM sys_permission WHERE permission_code = ?
                    """, roleId, code);
        }

        jdbcTemplate.update("""
                INSERT INTO sys_user (login_name, display_name, password_hash, unit_id)
                VALUES (?, ?, ?, ?)
                """, LOGIN_NAME, DISPLAY_NAME, passwordEncoder.encode(RAW_PASSWORD), unitId);
        userId = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE login_name = ?", Long.class,
                LOGIN_NAME);

        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
    }

    protected void updateAccountStatus(String status) {
        jdbcTemplate.update("UPDATE sys_user SET status = ? WHERE id = ?", status, userId);
    }

    protected void bumpAuthorizationVersion() {
        jdbcTemplate.update("UPDATE sys_user SET authorization_version = authorization_version + 1 WHERE id = ?",
                userId);
    }

    protected String passwordHashInDatabase() {
        return jdbcTemplate.queryForObject("SELECT password_hash FROM sys_user WHERE id = ?", String.class,
                userId);
    }
}
