package com.merine.rebuild.system.role.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 角色管理的入参。字段名与前端表单一致，校验失败可落回对应字段。 */
public final class RoleRequests {

    private static final String CODE_PATTERN = "^[A-Za-z0-9._-]{1,64}$";

    private RoleRequests() {
    }

    /**
     * 新建角色。角色编码是授权依据，创建后不可修改，因此只在新建时提交。
     * 允许不勾任何权限（登录后只有首页入口），由页面负责提醒这层后果。
     */
    public record CreateRole(
            @NotBlank(message = "请输入角色编码")
            @Pattern(regexp = CODE_PATTERN,
                    message = "角色编码只能使用字母、数字与 . _ -，长度 1–64")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String code,

            @NotBlank(message = "请输入角色名称")
            @Size(max = 80, message = "角色名称最多 80 个字符")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Size(max = 200, message = "角色说明最多 200 个字符")
            @Schema(description = "角色说明，用于解释这个角色能用什么功能", nullable = true)
            String description,

            @NotNull(message = "缺少 permissionCodes 字段")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "功能权限码列表，可为空数组")
            List<@NotBlank(message = "权限码不能为空") String> permissionCodes) {
    }

    /** 编辑角色。编码不可改，状态也不在这里改——启停走各自的动作接口。 */
    public record UpdateRole(
            @NotNull(message = "缺少编辑版本，请刷新角色后重试")
            @PositiveOrZero(message = "编辑版本不能为负数")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "打开编辑表单时取得的角色版本")
            Integer version,

            @NotBlank(message = "请输入角色名称")
            @Size(max = 80, message = "角色名称最多 80 个字符")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Size(max = 200, message = "角色说明最多 200 个字符")
            @Schema(description = "角色说明，用于解释这个角色能用什么功能", nullable = true)
            String description,

            @NotNull(message = "缺少 permissionCodes 字段")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "功能权限码列表，可为空数组")
            List<@NotBlank(message = "权限码不能为空") String> permissionCodes) {
    }

    /**
     * 启用/停用的批量动作。单个与批量共用同一条路径，避免两套语义。
     *
     * 名称不用 `ChangeStatus`：用户管理的批量动作叫这个名字，两个 record 同名会让
     * springdoc 生成同一个 schema 名，客户端的入参类型随之串掉（实测过）。
     */
    public record ChangeRoleStatus(
            @NotEmpty(message = "请至少选择一个角色")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "角色编码列表")
            List<@NotBlank(message = "角色编码不能为空") String> roleCodes) {
    }
}
