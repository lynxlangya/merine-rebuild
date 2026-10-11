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

    /** 目标视图既不在执行白名单，也没有已保存的说明，不能编辑。 */
    static ApiException scopeViewUntracked(String view) {
        return new ApiException(HttpStatus.CONFLICT, "SCOPE_VIEW_NOT_TRACKED",
                "视图「" + view + "」不在允许查询范围内，也没有已保存的说明");
    }

    /** 说明覆盖值总量超限；要求管理员缩短说明或减少开放范围，不静默截断。 */
    static ApiException scopeMetadataTooLarge() {
        return new ApiException(HttpStatus.BAD_REQUEST, "SCOPE_METADATA_TOO_LARGE",
                "视图与字段说明总量超过 32 KiB，请缩短说明或减少开放范围");
    }

    /** 下发目录超限；不下发半份说明，要求缩小范围后重试。 */
    static ApiException scopeResponseTooLarge() {
        return new ApiException(HttpStatus.CONFLICT, "DATASOURCE_SCOPE_TOO_LARGE",
                "当前范围的说明超出单次下发上限，请减少开放视图或缩短说明");
    }

    /** 需要实时核对目标库但当前账号没有测试连接权限。 */
    static ApiException probePermissionRequired() {
        return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                "该操作需要实时核对目标库字段，请授予数据源测试连接权限");
    }
}
