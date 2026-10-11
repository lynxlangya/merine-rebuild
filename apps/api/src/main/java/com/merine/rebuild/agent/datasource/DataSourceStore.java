package com.merine.rebuild.agent.datasource;

import com.merine.rebuild.agent.AgentSecretCipher;
import com.merine.rebuild.agent.datasource.dto.DataSourceCheckOutcome;
import com.merine.rebuild.agent.datasource.dto.DataSourceRequests.Save;
import com.merine.rebuild.agent.datasource.dto.DataSourceRequests.State;
import com.merine.rebuild.agent.datasource.dto.DataSourceView;
import com.merine.rebuild.agent.datasource.persistence.DataSourceMapper;
import com.merine.rebuild.agent.datasource.persistence.DataSourceRow;
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
        replaceViews(id, input.views());
        log.info("DataSource created: id={}", id);
        DataSourceView created = DataSourceViews.of(mapper.find(id), input.views());
        audit.recordCurrent(AuditEvent.succeeded("agent", "datasource:create", "DATASOURCE", id,
                created.name(), "新建数据源「%s」（%s:%d/%s，默认停用）".formatted(
                        created.name(), created.host(), created.port(), created.databaseName())));
        return created;
    }

    /**
     * 编辑：连接参数、密码或视图范围变化时清除旧检查结果并强制停用；
     * 只改名称/备注时保留状态与检查结论。锁行 + 版本条件保证并发编辑只有一个生效。
     */
    @Transactional
    public DataSourceView update(String id, Save input) {
        DataSourceRow current = lockOrThrow(id);
        if (input.version() == null || input.version() != current.version()) {
            throw DataSourceErrors.conflict();
        }
        List<String> oldViews = DataSourceViews.names(mapper.viewsOf(id));
        boolean changed = connectionChanged(current, input, oldViews);
        String encrypted = input.password() == null ? null
                : cipher.encrypt(secretAad(id), input.password());
        int updated = changed
                ? mapper.replaceConnection(id, input, encrypted, current.version())
                : mapper.updateMetadata(id, input, current.version());
        if (updated != 1) throw DataSourceErrors.conflict();
        if (changed) replaceViews(id, input.views());
        log.info("DataSource updated: id={} connectionChanged={}", id, changed);
        DataSourceView view = DataSourceViews.of(mapper.find(id),
                DataSourceViews.names(mapper.viewsOf(id)));
        audit.recordCurrent(AuditEvent.succeeded("agent", "datasource:update", "DATASOURCE", id,
                view.name(), "修改数据源「%s」：%s".formatted(view.name(), changed
                        ? "连接参数或视图范围变化，旧检查结果已清除并停用"
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

    /** 视图白名单整体替换：删除旧集合后按当前提交重建，操作在上层事务与行锁内完成。 */
    private void replaceViews(String dataSourceId, List<String> views) {
        mapper.deleteViews(dataSourceId);
        if (!views.isEmpty()) mapper.insertViews(dataSourceId, views);
    }

    private static boolean connectionChanged(DataSourceRow current, Save input, List<String> oldViews) {
        return current.dbType() != input.dbType()
                || !current.host().equals(input.host())
                || current.port() != input.port()
                || !current.databaseName().equals(input.databaseName())
                || !current.username().equals(input.username())
                || input.password() != null
                || !new HashSet<>(oldViews).equals(new HashSet<>(input.views()));
    }
}
