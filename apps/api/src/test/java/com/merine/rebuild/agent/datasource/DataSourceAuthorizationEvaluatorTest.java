package com.merine.rebuild.agent.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import com.merine.rebuild.agent.datasource.DataSourceAuthorizationEvaluator.Decision;
import com.merine.rebuild.agent.datasource.DataSourceAuthorizationEvaluator.Privilege;
import com.merine.rebuild.agent.datasource.dto.DataSourceCheckOutcome;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 只读授权判定表的纯函数回归：不启动 Spring，专门盯住“什么算写权限 / 什么算可读”。 */
@DisplayName("数据源只读授权判定")
class DataSourceAuthorizationEvaluatorTest {
    private static final String DATABASE = "merine_rebuild_test";

    private static Decision evaluate(List<Privilege> privileges, List<String> selected) {
        return DataSourceAuthorizationEvaluator.evaluate(true, List.of(), privileges, DATABASE,
                selected, selected);
    }

    @Test
    @DisplayName("全局、库级、表级 SELECT 都能覆盖勾选视图")
    void selectAtAnyLevelCoversTheView() {
        assertThat(evaluate(List.of(new Privilege(null, null, null, "SELECT", false)),
                List.of("v_alpha")).outcome()).isEqualTo(DataSourceCheckOutcome.PASSED);
        assertThat(evaluate(List.of(new Privilege(DATABASE, null, null, "SELECT", false)),
                List.of("v_alpha")).outcome()).isEqualTo(DataSourceCheckOutcome.PASSED);
        assertThat(evaluate(List.of(new Privilege(DATABASE, "v_alpha", null, "SELECT", false)),
                List.of("v_alpha")).outcome()).isEqualTo(DataSourceCheckOutcome.PASSED);
    }

    @Test
    @DisplayName("USAGE 与 SHOW VIEW 不算写权限，其它权限或转授权限一律拒绝")
    void writePrivilegesAreRejected() {
        assertThat(evaluate(List.of(new Privilege(null, null, null, "USAGE", false),
                new Privilege(DATABASE, "v_alpha", null, "SELECT", false),
                new Privilege(DATABASE, "v_alpha", null, "SHOW VIEW", false)),
                List.of("v_alpha")).outcome()).isEqualTo(DataSourceCheckOutcome.PASSED);

        for (String type : List.of("INSERT", "UPDATE", "DELETE", "CREATE", "DROP", "ALTER",
                "EXECUTE", "PROCESS", "SYSTEM_USER", "CREATE TEMPORARY TABLES")) {
            assertThat(evaluate(List.of(new Privilege(DATABASE, "v_alpha", null, type, false)),
                    List.of("v_alpha")).outcome())
                    .as("权限 %s 必须拒绝", type)
                    .isEqualTo(DataSourceCheckOutcome.WRITE_PRIVILEGE);
        }
        assertThat(evaluate(List.of(new Privilege(null, null, null, "SELECT", true)),
                List.of("v_alpha")).outcome())
                .as("带 GRANT OPTION 的 SELECT 仍可继续转授写权限，不能通过")
                .isEqualTo(DataSourceCheckOutcome.WRITE_PRIVILEGE);
    }

    @Test
    @DisplayName("账号持有角色或元数据不完整时只能显示授权无法确认")
    void unknownAuthorizationNeverPasses() {
        Decision withRole = DataSourceAuthorizationEvaluator.evaluate(true,
                List.of("read_role@%"), List.of(), DATABASE, List.of(), List.of());
        assertThat(withRole.outcome()).isEqualTo(DataSourceCheckOutcome.AUTHORIZATION_UNCONFIRMED);

        Decision incomplete = DataSourceAuthorizationEvaluator.evaluate(false,
                List.of(), List.of(), DATABASE, List.of(), List.of());
        assertThat(incomplete.outcome()).isEqualTo(DataSourceCheckOutcome.AUTHORIZATION_UNCONFIRMED);
    }

    @Test
    @DisplayName("列级 SELECT 不等于整视图可读，同名视图在其它库的授权也不覆盖目标库")
    void selectedViewMustBeReadableInTheTargetDatabase() {
        List<Privilege> columnOnly = List.of(
                new Privilege(DATABASE, "v_alpha", "unit_code", "SELECT", false));
        assertThat(evaluate(columnOnly, List.of("v_alpha")).outcome())
                .isEqualTo(DataSourceCheckOutcome.VIEW_DENIED);

        List<Privilege> otherDatabase = List.of(
                new Privilege("other_database", "v_alpha", null, "SELECT", false));
        assertThat(evaluate(otherDatabase, List.of("v_alpha")).outcome())
                .isEqualTo(DataSourceCheckOutcome.VIEW_DENIED);
    }

    @Test
    @DisplayName("没有勾选或其他可读视图时仍可通过只读检查，但说明需要勾选")
    void readonlyWithoutViewsPassesWithExplanation() {
        Decision decision = evaluate(List.of(), List.of());
        assertThat(decision.outcome()).isEqualTo(DataSourceCheckOutcome.PASSED);
        assertThat(decision.message()).contains("没有可查询的视图");

        Decision toPick = DataSourceAuthorizationEvaluator.evaluate(true, List.of(),
                List.of(), DATABASE, List.of("v_alpha"), List.of());
        assertThat(toPick.outcome()).isEqualTo(DataSourceCheckOutcome.PASSED);
        assertThat(toPick.message()).contains("勾选");
    }
}
