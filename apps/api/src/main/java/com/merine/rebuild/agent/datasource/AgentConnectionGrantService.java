package com.merine.rebuild.agent.datasource;

import com.merine.rebuild.agent.AgentSecretCipher;
import com.merine.rebuild.agent.datasource.dto.DataSourceCheckOutcome;
import com.merine.rebuild.agent.datasource.dto.ScopeViewMeta;
import com.merine.rebuild.agent.datasource.dto.DataSourceRequests.State;
import com.merine.rebuild.agent.datasource.persistence.DataSourceMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 复检固定数据源、检测并发变更并签发当次连接信息与查询目录，不执行模型 SQL。
 *
 * <p>网络授权复检在事务外完成；随后用短事务锁父行，确认连接版本与语义版本都未变化，
 * 再读取白名单与说明覆盖值，保证下发的连接、范围、语义是同一个一致快照。
 */
@Service
public class AgentConnectionGrantService {
    /** 服务间桥协议版本：2 起包含语义版本与目录说明。 */
    public static final int CONTRACT_VERSION = 2;

    private final DataSourceMapper mapper;
    private final DataSourceChecker checker;
    private final DataSourceTargetGuard targets;
    private final AgentSecretCipher cipher;
    private final DataSourceStore store;

    public AgentConnectionGrantService(DataSourceMapper mapper, DataSourceChecker checker,
                                       DataSourceTargetGuard targets, AgentSecretCipher cipher,
                                       DataSourceStore store) {
        this.mapper = mapper;
        this.checker = checker;
        this.targets = targets;
        this.cipher = cipher;
        this.store = store;
    }

    public ConnectionGrant grant(String sourceId, String scope) {
        var source = mapper.findSecret(sourceId);
        if (source == null || source.status() != State.ENABLED) throw DataSourceErrors.sourceUnavailable();
        List<String> views = DataSourceViews.names(mapper.viewsOf(sourceId));
        if (views.isEmpty()) throw DataSourceErrors.noViews();
        String password = cipher.decrypt(DataSourceStore.secretAad(sourceId), source.passwordCipher());
        var checked = checker.check(source.host(), source.port(), source.databaseName(),
                source.username(), password, views);
        if (checked.outcome() != DataSourceCheckOutcome.PASSED) {
            throw DataSourceErrors.checkFailed("只读授权复检未通过，请在数据源管理中检查配置");
        }
        // 复检耗时期间若配置、范围或说明被修改，不下发新旧混合的目录。
        var snapshot = store.snapshot(sourceId, source.version(), source.semanticVersion());
        var target = targets.resolve(source.host(), source.port());
        return new ConnectionGrant(CONTRACT_VERSION, source.id(), source.name(),
                snapshot.source().version(), target.address().getHostAddress(), source.host(),
                target.port(), source.databaseName(), source.username(), password, !target.loopback(),
                snapshot.views(), scope, snapshot.source().semanticVersion(), snapshot.meta());
    }

    /** 仅供受信 Agent 服务端读取，禁止放入模型上下文、日志和浏览器响应。 */
    public record ConnectionGrant(int contractVersion, String id, String name, int version,
                                  String address, String host, int port, String database, String username,
                                  String password, boolean tls, List<String> views, String scope,
                                  int semanticVersion, List<ScopeViewMeta> viewMeta) {
        @Override
        public String toString() {
            return "ConnectionGrant[redacted]";
        }
    }
}
