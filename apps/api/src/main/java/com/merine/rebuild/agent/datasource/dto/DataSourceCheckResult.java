package com.merine.rebuild.agent.datasource.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * 一次连接与授权检查的结果。{@code detectedViews} 是服务端从目标库检测到的、
 * 当前账号具备 SELECT 的视图清单，供管理员勾选；勾选不会创建或修改数据库授权。
 * {@code sourceVersion} 是产生本次结果时的数据源配置版本，供页面丢弃过期的字段探测结果；
 * 未保存配置的草稿检查为 null。
 */
@Schema(name = "DataSourceCheckResult")
public record DataSourceCheckResult(
        DataSourceCheckOutcome outcome,
        String message,
        Instant checkedAt,
        List<String> detectedViews,
        List<DataSourceViewColumn> viewColumns,
        Integer sourceVersion) {

    /** 探测/检查结果带上当时的数据源配置版本；未保存配置的草稿检查为 null。 */
    public DataSourceCheckResult withSourceVersion(int version) {
        return new DataSourceCheckResult(outcome, message, checkedAt, detectedViews, viewColumns, version);
    }
}
