package com.merine.rebuild.system.user;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * 用户对外表示。列表与详情共用一种结构：编辑表单需要的字段列表里都有，
 * 不再为同一份数据造第二套 VO。
 * 不含口令哈希——它只存在于 sys_user 表与认证比对那一刻。
 */
public record UserSummary(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "用户技术主键，以十进制字符串返回")
        String id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String loginName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String unitCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String unitName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "角色编码，按编码升序，与 roleNames 下标一一对应")
        List<String> roleCodes,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "角色名称，与 roleCodes 下标一一对应")
        List<String> roleNames,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                description = "账号状态：ENABLED 启用，DISABLED 停用")
        String status,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "编辑版本；保存时原样提交，冲突须重新读取")
        int version,
        @Schema(description = "最近一次成功登录时刻（UTC）；从未登录为 null")
        Instant lastLoginAt) {

    public static UserSummary from(UserRow row) {
        return new UserSummary(
                Long.toString(row.id()),
                row.loginName(),
                row.displayName(),
                row.unitCode(),
                row.unitName(),
                row.roleCodes(),
                row.roleNames(),
                row.status(),
                row.version(),
                row.lastLoginAt());
    }
}
