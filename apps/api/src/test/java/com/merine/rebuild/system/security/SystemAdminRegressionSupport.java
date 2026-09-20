package com.merine.rebuild.system.security;

import com.merine.rebuild.support.MockMvcRegressionSupport;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;

/**
 * 需要系统管理权限的测试共用管理员 fixture。
 *
 * 只创建一个合成单位、一个管理员角色和一个管理员账号；
 * 用户管理的复杂账号矩阵仍由 system/user 自己的 fixture 提供。
 */
@TestPropertySource(properties =
        "merine.security.system-admin-role-codes=" + SystemAdminRegressionSupport.ADMIN_ROLE_CODE)
public abstract class SystemAdminRegressionSupport extends MockMvcRegressionSupport {
    protected static final String ADMIN_ROLE_CODE = "REGR-SYS-ADMIN";
    protected static final String ADMIN_LOGIN = "regr.sys.admin";
    protected static final String ADMIN_DISPLAY = "回归系统管理员";
    protected static final String ADMIN_PASSWORD = "regr-sys-secret-1";
    protected static final String ADMIN_UNIT_CODE = "REGR-SYS-UNIT";
    protected static final String ADMIN_UNIT_NAME = "回归系统管理单位";

    protected long adminUserId;

    @BeforeEach
    void resetSystemAdminFixture() {
        deleteSystemAdminFixture();
        jdbcTemplate.update("""
                INSERT INTO sys_unit (unit_code, unit_name, status)
                VALUES (?, ?, 'ENABLED')
                """, ADMIN_UNIT_CODE, ADMIN_UNIT_NAME);
        long unitId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_unit WHERE unit_code = ?", Long.class, ADMIN_UNIT_CODE);
        jdbcTemplate.update("""
                INSERT INTO sys_role (role_code, role_name, status)
                VALUES (?, ?, 'ENABLED')
                """, ADMIN_ROLE_CODE, "回归系统管理员角色");
        long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE role_code = ?", Long.class, ADMIN_ROLE_CODE);
        jdbcTemplate.update("""
                INSERT INTO sys_user (login_name, display_name, password_hash, unit_id)
                VALUES (?, ?, ?, ?)
                """, ADMIN_LOGIN, ADMIN_DISPLAY, passwordEncoder.encode(ADMIN_PASSWORD), unitId);
        adminUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE login_name = ?", Long.class, ADMIN_LOGIN);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)",
                adminUserId, roleId);
    }

    private void deleteSystemAdminFixture() {
        jdbcTemplate.update("""
                DELETE ur FROM sys_user_role ur
                 JOIN sys_user u ON u.id = ur.user_id
                WHERE u.login_name = ?
                """, ADMIN_LOGIN);
        jdbcTemplate.update("""
                DELETE ur FROM sys_user_role ur
                 JOIN sys_role r ON r.id = ur.role_id
                WHERE r.role_code = ?
                """, ADMIN_ROLE_CODE);
        jdbcTemplate.update("DELETE FROM sys_user WHERE login_name = ?", ADMIN_LOGIN);
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code = ?", ADMIN_ROLE_CODE);
        jdbcTemplate.update("UPDATE sys_unit SET parent_id = NULL WHERE unit_code = ?",
                ADMIN_UNIT_CODE);
        jdbcTemplate.update("DELETE FROM sys_unit WHERE unit_code = ?", ADMIN_UNIT_CODE);
    }

    protected MockHttpSession adminSession() throws Exception {
        return signIn(ADMIN_LOGIN, ADMIN_PASSWORD);
    }

    protected List<String> roleCodesForAdmin() {
        return jdbcTemplate.queryForList("""
                SELECT r.role_code
                  FROM sys_user_role ur
                  JOIN sys_role r ON r.id = ur.role_id
                 WHERE ur.user_id = ?
                 ORDER BY r.role_code
                """, String.class, adminUserId);
    }
}
