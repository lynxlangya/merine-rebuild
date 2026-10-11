package com.merine.rebuild.agent.datasource;

import com.merine.rebuild.agent.AgentSecretCipher;
import com.merine.rebuild.agent.datasource.dto.DataSourceCheckOutcome;
import com.merine.rebuild.agent.datasource.dto.DataSourceRequests.State;
import com.merine.rebuild.agent.datasource.persistence.DataSourceMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/** 复检固定数据源、检测并发变更并签发当次连接信息，不执行模型 SQL。 */
@Service
public class AgentConnectionGrantService {
    private final DataSourceMapper mapper;
    private final DataSourceChecker checker;
    private final DataSourceTargetGuard targets;
    private final AgentSecretCipher cipher;

    public AgentConnectionGrantService(DataSourceMapper mapper, DataSourceChecker checker,
                                       DataSourceTargetGuard targets, AgentSecretCipher cipher) {
        this.mapper = mapper;
        this.checker = checker;
        this.targets = targets;
        this.cipher = cipher;
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
        // 复检耗时期间若配置/白名单被修改或停用，不下发旧配置。
        var latest = mapper.findSecret(sourceId);
        if (latest == null || latest.status() != State.ENABLED || latest.version() != source.version()) {
            throw DataSourceErrors.conflict();
        }
        var target = targets.resolve(source.host(), source.port());
        return new ConnectionGrant(source.id(), source.name(), source.version(),
                        target.address().getHostAddress(), source.host(), target.port(),
                        source.databaseName(), source.username(), password, !target.loopback(), views, scope);
    }

    /** 仅供受信 Agent 服务端读取，禁止放入模型上下文、日志和浏览器响应。 */
    public record ConnectionGrant(String id, String name, int version, String address, String host,
                                  int port, String database, String username, String password,
                                  boolean tls, List<String> views, String scope) {
        @Override public String toString() { return "ConnectionGrant[redacted]"; }
    }
}
