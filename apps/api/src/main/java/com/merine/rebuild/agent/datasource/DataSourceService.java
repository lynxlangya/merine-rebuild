package com.merine.rebuild.agent.datasource;

import com.merine.rebuild.agent.AgentSecretCipher;
import com.merine.rebuild.agent.datasource.dto.DataSourceCheckOutcome;
import com.merine.rebuild.agent.datasource.dto.DataSourceCheckResult;
import com.merine.rebuild.agent.datasource.dto.DataSourceRequests.CheckDraft;
import com.merine.rebuild.agent.datasource.dto.DataSourceRequests.Save;
import com.merine.rebuild.agent.datasource.dto.DataSourceRequests.State;
import com.merine.rebuild.agent.datasource.dto.DataSourceRequests.StatusChange;
import com.merine.rebuild.agent.datasource.dto.DataSourceView;
import com.merine.rebuild.agent.datasource.persistence.DataSourceMapper;
import com.merine.rebuild.agent.datasource.persistence.DataSourceRow;
import com.merine.rebuild.agent.datasource.persistence.DataSourceSecretRow;
import com.merine.rebuild.agent.datasource.persistence.DataSourceViewRow;
import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.common.PageResult;
import com.merine.rebuild.system.audit.AuditEvent;
import com.merine.rebuild.system.audit.AuditTrail;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据源用例编排：读、校验、检查与审计。耗时可能数秒的网络检查不进事务，
 * 事务写由 {@link DataSourceStore} 单独承担；这样健康路径与停用配置都不会持有长期连接。
 *
 * <p>检查通过只代表当时状态：启用动作会重新检查，配置在检查期间变化则报冲突，
 * 不会用旧结论启用新配置。
 */
@Service
public class DataSourceService {
    private final DataSourceMapper mapper;
    private final DataSourceStore store;
    private final DataSourceChecker checker;
    private final DataSourceProperties properties;
    private final AgentSecretCipher cipher;
    private final AuditTrail audit;

    public DataSourceService(DataSourceMapper mapper, DataSourceStore store, DataSourceChecker checker,
                             DataSourceProperties properties, AgentSecretCipher cipher,
                             AuditTrail audit) {
        this.mapper = mapper;
        this.store = store;
        this.checker = checker;
        this.properties = properties;
        this.cipher = cipher;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResult<DataSourceView> list(String search, int page, int pageSize) {
        String keyword = search.trim();
        List<DataSourceRow> rows = mapper.list(keyword, pageSize, (long) (page - 1) * pageSize);
        Map<String, List<String>> views = viewsByDataSource(rows);
        return new PageResult<>(
                rows.stream()
                        .map(row -> DataSourceViews.of(row, views.getOrDefault(row.id(), List.of())))
                        .toList(),
                mapper.count(keyword), page, pageSize);
    }

    @Transactional(readOnly = true)
    public DataSourceView find(String id) {
        DataSourceRow row = mapper.find(id);
        if (row == null) throw DataSourceErrors.notFound();
        return DataSourceViews.of(row, DataSourceViews.names(mapper.viewsOf(id)));
    }

    public DataSourceView create(Save raw) {
        return store.create(DataSourceInputs.normalizeSave(raw, properties));
    }

    public DataSourceView update(String id, Save raw) {
        return store.update(id, DataSourceInputs.normalizeSave(raw, properties));
    }

    /** 启用：不复用旧检查结果，先在事务外重新检查，再在短事务里落状态与结论。 */
    public DataSourceView setStatus(String id, StatusChange input) {
        if (input.status() == State.DISABLED) {
            return store.disable(id, input.version());
        }
        DataSourceSecretRow current = requireSecret(id);
        if (current.version() != input.version()) throw DataSourceErrors.conflict();
        if (current.status() == State.ENABLED) return find(id);
        List<String> views = DataSourceViews.names(mapper.viewsOf(id));
        if (views.isEmpty()) throw DataSourceErrors.noViews();
        DataSourceCheckResult check = check(current, views);
        if (check.outcome() != DataSourceCheckOutcome.PASSED) {
            store.recordCheck(id, current.version(), check.outcome(), check.message(), check.checkedAt());
            audit.recordCurrent(AuditEvent.failed("agent", "datasource:enable", "DATASOURCE", id,
                    current.name(), "启用校验未通过：" + outcomeLabel(check.outcome())));
            throw DataSourceErrors.checkFailed(check.message());
        }
        return store.enableChecked(id, input.version(), check.message(), check.checkedAt());
    }

    /** 对已保存配置做一次检查并记录结果；检查期间配置变化则报冲突，不写陈旧结论。 */
    public DataSourceCheckResult checkSaved(String id) {
        DataSourceSecretRow current = requireSecret(id);
        List<String> views = DataSourceViews.names(mapper.viewsOf(id));
        DataSourceCheckResult result = check(current, views).withSourceVersion(current.version());
        store.recordCheck(id, current.version(), result.outcome(), result.message(), result.checkedAt());
        auditCheck(id, current.name(), result);
        return result;
    }

    /**
     * 未保存配置的连接测试：不落库；带 id 且目标与身份完全一致、密码留空时复用已保存密码，
     * 防止把保存的凭据用于另一台服务器或另一个账号。
     */
    public DataSourceCheckResult checkDraft(CheckDraft raw) {
        CheckDraft input = DataSourceInputs.normalizeCheck(raw, properties);
        String password = input.password();
        if (password == null && input.id() != null) {
            DataSourceSecretRow saved = requireSecret(input.id());
            if (!sameTarget(saved, input)) {
                throw DataSourceInputs.invalid("password", "连接目标或账号已修改，请重新输入数据库密码");
            }
            password = decrypt(saved);
        }
        if (password == null) throw DataSourceInputs.invalid("password", "请填写数据库密码");
        DataSourceCheckResult result = checker.check(input.host(), input.port(), input.databaseName(),
                input.username(), password, input.views());
        audit.recordCurrent(result.outcome() == DataSourceCheckOutcome.PASSED
                ? AuditEvent.succeeded("agent", "datasource:check", "DATASOURCE",
                        input.id() == null ? "" : input.id(), "",
                        "测试未保存的数据源配置：连接与只读授权检查通过")
                : AuditEvent.failed("agent", "datasource:check", "DATASOURCE",
                        input.id() == null ? "" : input.id(), "",
                        "测试未保存的数据源配置：检查未通过（" + outcomeLabel(result.outcome()) + "）"));
        return result;
    }

    private DataSourceCheckResult check(DataSourceSecretRow current, List<String> views) {
        return checker.check(current.host(), current.port(), current.databaseName(),
                current.username(), decrypt(current), views);
    }

    /** 草稿检查复用保存密码的前置条件：连接目标与数据库身份完全一致。 */
    private static boolean sameTarget(DataSourceSecretRow saved, CheckDraft input) {
        return saved.dbType() == input.dbType()
                && saved.host().equals(input.host())
                && saved.port() == input.port()
                && saved.databaseName().equals(input.databaseName())
                && saved.username().equals(input.username());
    }

    private DataSourceSecretRow requireSecret(String id) {
        DataSourceSecretRow row = mapper.findSecret(id);
        if (row == null) throw DataSourceErrors.notFound();
        return row;
    }

    private String decrypt(DataSourceSecretRow row) {
        try {
            return cipher.decrypt(DataSourceStore.secretAad(row.id()), row.passwordCipher());
        } catch (ApiException error) {
            throw DataSourceErrors.secretUnavailable();
        }
    }

    /** 列表页一次批量补装视图，避免逐条查询；空列表不发起 IN 查询。 */
    private Map<String, List<String>> viewsByDataSource(List<DataSourceRow> rows) {
        if (rows.isEmpty()) return Map.of();
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (DataSourceViewRow row : mapper.viewsOfMany(rows.stream().map(DataSourceRow::id).toList())) {
            grouped.computeIfAbsent(row.dataSourceId(), key -> new ArrayList<>()).add(row.viewName());
        }
        return grouped;
    }

    private void auditCheck(String id, String name, DataSourceCheckResult result) {
        String summary = "检查数据源「%s」：%s".formatted(name,
                result.outcome() == DataSourceCheckOutcome.PASSED
                        ? "连接与只读授权检查通过"
                        : "检查未通过（" + outcomeLabel(result.outcome()) + "）");
        audit.recordCurrent(result.outcome() == DataSourceCheckOutcome.PASSED
                ? AuditEvent.succeeded("agent", "datasource:check", "DATASOURCE", id, name, summary)
                : AuditEvent.failed("agent", "datasource:check", "DATASOURCE", id, name, summary));
    }

    private static String outcomeLabel(DataSourceCheckOutcome outcome) {
        return switch (outcome) {
            case PASSED -> "通过";
            case CONNECTION_FAILED -> "连接失败";
            case WRITE_PRIVILEGE -> "存在写权限";
            case AUTHORIZATION_UNCONFIRMED -> "授权无法确认";
            case VIEW_DENIED -> "勾选视图未授权";
        };
    }
}
