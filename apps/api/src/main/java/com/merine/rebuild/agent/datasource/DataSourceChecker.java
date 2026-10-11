package com.merine.rebuild.agent.datasource;

import com.merine.rebuild.agent.datasource.DataSourceTargetGuard.TargetDeniedException;
import com.merine.rebuild.agent.datasource.dto.DataSourceCheckOutcome;
import com.merine.rebuild.agent.datasource.dto.DataSourceCheckResult;
import com.merine.rebuild.common.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 连接检查入口：先做部署允许范围校验，再在并发名额内执行检查。
 * 每次检查使用一条临时连接并及时关闭，不为停用配置保留长期连接池；不排队堆积，也不自动重试。
 */
@Component
public class DataSourceChecker {
    private final DataSourceTargetGuard guard;
    private final DataSourceReadOnlyInspector inspector;
    private final DataSourceProperties properties;
    private final Semaphore slots;

    public DataSourceChecker(DataSourceTargetGuard guard, DataSourceReadOnlyInspector inspector,
                             DataSourceProperties properties) {
        this.guard = guard;
        this.inspector = inspector;
        this.properties = properties;
        this.slots = new Semaphore(properties.maxConcurrentChecks(), true);
    }

    DataSourceCheckResult check(String host, int port, String database, String username,
                                String password, List<String> selectedViews) {
        DataSourceTargetGuard.ResolvedTarget target;
        try {
            target = guard.resolve(host, port);
        } catch (TargetDeniedException denied) {
            return new DataSourceCheckResult(DataSourceCheckOutcome.CONNECTION_FAILED,
                    denied.getMessage(), Instant.now(), List.of());
        }
        boolean acquired;
        try {
            acquired = slots.tryAcquire(properties.acquireTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            acquired = false;
        }
        if (!acquired) {
            throw new ApiException(HttpStatus.CONFLICT, "DATASOURCE_CHECK_BUSY",
                    "正在执行的数据源检查较多，请稍后重试");
        }
        try {
            return inspector.inspect(target, database, username, password, selectedViews);
        } finally {
            slots.release();
        }
    }
}
