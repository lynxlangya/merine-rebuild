package com.merine.rebuild.system.unit;

import com.merine.rebuild.system.security.SystemAdminRegressionSupport;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;

/**
 * 单位管理回归测试的最小 fixture。
 *
 * 只造一个系统管理员、一个一级合成单位和一个普通业务账号；
 * 不复制用户管理测试的复杂账号矩阵。
 */
abstract class UnitAdminRegressionSupport extends SystemAdminRegressionSupport {
    protected static final String USERS_PATH = "/api/system/users";
    protected static final String LOGIN_NAME_PREFIX = "regr.unitadm.";
    protected static final String UNIT_CODE_PREFIX = "REGR-UNITADMIN-";

    protected static final String UNIT_ALPHA = UNIT_CODE_PREFIX + "ALPHA";
    protected static final String UNIT_ALPHA_NAME = "回归单位甲";
    protected static final String ANALYST_ROLE_CODE = "REGR-UNIT-ANALYST";
    protected static final String ANALYST_LOGIN = LOGIN_NAME_PREFIX + "analyst";

    /** 单位测试中所有合成账号共用的明文密码；库里只存 BCrypt 哈希。 */
    protected static final String RAW_PASSWORD = "regr-unit-secret-1";

    @BeforeEach
    void resetUnitFixture() {
        deleteUnitFixture();
        jdbcTemplate.update("""
                INSERT INTO sys_unit (unit_code, unit_name, status)
                VALUES (?, ?, 'ENABLED')
                """, UNIT_ALPHA, UNIT_ALPHA_NAME);
        long unitId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_unit WHERE unit_code = ?", Long.class, UNIT_ALPHA);
        jdbcTemplate.update("""
                INSERT INTO sys_role (role_code, role_name, status)
                VALUES (?, ?, 'ENABLED')
                """, ANALYST_ROLE_CODE, "回归单位业务角色");
        long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_role WHERE role_code = ?", Long.class, ANALYST_ROLE_CODE);
        jdbcTemplate.update("""
                INSERT INTO sys_user (login_name, display_name, password_hash, unit_id)
                VALUES (?, ?, ?, ?)
                """, ANALYST_LOGIN, "回归单位业务账号", passwordEncoder.encode(RAW_PASSWORD), unitId);
        long analystId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE login_name = ?", Long.class, ANALYST_LOGIN);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)",
                analystId, roleId);
    }

    private void deleteUnitFixture() {
        jdbcTemplate.update("""
                DELETE ur FROM sys_user_role ur
                 JOIN sys_user u ON u.id = ur.user_id
                WHERE u.login_name LIKE ?
                """, LOGIN_NAME_PREFIX + "%");
        jdbcTemplate.update("""
                DELETE ur FROM sys_user_role ur
                 JOIN sys_role r ON r.id = ur.role_id
                WHERE r.role_code = ?
                """, ANALYST_ROLE_CODE);
        jdbcTemplate.update("DELETE FROM sys_user WHERE login_name LIKE ?", LOGIN_NAME_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_code = ?", ANALYST_ROLE_CODE);
        jdbcTemplate.update("UPDATE sys_unit SET parent_id = NULL WHERE unit_code LIKE ?",
                UNIT_CODE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM sys_unit WHERE unit_code LIKE ?", UNIT_CODE_PREFIX + "%");
    }

    protected String createBody(String loginName, String displayName, String unitCode,
                                List<String> roleCodes, String password) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("loginName", loginName);
        body.put("displayName", displayName);
        body.put("unitCode", unitCode);
        body.put("roleCodes", roleCodes);
        body.put("password", password);
        return objectMapper.writeValueAsString(body);
    }
}
