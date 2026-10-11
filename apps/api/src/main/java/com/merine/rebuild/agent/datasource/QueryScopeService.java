package com.merine.rebuild.agent.datasource;

import com.merine.rebuild.agent.AgentSecretCipher;
import com.merine.rebuild.agent.datasource.dto.DataSourceCheckOutcome;
import com.merine.rebuild.agent.datasource.dto.DataSourceCheckResult;
import com.merine.rebuild.agent.datasource.dto.DataSourceView;
import com.merine.rebuild.agent.datasource.dto.QueryScopeDetail;
import com.merine.rebuild.agent.datasource.dto.QueryScopeRequests.ColumnDescription;
import com.merine.rebuild.agent.datasource.dto.QueryScopeRequests.ReplaceViews;
import com.merine.rebuild.agent.datasource.dto.QueryScopeRequests.SaveMetadata;
import com.merine.rebuild.agent.datasource.dto.ScopeColumnMeta;
import com.merine.rebuild.agent.datasource.dto.ScopeViewMeta;
import com.merine.rebuild.agent.datasource.persistence.DataSourceMapper;
import com.merine.rebuild.agent.datasource.persistence.DataSourceRow;
import com.merine.rebuild.agent.datasource.persistence.DataSourceSecretRow;
import com.merine.rebuild.agent.datasource.persistence.ViewColumnMetaRow;
import com.merine.rebuild.agent.datasource.persistence.ViewMetaRow;
import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.common.PageResult;
import com.merine.rebuild.system.audit.AuditEvent;
import com.merine.rebuild.system.audit.AuditTrail;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 查询范围用例：维护执行白名单与视图/字段说明覆盖值。
 *
 * <p>范围与说明分开保存：范围变化清除检查结果并停用数据源；说明变化只递增语义版本。
 * 网络核对（新增视图的 SELECT 授权、字段是否存在）在事务外完成，复用
 * {@link DataSourceChecker} 的目标范围校验、并发名额与超时限制；事务写由
 * {@link DataSourceStore} 用父行锁与两类版本条件保证并发安全。
 */
@Service
public class QueryScopeService {
    /** 保存时说明覆盖值序列化上限；超出明确拒绝，不静默截断口径。 */
    static final int MAX_META_BYTES = 32 * 1024;
    /** 服务间桥下发目录上限：超过时保留上一份可用配置由管理员缩短说明。 */
    public static final int MAX_BRIDGE_BYTES = 64 * 1024;
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    private final DataSourceMapper mapper;
    private final DataSourceStore store;
    private final DataSourceService datasources;
    private final DataSourceChecker checker;
    private final DataSourceProperties properties;
    private final AgentSecretCipher cipher;
    private final AuditTrail audit;
    private final ObjectMapper json;

    public QueryScopeService(DataSourceMapper mapper, DataSourceStore store, DataSourceService datasources,
                             DataSourceChecker checker, DataSourceProperties properties,
                             AgentSecretCipher cipher, AuditTrail audit, ObjectMapper json) {
        this.mapper = mapper;
        this.store = store;
        this.datasources = datasources;
        this.checker = checker;
        this.properties = properties;
        this.cipher = cipher;
        this.audit = audit;
        this.json = json;
    }

    /** 列表只读本项目配置库：不连接目标库，不含密码。 */
    public PageResult<DataSourceView> list(String search, int page, int pageSize) {
        return datasources.list(search, page, pageSize);
    }

    @Transactional(readOnly = true)
    public QueryScopeDetail find(String id) {
        DataSourceRow row = mapper.find(id);
        if (row == null) throw DataSourceErrors.notFound();
        return detail(row);
    }

    /** 显式刷新：探测该数据源可 SELECT 的视图；带 view 时同时读取该视图字段元数据。 */
    public DataSourceCheckResult inspect(String id, String view) {
        DataSourceSecretRow current = requireSecret(id);
        String describe = view == null || view.isBlank() ? null : view.trim();
        if (describe != null && !IDENTIFIER.matcher(describe).matches()) {
            throw DataSourceInputs.invalid("view", "视图名不合法");
        }
        List<String> views = DataSourceViews.names(mapper.viewsOf(id));
        DataSourceCheckResult result = checker.check(current.host(), current.port(), current.databaseName(),
                current.username(), decrypt(current), views, describe);
        audit.recordCurrent(result.outcome() == DataSourceCheckOutcome.PASSED
                ? AuditEvent.succeeded("agent", "scope:inspect", "DATASOURCE", id, current.name(),
                        describe == null
                                ? "探测数据源「%s」可读视图".formatted(current.name())
                                : "探测数据源「%s」视图「%s」的可读性与字段".formatted(current.name(), describe))
                : AuditEvent.failed("agent", "scope:inspect", "DATASOURCE", id, current.name(),
                        "探测数据源「%s」未完成：%s".formatted(current.name(), result.message())));
        return result;
    }

    /** 白名单整体替换：新增候选必须用已保存连接重新确认可读，纯移除不访问目标库。 */
    public DataSourceView replaceViews(String id, ReplaceViews input, boolean canProbe) {
        List<String> views = DataSourceInputs.normalizeViews(input.views(), properties);
        DataSourceSecretRow current = requireSecret(id);
        if (input.version() != current.version()) throw DataSourceErrors.conflict();
        List<String> old = DataSourceViews.names(mapper.viewsOf(id));
        boolean adding = views.stream().anyMatch(view -> !old.contains(view));
        if (adding) {
            if (!canProbe) throw DataSourceErrors.probePermissionRequired();
            DataSourceCheckResult check = checker.check(current.host(), current.port(),
                    current.databaseName(), current.username(), decrypt(current), views);
            if (check.outcome() != DataSourceCheckOutcome.PASSED) {
                audit.recordCurrent(AuditEvent.failed("agent", "scope:update-views", "DATASOURCE", id,
                        current.name(), "更新数据源「%s」范围未通过复检：%s"
                                .formatted(current.name(), check.message())));
                throw DataSourceErrors.checkFailed(check.message());
            }
        }
        return store.replaceViews(id, input.version(), views);
    }

    /**
     * 指定视图的说明覆盖段整体替换。提交字段集合里出现尚无覆盖值的新字段时需要实时核对目标库，
     * 因此除 {@code agent:scope:update} 外还需要 {@code agent:datasource:test}；
     * 只编辑/清空已有覆盖值时不需要网络权限。
     */
    public QueryScopeDetail saveMetadata(String id, SaveMetadata input, boolean canProbe) {
        String view = input.view().trim();
        if (!IDENTIFIER.matcher(view).matches()) throw DataSourceInputs.invalid("view", "视图名不合法");
        String displayName = text(input.displayName(), 80);
        String description = text(input.description(), 2000);
        List<ViewColumnMetaRow> columns = normalizeColumns(id, view, input.columns());
        DataSourceRow row = mapper.find(id);
        if (row == null) throw DataSourceErrors.notFound();
        List<String> whitelist = DataSourceViews.names(mapper.viewsOf(id));
        List<ViewMetaRow> existingMeta = mapper.viewMetaOf(id);
        boolean tracked = whitelist.contains(view)
                || existingMeta.stream().anyMatch(meta -> meta.viewName().equals(view));
        if (!tracked) throw DataSourceErrors.scopeViewUntracked(view);
        List<ViewColumnMetaRow> existingColumns = mapper.columnMetaOfSource(id).stream()
                .filter(item -> item.viewName().equals(view))
                .toList();
        Set<String> known = new LinkedHashSet<>();
        existingColumns.forEach(item -> known.add(item.columnName()));
        boolean needsProbe = columns.stream().anyMatch(item -> !known.contains(item.columnName()));
        if (needsProbe) {
            if (!canProbe) throw DataSourceErrors.probePermissionRequired();
            DataSourceSecretRow current = requireSecret(id);
            DataSourceCheckResult check = checker.check(current.host(), current.port(),
                    current.databaseName(), current.username(), decrypt(current), whitelist, view);
            if (check.outcome() != DataSourceCheckOutcome.PASSED) {
                audit.recordCurrent(AuditEvent.failed("agent", "scope:update-metadata", "DATASOURCE", id,
                        row.name(), "更新数据源「%s」视图「%s」说明未通过字段核对：%s"
                                .formatted(row.name(), view, check.message())));
                throw DataSourceErrors.checkFailed(check.message());
            }
            Set<String> live = new LinkedHashSet<>();
            check.viewColumns().forEach(column -> live.add(column.name()));
            for (ViewColumnMetaRow column : columns) {
                if (!live.contains(column.columnName()) && !known.contains(column.columnName())) {
                    throw DataSourceInputs.invalid("columns", "字段不存在或不可读：" + column.columnName());
                }
            }
        }
        List<ScopeViewMeta> merged = mergedMeta(existingMeta, mapper.columnMetaOfSource(id), view,
                displayName, description, columns);
        if (json.writeValueAsBytes(merged).length > MAX_META_BYTES) {
            throw DataSourceErrors.scopeMetadataTooLarge();
        }
        SaveMetadata normalized = new SaveMetadata(input.version(), input.semanticVersion(), view,
                displayName, description, input.columns());
        return store.saveViewMetadata(id, normalized, columns);
    }

    /** 说明覆盖值按视图合并：只保留有内容的视图/字段，空串表示取消覆盖。 */
    private static List<ScopeViewMeta> mergedMeta(List<ViewMetaRow> views, List<ViewColumnMetaRow> columns,
                                                  String view, String displayName, String description,
                                                  List<ViewColumnMetaRow> newColumns) {
        Map<String, List<ScopeColumnMeta>> byView = new LinkedHashMap<>();
        for (ViewColumnMetaRow row : columns) {
            if (row.viewName().equals(view)) continue;
            byView.computeIfAbsent(row.viewName(), key -> new ArrayList<>())
                    .add(new ScopeColumnMeta(row.columnName(), row.description()));
        }
        List<ScopeColumnMeta> viewColumns = new ArrayList<>();
        for (ViewColumnMetaRow row : newColumns) {
            viewColumns.add(new ScopeColumnMeta(row.columnName(), row.description()));
        }
        if (!viewColumns.isEmpty()) byView.put(view, viewColumns);
        Map<String, ScopeViewMeta> result = new LinkedHashMap<>();
        for (ViewMetaRow row : views) {
            if (row.viewName().equals(view)) continue;
            List<ScopeColumnMeta> metaColumns = byView.remove(row.viewName());
            if (row.displayName().isEmpty() && row.description().isEmpty()
                    && (metaColumns == null || metaColumns.isEmpty())) {
                continue;
            }
            result.put(row.viewName(), new ScopeViewMeta(row.viewName(), row.displayName(),
                    row.description(), metaColumns == null ? List.of() : metaColumns));
        }
        for (Map.Entry<String, List<ScopeColumnMeta>> entry : byView.entrySet()) {
            result.put(entry.getKey(), new ScopeViewMeta(entry.getKey(), "", "", entry.getValue()));
        }
        if (!displayName.isEmpty() || !description.isEmpty() || !viewColumns.isEmpty()) {
            result.put(view, new ScopeViewMeta(view, displayName, description, viewColumns));
        }
        return List.copyOf(result.values());
    }

    private static List<ViewColumnMetaRow> normalizeColumns(String id, String view,
                                                            List<ColumnDescription> raw) {
        if (raw == null) return List.of();
        Set<String> seen = new LinkedHashSet<>();
        List<ViewColumnMetaRow> columns = new ArrayList<>();
        for (ColumnDescription item : raw) {
            String name = item.name() == null ? "" : item.name().trim();
            if (!IDENTIFIER.matcher(name).matches()) {
                throw DataSourceInputs.invalid("columns", "字段名不合法：" + name);
            }
            if (!seen.add(name)) throw DataSourceInputs.invalid("columns", "字段不能重复：" + name);
            String description = text(item.description(), 300);
            if (!description.isEmpty()) columns.add(new ViewColumnMetaRow(id, view, name, description));
        }
        return List.copyOf(columns);
    }

    private QueryScopeDetail detail(DataSourceRow row) {
        return QueryScopeDetail.of(
                DataSourceViews.of(row, DataSourceViews.names(mapper.viewsOf(row.id()))),
                DataSourceViews.scopeMeta(mapper.viewMetaOf(row.id()), mapper.columnMetaOfSource(row.id())));
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

    private static String text(String raw, int max) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() > max) throw DataSourceInputs.invalid("text", "内容长度不能超过 " + max + " 个字符");
        return value;
    }
}
