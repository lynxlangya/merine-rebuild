package com.merine.rebuild.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.merine.rebuild.support.MockMvcRegressionSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 「无物理外键」强规则的结构断言（规则：AGENTS.md「不可省略的约束」、docs/rules/database.md 第 5 节）。
 *
 * 两道口子一起守：
 *  * 实际库结构——迁移执行完仍存在外键即失败；
 *  * 迁移文本——只有 V2/V7/V12/V13 这四个历史文件允许保留外键定义（统一由 V17 移除），
 *    其余文件出现外键即失败。
 * 引用完整性本身由写路径的引用锁与删除前校验维护，行为回归见
 * {@link ReferenceIntegrityRegressionTest}。
 */
class SchemaForeignKeyRegressionTest extends MockMvcRegressionSupport {

    /**
     * 迁移文本从源码目录读，不走 classpath：
     * 构建产物里可能留着改名前的旧文件（例如并存的 V12 旧名），
     * 拿它做规则断言会得出与仓库内容不符的结论。
     */
    private static final Path MIGRATION_DIR =
            Path.of("src", "main", "resources", "db", "migration");

    /** 规则生效前写下的历史迁移：保持不可变，外键统一由 V17 移除。 */
    private static final Set<String> LEGACY_FOREIGN_KEY_MIGRATIONS = Set.of(
            "V2__create_system_tables.sql",
            "V7__extend_unit_hierarchy.sql",
            "V12__create_permission_and_menu_tables.sql",
            "V13__create_dictionary_tables.sql");

    /** 外键移除后必须原样保留的关联列索引与唯一约束：删约束不能顺手带走索引。 */
    private static final Map<String, List<String>> REQUIRED_REFERENCE_INDEXES = Map.of(
            "sys_unit", List.of("idx_sys_unit_parent"),
            "sys_user", List.of("idx_sys_user_unit"),
            "sys_user_role", List.of("uk_sys_user_role", "idx_sys_user_role_role"),
            "sys_menu", List.of("idx_sys_menu_parent", "idx_sys_menu_permission"),
            "sys_role_permission",
            List.of("uk_sys_role_permission", "idx_sys_role_permission_permission"),
            "sys_dict_item", List.of("uk_sys_dict_item_value", "idx_sys_dict_item_type"));

    @Test
    void databaseHasNoForeignKey() {
        List<Map<String, Object>> foreignKeys = jdbcTemplate.queryForList("""
                SELECT TABLE_NAME, CONSTRAINT_NAME
                  FROM information_schema.TABLE_CONSTRAINTS
                 WHERE CONSTRAINT_SCHEMA = DATABASE()
                   AND CONSTRAINT_TYPE = 'FOREIGN KEY'
                 ORDER BY TABLE_NAME, CONSTRAINT_NAME
                """);

        assertThat(foreignKeys)
                .as("表结构禁止物理外键与级联（见 docs/rules/database.md 第 5 节）")
                .isEmpty();
    }

    @Test
    void referenceIndexesSurviveForeignKeyRemoval() {
        Set<String> existing = jdbcTemplate.queryForList("""
                        SELECT DISTINCT TABLE_NAME, INDEX_NAME
                          FROM information_schema.STATISTICS
                         WHERE TABLE_SCHEMA = DATABASE()
                        """).stream()
                .map(row -> row.get("TABLE_NAME") + "." + row.get("INDEX_NAME"))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> missing = new ArrayList<>();
        REQUIRED_REFERENCE_INDEXES.forEach((table, indexes) -> indexes.forEach(index -> {
            if (!existing.contains(table + "." + index)) {
                missing.add(table + "." + index);
            }
        }));

        assertThat(missing).as("关联列索引与外键无关，删外键后必须仍在").isEmpty();
    }

    @Test
    void migrationsDoNotDefineForeignKey() throws IOException {
        assertThat(Files.isDirectory(MIGRATION_DIR))
                .as("测试工作目录应当是 apps/api，能在 %s 下扫到迁移", MIGRATION_DIR)
                .isTrue();
        List<Path> migrations;
        try (Stream<Path> files = Files.list(MIGRATION_DIR)) {
            migrations = files
                    .filter(file -> file.getFileName().toString().endsWith(".sql"))
                    .sorted()
                    .toList();
        }
        assertThat(migrations).as("源码目录里必须有迁移文件").isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Path migration : migrations) {
            String fileName = migration.getFileName().toString();
            if (LEGACY_FOREIGN_KEY_MIGRATIONS.contains(fileName)) {
                continue;
            }
            for (String line : Files.readAllLines(migration, StandardCharsets.UTF_8)) {
                String upper = line.toUpperCase(Locale.ROOT);
                if (upper.contains("FOREIGN KEY") && !upper.contains("DROP FOREIGN KEY")) {
                    violations.add(fileName + " → " + line.strip());
                }
            }
        }

        assertThat(violations)
                .as("新迁移不得定义外键；历史定义只能由 V17 移除")
                .isEmpty();
    }

    @Test
    void foreignKeyRemovalIsRecordedInV17() throws IOException {
        Path migration = MIGRATION_DIR.resolve("V17__drop_all_foreign_keys.sql");
        assertThat(Files.isRegularFile(migration)).as("外键移除点固定为 V17").isTrue();

        String text = Files.readString(migration, StandardCharsets.UTF_8).toUpperCase(Locale.ROOT);
        assertThat(text).as("V17 必须真的删掉外键").contains("DROP FOREIGN KEY");
        assertThat(text)
                .as("V17 只做移除，不定义新外键")
                .doesNotContain("REFERENCES")
                .doesNotContain("ADD CONSTRAINT");
    }
}
