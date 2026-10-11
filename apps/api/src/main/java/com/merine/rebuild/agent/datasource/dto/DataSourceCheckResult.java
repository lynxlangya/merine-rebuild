package com.merine.rebuild.agent.datasource.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * 一次连接与授权检查的结果。{@code detectedViews} 是服务端从目标库检测到的、
 * 当前账号具备 SELECT 的视图清单，供管理员勾选；勾选不会创建或修改数据库授权。
 */
@Schema(name = "DataSourceCheckResult")
public record DataSourceCheckResult(
        DataSourceCheckOutcome outcome,
        String message,
        Instant checkedAt,
        List<String> detectedViews) {
}
