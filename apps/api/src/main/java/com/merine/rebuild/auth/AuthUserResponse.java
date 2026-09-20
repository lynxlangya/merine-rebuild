package com.merine.rebuild.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 当前身份响应。对外标识用字符串；不含密码哈希与任何内部字段。 */
public record AuthUserResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String loginName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String unitName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "角色编码，按编码升序，与 roleNames 下标一一对应")
        List<String> roleCodes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> roleNames,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "有效功能权限码；内置管理员角色已在此展开为全部权限码")
        List<String> permissionCodes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "授权版本；角色、角色权限或单位变化时递增，旧会话据此失效")
        int authorizationVersion) {

    public static AuthUserResponse from(AuthenticatedAccount account) {
        return new AuthUserResponse(
                Long.toString(account.userId()),
                account.loginName(),
                account.displayName(),
                account.unitName(),
                account.roleCodes(),
                account.roleNames(),
                account.permissionCodes(),
                account.authorizationVersion());
    }
}
