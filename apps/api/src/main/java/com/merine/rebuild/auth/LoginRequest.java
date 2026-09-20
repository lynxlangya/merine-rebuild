package com.merine.rebuild.auth;

import com.merine.rebuild.system.user.account.PasswordLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录入参。字段名与前端表单字段一致，校验失败时前端可把错误落回对应输入框。
 * 密码只用于本次比对，不记录日志、不回显。
 */
public record LoginRequest(
        @NotBlank(message = "请输入账号")
        @Size(max = 64, message = "账号最多 64 个字符")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String loginName,

        @NotBlank(message = "请输入密码")
        @Size(max = PasswordLimits.MAX_BYTES, message = PasswordLimits.MESSAGE)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "UTF-8 编码不超过 72 字节")
        String password,

        /**
         * 用包装类型而不是 boolean：Boot 4 使用的 Jackson 3 对 record 里缺失的原始类型
         * 不再默默取默认值，而是判定整个请求体不可解析（实测缺失该字段会返回 400
         * INVALID_JSON）。可省略的布尔字段一律用包装类型，再用下面的方法归一化。
         */
        @Schema(description = "勾选后延长本次会话的空闲超时；省略或为 null 表示不保持登录")
        Boolean rememberMe) {

    /** 缺失与显式 null 都按“不保持登录”处理。 */
    public boolean rememberMeOrDefault() {
        return Boolean.TRUE.equals(rememberMe);
    }
}
