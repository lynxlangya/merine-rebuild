package com.merine.rebuild.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 本地开发免密登录请求；仅在 dev profile 的控制器使用。 */
public record DevLoginRequest(
        @NotBlank(message = "请选择账号")
        @Size(max = 64, message = "账号最多 64 个字符")
        String loginName,
        Boolean rememberMe) {

    public boolean rememberMeOrDefault() {
        return Boolean.TRUE.equals(rememberMe);
    }
}
