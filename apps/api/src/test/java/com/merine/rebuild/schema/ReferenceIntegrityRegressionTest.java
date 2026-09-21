package com.merine.rebuild.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.support.MockMvcRegressionSupport;
import com.merine.rebuild.system.menu.MenuService;
import com.merine.rebuild.system.menu.dto.MenuNode;
import com.merine.rebuild.system.menu.dto.MenuRequests;
import com.merine.rebuild.system.unit.UnitService;
import com.merine.rebuild.system.user.admin.UserAdminService;
import com.merine.rebuild.system.user.admin.dto.UserRequests;
import com.merine.rebuild.system.user.admin.dto.UserSummary;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

/**
 * 去掉物理外键后的引用完整性回归：巡检悬空引用，并验证「引用锁」真的顶替了外键父行锁。
 *
 * 并发场景都在真实 MySQL 上跑：测试自己在独立连接上拿住与生产代码同形的行锁，
 * 另一个线程调用真实用例，验证它先被挡住、放行后按业务语义失败，
 * 而不是像没有外键又没锁那样留下悬空行。
 */
class ReferenceIntegrityRegressionTest extends MockMvcRegressionSupport {

    private static final String UNIT_CODE = "REGR-REFLOCK-UNIT";
    private static final String LOGIN_NAME = "reflock.probe";
    private static final String RAW_PASSWORD = "reflock-secret-1";
    private static final String MENU_NAME = "回归引用锁目录";
    private static final String PERMISSION_CODE = "regr-reflock:probe";

    @Autowired
    private DataSource rawDataSource;
    @Autowired
    private UserAdminService userAdminService;
    @Autowired
    private UnitService unitService;
    @Autowired
    private MenuService menuService;

    private ExecutorService executor;

    @BeforeEach
    void openExecutor() {
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void closeExecutorAndDeleteFixtures() {
        executor.shutdownNow();
        deleteFixtures();
    }

    @Test
    void databaseHasNoDanglingReference() {
        danglingChecks().forEach((relation, sql) -> {
            Long dangling = jdbcTemplate.queryForObject(sql, Long.class);
            assertThat(dangling).as("悬空引用：%s", relation).isZero();
        });
    }

    /**
     * 删单位与建用户互斥：删除方持有全表排他锁时，新建用户必须先被挡住，
     * 删完之后必须以「单位不存在」失败，而不是插入一条指向已删单位的用户。
     */
    @Test
    void userWriteIsBlockedUntilUnitDeletionCommits() throws Exception {
        long unitId = insertUnitFixture();
        CountDownLatch writeStarted = new CountDownLatch(1);

        try (Connection holder = rawDataSource.getConnection()) {
            holder.setAutoCommit(false);
            // 与 UnitService.delete 同形：先拿全表排他锁，再校验、再删
            try (Statement statement = holder.createStatement()) {
                statement.executeQuery("SELECT id FROM sys_unit ORDER BY id FOR UPDATE").close();
            }

            Future<UserSummary> write = executor.submit(() -> {
                writeStarted.countDown();
                return userAdminService.create(new UserRequests.CreateUser(
                        LOGIN_NAME, "回归引用锁账号", UNIT_CODE, List.of("SYSTEM_ADMIN"),
                        RAW_PASSWORD));
            });
            assertThat(writeStarted.await(5, TimeUnit.SECONDS)).as("写用户的线程必须真的跑起来").isTrue();
            assertBlocked(write);

            try (Statement statement = holder.createStatement()) {
                statement.executeUpdate("DELETE FROM sys_unit WHERE id = " + unitId);
            }
            holder.commit();

            ApiException failure = failureOf(write, "UNKNOWN_UNIT");
            assertThat(failure.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        assertThat(countUsers()).as("失败的用户写入不能留下半条记录").isZero();
    }

    /**
     * 反向：用户写入持有单位引用锁时，删除单位必须先被挡住；
     * 放行后删除方要看到「锁之后」提交的用户，按 409 拒绝删除。
     */
    @Test
    void unitDeletionSeesUserCommittedBehindTheReferenceLock() throws Exception {
        insertUnitFixture();
        CountDownLatch deletionStarted = new CountDownLatch(1);

        try (Connection holder = rawDataSource.getConnection()) {
            holder.setAutoCommit(false);
            // 与 UserAdminService 写路径同形：先共享锁目标单位行，再写引用它的数据
            try (PreparedStatement lock = holder.prepareStatement(
                    "SELECT id FROM sys_unit WHERE unit_code = ? FOR SHARE")) {
                lock.setString(1, UNIT_CODE);
                lock.executeQuery().close();
            }
            try (PreparedStatement insert = holder.prepareStatement("""
                    INSERT INTO sys_user (login_name, display_name, password_hash, unit_id)
                    SELECT ?, ?, ?, id FROM sys_unit WHERE unit_code = ?
                    """)) {
                insert.setString(1, LOGIN_NAME);
                insert.setString(2, "回归引用锁账号");
                insert.setString(3, passwordEncoder.encode(RAW_PASSWORD));
                insert.setString(4, UNIT_CODE);
                insert.executeUpdate();
            }

            Future<Void> deletion = executor.submit(() -> {
                deletionStarted.countDown();
                unitService.delete(UNIT_CODE);
                return null;
            });
            assertThat(deletionStarted.await(5, TimeUnit.SECONDS)).as("删单位的线程必须真的跑起来").isTrue();
            assertBlocked(deletion);

            holder.commit();

            failureOf(deletion, "UNIT_HAS_USERS");
        }

        assertThat(countUsers()).as("删除被拒绝，用户必须还在").isEqualTo(1);
    }

    /**
     * 删菜单子树与新建子节点互斥：删除方锁住菜单树时，新建必须被挡住；
     * 父节点删掉后新建以「菜单节点不存在」失败，不会留下悬空节点。
     */
    @Test
    void menuWriteIsBlockedUntilSubtreeDeletionCommits() throws Exception {
        long directoryId = insertMenuFixture();
        CountDownLatch writeStarted = new CountDownLatch(1);

        try (Connection holder = rawDataSource.getConnection()) {
            holder.setAutoCommit(false);
            // 与 MenuService.delete 同形：先锁整棵菜单树，再删子树
            try (Statement statement = holder.createStatement()) {
                statement.executeQuery("SELECT id FROM sys_menu ORDER BY id FOR UPDATE").close();
            }

            Future<MenuNode> creation = executor.submit(() -> {
                writeStarted.countDown();
                // 父节点已经被锁住：能不能建成功完全取决于引用锁有没有生效
                return menuService.create(new MenuRequests.CreateMenu(
                        Long.toString(directoryId), "BUTTON", "回归引用锁按钮", null,
                        PERMISSION_CODE, null, 0));
            });
            assertThat(writeStarted.await(5, TimeUnit.SECONDS)).as("建菜单的线程必须真的跑起来").isTrue();
            assertBlocked(creation);

            try (Statement statement = holder.createStatement()) {
                statement.executeUpdate("DELETE FROM sys_menu WHERE id = " + directoryId);
            }
            holder.commit();

            failureOf(creation, "MENU_NOT_FOUND");
        }

        assertThat(countMenus()).as("失败的菜单写入不能留下悬空节点").isZero();
    }

    /** 引用锁被别的写事务持有时，写用例必须停在锁上，而不是先写后报错。 */
    private static void assertBlocked(Future<?> future) {
        assertThatThrownBy(() -> future.get(700, TimeUnit.MILLISECONDS))
                .as("引用锁被持有时写用例不能提前完成")
                .isInstanceOf(TimeoutException.class);
    }

    private static ApiException failureOf(Future<?> future, String expectedCode) throws Exception {
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> future.get(15, TimeUnit.SECONDS));
        assertThat(failure.getCause()).as("失败必须是可预期的业务错误").isInstanceOf(ApiException.class);
        ApiException error = (ApiException) failure.getCause();
        assertThat(error.code()).as("错误码说明是引用校验拦住的写入").isEqualTo(expectedCode);
        return error;
    }

    private long insertUnitFixture() {
        Long rootId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_unit WHERE unit_code = 'ORG_001'", Long.class);
        assertThat(rootId).as("迁移导入的一级单位 ORG_001 必须存在").isNotNull();

        jdbcTemplate.update("""
                INSERT INTO sys_unit (unit_code, unit_name, parent_id, unit_level, status)
                VALUES (?, ?, ?, 2, 'ENABLED')
                """, UNIT_CODE, "回归引用锁单位", rootId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_unit WHERE unit_code = ?", Long.class, UNIT_CODE);
    }

    private long insertMenuFixture() {
        Long parentId = jdbcTemplate.queryForObject("""
                SELECT id FROM sys_menu
                 WHERE menu_name = '系统管理' AND menu_type = 'DIRECTORY'
                """, Long.class);
        assertThat(parentId).as("引导菜单「系统管理」目录必须存在").isNotNull();

        jdbcTemplate.update("""
                INSERT INTO sys_menu (parent_id, menu_type, menu_name, sort_order, status, description)
                VALUES (?, 'DIRECTORY', ?, 999, 'ENABLED', '引用锁回归用合成目录')
                """, parentId, MENU_NAME);
        return jdbcTemplate.queryForObject("""
                SELECT id FROM sys_menu WHERE menu_name = ? AND menu_type = 'DIRECTORY'
                """, Long.class, MENU_NAME);
    }

    private long countUsers() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE login_name = ?", Long.class, LOGIN_NAME);
        return count == null ? 0 : count;
    }

    private long countMenus() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_menu WHERE menu_name = ?", Long.class, MENU_NAME);
        return count == null ? 0 : count;
    }

    private void deleteFixtures() {
        jdbcTemplate.update("""
                DELETE rp FROM sys_role_permission rp
                 JOIN sys_permission p ON p.id = rp.permission_id
                WHERE p.permission_code = ?
                """, PERMISSION_CODE);
        jdbcTemplate.update("""
                DELETE m FROM sys_menu m
                 JOIN sys_permission p ON p.id = m.permission_id
                WHERE p.permission_code = ?
                """, PERMISSION_CODE);
        jdbcTemplate.update("DELETE FROM sys_menu WHERE menu_name = ?", MENU_NAME);
        jdbcTemplate.update("DELETE FROM sys_permission WHERE permission_code = ?", PERMISSION_CODE);
        jdbcTemplate.update("""
                DELETE ur FROM sys_user_role ur
                 JOIN sys_user u ON u.id = ur.user_id
                WHERE u.login_name = ?
                """, LOGIN_NAME);
        jdbcTemplate.update("DELETE FROM sys_user WHERE login_name = ?", LOGIN_NAME);
        jdbcTemplate.update("DELETE FROM sys_unit WHERE unit_code = ?", UNIT_CODE);
    }

    /** 9 条关联各自查一遍悬空引用：外键没了，这是唯一能发现旁路写入的检查。 */
    private static Map<String, String> danglingChecks() {
        Map<String, String> checks = new LinkedHashMap<>();
        checks.put("sys_user.unit_id → sys_unit", """
                SELECT COUNT(*) FROM sys_user u
                 LEFT JOIN sys_unit un ON un.id = u.unit_id
                WHERE un.id IS NULL
                """);
        checks.put("sys_user_role.user_id → sys_user", """
                SELECT COUNT(*) FROM sys_user_role ur
                 LEFT JOIN sys_user u ON u.id = ur.user_id
                WHERE u.id IS NULL
                """);
        checks.put("sys_user_role.role_id → sys_role", """
                SELECT COUNT(*) FROM sys_user_role ur
                 LEFT JOIN sys_role r ON r.id = ur.role_id
                WHERE r.id IS NULL
                """);
        checks.put("sys_unit.parent_id → sys_unit", """
                SELECT COUNT(*) FROM sys_unit c
                 LEFT JOIN sys_unit p ON p.id = c.parent_id
                WHERE c.parent_id IS NOT NULL AND p.id IS NULL
                """);
        checks.put("sys_menu.parent_id → sys_menu", """
                SELECT COUNT(*) FROM sys_menu c
                 LEFT JOIN sys_menu p ON p.id = c.parent_id
                WHERE c.parent_id IS NOT NULL AND p.id IS NULL
                """);
        checks.put("sys_menu.permission_id → sys_permission", """
                SELECT COUNT(*) FROM sys_menu m
                 LEFT JOIN sys_permission p ON p.id = m.permission_id
                WHERE m.permission_id IS NOT NULL AND p.id IS NULL
                """);
        checks.put("sys_role_permission.role_id → sys_role", """
                SELECT COUNT(*) FROM sys_role_permission rp
                 LEFT JOIN sys_role r ON r.id = rp.role_id
                WHERE r.id IS NULL
                """);
        checks.put("sys_role_permission.permission_id → sys_permission", """
                SELECT COUNT(*) FROM sys_role_permission rp
                 LEFT JOIN sys_permission p ON p.id = rp.permission_id
                WHERE p.id IS NULL
                """);
        checks.put("sys_dict_item.dict_type_id → sys_dict_type", """
                SELECT COUNT(*) FROM sys_dict_item i
                 LEFT JOIN sys_dict_type t ON t.id = i.dict_type_id
                WHERE t.id IS NULL
                """);
        return checks;
    }
}
