package com.merine.rebuild.agent.datasource;

import com.merine.rebuild.agent.AgentSecretCipher;
import com.merine.rebuild.agent.datasource.dto.DataSourceCheckOutcome;
import com.merine.rebuild.agent.datasource.dto.DataSourceRequests.Save;
import com.merine.rebuild.agent.datasource.dto.DataSourceRequests.State;
import com.merine.rebuild.agent.datasource.dto.DataSourceView;
import com.merine.rebuild.agent.datasource.dto.QueryScopeDetail;
import com.merine.rebuild.agent.datasource.dto.QueryScopeRequests.SaveMetadata;
import com.merine.rebuild.agent.datasource.dto.ScopeViewMeta;
import com.merine.rebuild.agent.datasource.persistence.DataSourceMapper;
import com.merine.rebuild.agent.datasource.persistence.DataSourceRow;
import com.merine.rebuild.agent.datasource.persistence.ViewColumnMetaRow;
import com.merine.rebuild.agent.datasource.persistence.ViewMetaRow;
import com.merine.rebuild.system.audit.AuditEvent;
import com.merine.rebuild.system.audit.AuditTrail;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据源的事务写组件。与网络检查分离的原因是：检查可能耗时数秒，
 * 不能放在持有数据库锁的事务里；这里只做短事务的持久化与审计。
 */
@Component
public class DataSourceStore {
    private static final Logger log = LoggerFactory.getLogger(DataSourceStore.class);

    /** 服务间桥读取的一致快照：连接配置、白名单、说明与两类版本要么都有效，要么拒绝。 */
    public record DataSourceScopeSnapshot(DataSourceRow source, List<String> views,
                                          List<ScopeViewMeta> meta) {
    }

    private final DataSourceMapper mapper;
    private final AgentSecretCipher cipher;
    private final AuditTrail audit;

    DataSourceStore(DataSourceMapper mapper, AgentSecretCipher cipher, AuditTrail audit) {
        this.mapper = mapper;
        this.cipher = cipher;
        this.audit = audit;
    }

    /** 密码密文的认证附加数据：与用途和记录绑定，换记录或换表都无法解密。 */
    static String secretAad(String id) {
        return "datasource:" + id;
    }

    @Transactional
    public DataSourceView create(Save input) {
        String id = UUID.randomUUID().toString();
        String encrypted = cipher.encrypt(secretAad(id),
                DataSourceInputs.requirePassword(input.password()));
        try {
            mapper.insert(id, input, encrypted);
        } catch (DuplicateKeyException error) {
            throw DataSourceErrors.nameExists();
        }
        log.info("DataSource created: id={}", id);
        DataSourceView created = DataSourceViews.of(mapper.find(id), List.of());
        audit.recordCurrent(AuditEvent.succeeded("agent", "datasource:create", "DATASOURCE", id,
                created.name(), "新建数据源「%s」（%s:%d/%s，默认停用）".formatted(
                        created.name(), created.host(), created.port(), created.databaseName())));
        return created;
    }

    /**
     * 编辑：数据目标/身份或密码变化时清除旧检查结果并强制停用；
     * 数据目标或身份变化还会清空允许视图与说明（新库同名视图可能不是同一个对象），
     * 仅轮换密码保留范围与说明，只改名称/备注保留状态与检查结论。
     * 锁行 + 版本条件保证并发编辑只有一个生效。
     */
    @Transactional
    public DataSourceView update(String id, Save input) {
        DataSourceRow current = lockOrThrow(id);
        if (input.version() == null || input.version() != current.version()) {
            throw DataSourceErrors.conflict();
        }
        boolean targetChanged = targetChanged(current, input);
        boolean connectionChanged = targetChanged || input.password() != null;
        String encrypted = input.password() == null ? null
                : cipher.encrypt(secretAad(id), input.password());
        int updated = connectionChanged
                ? mapper.replaceConnection(id, input, encrypted, current.version())
                : mapper.updateMetadata(id, input, current.version());
        if (updated != 1) throw DataSourceErrors.conflict();
        if (targetChanged) {
            mapper.deleteViews(id);
            mapper.deleteViewMeta(id);
            mapper.deleteColumnMetaBySource(id);
        }
        log.info("DataSource updated: id={} targetChanged={} connectionChanged={}", id, targetChanged,
                connectionChanged);
        DataSourceView view = DataSourceViews.of(mapper.find(id),
                DataSourceViews.names(mapper.viewsOf(id)));
        audit.recordCurrent(AuditEvent.succeeded("agent", "datasource:update", "DATASOURCE", id,
                view.name(), "修改数据源「%s」：%s".formatted(view.name(), targetChanged
                        ? "数据目标或身份变化，检查结果、允许视图与说明已清除并停用"
                        : connectionChanged
                                ? "连接参数变化，旧检查结果已清除并停用；范围与说明保留"
                                : "仅修改名称或备注")));
        return view;
    }

    /** 记录一次检查结论；检查期间配置已变化则不落旧结果，由调用方报冲突。 */
    public void recordCheck(String id, int version, DataSourceCheckOutcome outcome, String message,
                            Instant checkedAt) {
        if (mapper.recordCheck(id, version, outcome, message, checkedAt) != 1) {
            throw DataSourceErrors.checkConflict();
        }
    }

    /** 启用：同一事务里锁行、确认版本与视图，并写入刚完成的检查结论，不复用页面上的旧结果。 */
    @Transactional
    public DataSourceView enableChecked(String id, int version, String message, Instant checkedAt) {
        DataSourceRow current = lockOrThrow(id);
        if (current.version() != version) throw DataSourceErrors.conflict();
        if (current.status() == State.ENABLED) {
            return DataSourceViews.of(current, DataSourceViews.names(mapper.viewsOf(id)));
        }
        if (mapper.viewsOf(id).isEmpty()) throw DataSourceErrors.noViews();
        if (mapper.enableChecked(id, version, message, checkedAt) != 1) {
            throw DataSourceErrors.conflict();
        }
        log.info("DataSource enabled: id={}", id);
        DataSourceView enabled = DataSourceViews.of(mapper.find(id),
                DataSourceViews.names(mapper.viewsOf(id)));
        audit.recordCurrent(AuditEvent.succeeded("agent", "datasource:enable", "DATASOURCE", id,
                enabled.name(), "启用数据源「%s」（%s:%d/%s，%d 个视图）".formatted(enabled.name(),
                        enabled.host(), enabled.port(), enabled.databaseName(), enabled.views().size())));
        return enabled;
    }

    @Transactional
    public DataSourceView disable(String id, int version) {
        DataSourceRow current = lockOrThrow(id);
        if (current.version() != version) throw DataSourceErrors.conflict();
        if (current.status() == State.DISABLED) {
            return DataSourceViews.of(current, DataSourceViews.names(mapper.viewsOf(id)));
        }
        if (mapper.disable(id, version) != 1) throw DataSourceErrors.conflict();
        log.info("DataSource disabled: id={}", id);
        DataSourceView disabled = DataSourceViews.of(mapper.find(id),
                DataSourceViews.names(mapper.viewsOf(id)));
        audit.recordCurrent(AuditEvent.succeeded("agent", "datasource:disable", "DATASOURCE", id,
                disabled.name(), "停用数据源「%s」".formatted(disabled.name())));
        return disabled;
    }

    private DataSourceRow lockOrThrow(String id) {
        DataSourceRow current = mapper.lock(id);
        if (current == null) throw DataSourceErrors.notFound();
        return current;
    }

    /**
     * 服务间桥的一致快照：网络授权复检在事务外完成后调用，这里只锁父行做短事务读取；
     * 连接版本或语义版本在检查期间变化即报冲突，调用方重新描述，不返回新旧混合的目录。
     */
    @Transactional
    public DataSourceScopeSnapshot snapshot(String id, int expectedVersion, int expectedSemanticVersion) {
        DataSourceRow row = lockOrThrow(id);
        if (row.version() != expectedVersion || row.semanticVersion() != expectedSemanticVersion
                || row.status() != State.ENABLED) {
            throw DataSourceErrors.conflict();
        }
        List<String> views = DataSourceViews.names(mapper.viewsOf(id));
        if (views.isEmpty()) throw DataSourceErrors.noViews();
        // 只下发当前白名单内的说明；已移出范围的旧覆盖保留在库内但不进入模型目录。
        List<ScopeViewMeta> meta = DataSourceViews.scopeMeta(mapper.viewMetaOf(id),
                        mapper.columnMetaOfSource(id)).stream()
                .filter(item -> views.contains(item.view()))
                .toList();
        return new DataSourceScopeSnapshot(row, views, meta);
    }

    /** 数据目标或数据库身份是否变化；密码轮换不算。 */
    private static boolean targetChanged(DataSourceRow current, Save input) {
        return current.dbType() != input.dbType()
                || !current.host().equals(input.host())
                || current.port() != input.port()
                || !current.databaseName().equals(input.databaseName())
                || !current.username().equals(input.username());
    }

    /** 视图白名单整体替换：唯一写入口；集合实际变化时清除检查结果并强制停用。 */
    @Transactional
    public DataSourceView replaceViews(String id, int version, List<String> views) {
        DataSourceRow current = lockOrThrow(id);
        if (current.version() != version) throw DataSourceErrors.conflict();
        List<String> oldViews = DataSourceViews.names(mapper.viewsOf(id));
        if (new HashSet<>(oldViews).equals(new HashSet<>(views))) {
            return DataSourceViews.of(current, oldViews);
        }
        mapper.deleteViews(id);
        if (!views.isEmpty()) mapper.insertViews(id, views);
        if (mapper.bumpAfterViewChange(id, version) != 1) throw DataSourceErrors.conflict();
        log.info("DataSource views replaced: id={} views={}", id, views.size());
        DataSourceView updated = DataSourceViews.of(mapper.find(id),
                DataSourceViews.names(mapper.viewsOf(id)));
        audit.recordCurrent(AuditEvent.succeeded("agent", "scope:update-views", "DATASOURCE", id,
                updated.name(), "更新数据源「%s」允许查询的视图：%d 个；旧检查结果已清除并停用"
                        .formatted(updated.name(), views.size())));
        return updated;
    }

    /**
     * 指定视图的说明覆盖段整体替换：连接配置版本与语义版本都要未变化，
     * 递增语义版本但不使检查失效；内容未变化时不递增、不写成功审计。
     */
    @Transactional
    public QueryScopeDetail saveViewMetadata(String id, SaveMetadata input, List<ViewColumnMetaRow> columns) {
        DataSourceRow current = lockOrThrow(id);
        if (current.version() != input.version() || current.semanticVersion() != input.semanticVersion()) {
            throw DataSourceErrors.conflict();
        }
        String displayName = input.displayName() == null ? "" : input.displayName().trim();
        String description = input.description() == null ? "" : input.description().trim();
        List<ViewMetaRow> existingViews = mapper.viewMetaOf(id);
        List<ViewColumnMetaRow> existingColumns = mapper.columnMetaOfSource(id).stream()
                .filter(row -> row.viewName().equals(input.view()))
                .toList();
        ViewMetaRow existing = existingViews.stream()
                .filter(row -> row.viewName().equals(input.view()))
                .findFirst()
                .orElse(null);
        boolean unchanged = existing != null
                && existing.displayName().equals(displayName)
                && existing.description().equals(description)
                && sameColumns(existingColumns, columns);
        if (unchanged) {
            return QueryScopeDetail.of(DataSourceViews.of(mapper.find(id),
                    DataSourceViews.names(mapper.viewsOf(id))),
                    DataSourceViews.scopeMeta(existingViews, mapper.columnMetaOfSource(id)));
        }
        mapper.upsertViewMeta(id, input.view(), displayName, description);
        mapper.deleteColumnMeta(id, input.view());
        if (!columns.isEmpty()) mapper.insertColumnMeta(id, columns);
        if (mapper.bumpSemanticVersion(id, input.version(), input.semanticVersion()) != 1) {
            throw DataSourceErrors.conflict();
        }
        log.info("DataSource view metadata saved: id={} view={} columns={}", id, input.view(),
                columns.size());
        DataSourceRow saved = mapper.find(id);
        audit.recordCurrent(AuditEvent.succeeded("agent", "scope:update-metadata", "DATASOURCE", id,
                saved.name(), "更新数据源「%s」视图「%s」的说明：显示名%s、用途说明%s、字段说明 %d 个"
                        .formatted(saved.name(), input.view(), displayName.isEmpty() ? "已清空" : "已填写",
                                description.isEmpty() ? "已清空" : "已填写", columns.size())));
        return QueryScopeDetail.of(DataSourceViews.of(saved, DataSourceViews.names(mapper.viewsOf(id))),
                DataSourceViews.scopeMeta(mapper.viewMetaOf(id), mapper.columnMetaOfSource(id)));
    }

    private static boolean sameColumns(List<ViewColumnMetaRow> existing, List<ViewColumnMetaRow> submitted) {
        if (existing.size() != submitted.size()) return false;
        List<ViewColumnMetaRow> sortedExisting = existing.stream()
                .sorted((left, right) -> left.columnName().compareTo(right.columnName()))
                .toList();
        List<ViewColumnMetaRow> sortedSubmitted = submitted.stream()
                .sorted((left, right) -> left.columnName().compareTo(right.columnName()))
                .toList();
        for (int index = 0; index < sortedExisting.size(); index += 1) {
            if (!sortedExisting.get(index).columnName().equals(sortedSubmitted.get(index).columnName())
                    || !sortedExisting.get(index).description().equals(sortedSubmitted.get(index).description())) {
                return false;
            }
        }
        return true;
    }
}
