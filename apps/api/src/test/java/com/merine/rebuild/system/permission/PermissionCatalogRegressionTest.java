package com.merine.rebuild.system.permission;

import static org.assertj.core.api.Assertions.assertThat;

import com.merine.rebuild.support.MockMvcRegressionSupport;
import com.merine.rebuild.system.menu.MenuBootstrap;
import com.merine.rebuild.system.security.PermissionCodes;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 权限字典与引导菜单的一致性回归。
 *
 * 权限码有三处登记：代码常量（判权与门禁）、迁移写入的 sys_permission、以及
 * 「恢复默认菜单」用的 {@link MenuBootstrap} 清单。三者不一致会让页面勾到不存在的码、
 * 或者恢复出来的菜单缺按钮，因此都用真实库核对，而不是靠各处各自记忆。
 */
@DisplayName("功能权限码一致性")
class PermissionCatalogRegressionTest extends MockMvcRegressionSupport {

    @Autowired
    private MenuBootstrap bootstrap;

    @Test
    @DisplayName("代码常量与 sys_permission 参考数据一一对应")
    void codeConstantsMatchTheDatabaseCatalog() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT permission_code, permission_name
                  FROM sys_permission
                 ORDER BY permission_code
                """);

        assertThat(rows).extracting(row -> row.get("permission_code"))
                .as("代码常量与迁移清单必须完全一致")
                .containsExactlyInAnyOrderElementsOf(PermissionCodes.all());
        assertThat(rows).allSatisfy(row ->
                assertThat((String) row.get("permission_name")).as("权限名称不能为空").isNotBlank());
    }

    @Test
    @DisplayName("恢复默认菜单的清单与迁移写入的菜单树一致：页面 route key 与按钮权限码都能对上")
    void bootstrapMenuMatchesTheMigratedMenuTree() {
        List<String> routeKeys = jdbcTemplate.queryForList(
                "SELECT route_key FROM sys_menu WHERE menu_type = 'PAGE' ORDER BY route_key",
                String.class);
        List<String> buttonCodes = jdbcTemplate.queryForList("""
                SELECT p.permission_code
                  FROM sys_menu m
                  JOIN sys_permission p ON p.id = m.permission_id
                 WHERE m.menu_type IN ('BUTTON', 'TAB')
                 ORDER BY p.permission_code
                """, String.class);
        List<String> directoryNames = jdbcTemplate.queryForList(
                "SELECT menu_name FROM sys_menu WHERE menu_type = 'DIRECTORY' ORDER BY menu_name",
                String.class);

        assertThat(routeKeys)
                .containsExactlyInAnyOrderElementsOf(bootstrap.entries().stream()
                        .filter(entry -> entry.type() == MenuBootstrap.Type.PAGE)
                        .map(MenuBootstrap.Entry::routeKey)
                        .toList());
        assertThat(buttonCodes)
                .containsExactlyInAnyOrderElementsOf(bootstrap.entries().stream()
                        .filter(entry -> entry.type() == MenuBootstrap.Type.BUTTON)
                        .map(MenuBootstrap.Entry::permissionCode)
                        .toList());
        assertThat(directoryNames)
                .containsExactlyInAnyOrderElementsOf(bootstrap.entries().stream()
                        .filter(entry -> entry.type() == MenuBootstrap.Type.DIRECTORY)
                        .map(MenuBootstrap.Entry::name)
                        .toList());
    }
}
