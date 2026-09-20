package com.merine.rebuild.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 当前身份响应。对外标识用字符串；不含密码哈希与任何内部字段。 */
public record AuthUserResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String loginName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String unitName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> roleNames,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "授权版本；角色或数据范围变化时递增，旧会话据此失效")
        int authorizationVersion) {

    public static AuthUserResponse from(AuthenticatedAccount account) {
        return new AuthUserResponse(
                Long.toString(account.userId()),
                account.loginName(),
                account.displayName(),
                account.unitName(),
                account.roleNames(),
                account.authorizationVersion());
    }
}
