package com.merine.rebuild.agent.datasource;

import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.common.ApiResponse;
import java.util.List;
import org.springframework.http.HttpStatus;

/** 数据源用例的稳定错误；错误码供前端与调用方判断分支，消息面向管理员。 */
final class DataSourceErrors {
    private DataSourceErrors() {}

    static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "DATASOURCE_NOT_FOUND", "数据源配置不存在");
    }

    static ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT, "DATASOURCE_CONFLICT", "配置已变化，请刷新后重试");
    }

    static ApiException checkConflict() {
        return new ApiException(HttpStatus.CONFLICT, "DATASOURCE_CONFLICT",
                "配置在检查期间发生变化，请重新检查");
    }

    static ApiException nameExists() {
        String message = "已有同名数据源，请用名称区分环境或账号";
        return new ApiException(HttpStatus.CONFLICT, "DATASOURCE_NAME_EXISTS", message,
                List.of(new ApiResponse.FieldError("name", message)));
    }

    static ApiException noViews() {
        return new ApiException(HttpStatus.CONFLICT, "DATASOURCE_NO_VIEWS",
                "请先检测并勾选至少一个允许查询的视图，再启用数据源");
    }

    static ApiException secretUnavailable() {
        return new ApiException(HttpStatus.CONFLICT, "DATASOURCE_SECRET_UNAVAILABLE",
                "数据源密码不可用，请重新保存配置");
    }

    static ApiException sourceUnavailable() {
        return new ApiException(HttpStatus.CONFLICT, "DATASOURCE_UNAVAILABLE",
                "数据源未启用或已不可用");
    }

    static ApiException viewNotAllowed() {
        return new ApiException(HttpStatus.CONFLICT, "DATASOURCE_VIEW_NOT_ALLOWED",
                "数据源未授权该视图，不能执行查询");
    }

    static ApiException targetDenied(String message) {
        return new ApiException(HttpStatus.CONFLICT, "DATASOURCE_TARGET_DENIED", message);
    }

    static ApiException queryFailed() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DATASOURCE_QUERY_FAILED",
                "数据源查询失败，请稍后重试");
    }

    static ApiException checkFailed(String message) {
        return new ApiException(HttpStatus.CONFLICT, "DATASOURCE_CHECK_FAILED", message);
    }
}
