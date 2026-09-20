package com.merine.rebuild.system.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 用户管理的入参。字段名与前端表单一致，校验失败可落回对应字段。 */
public final class UserRequests {

    private UserRequests() {
    }

    /**
     * 新建用户。账号与初始密码必填——没有凭据的账号无法登录，
     * 让调用方显式给出初始密码，而不是由服务端猜一个再想办法告诉他。
     */
    public record CreateUser(
            @NotBlank(message = "请输入账号")
            @Pattern(regexp = "^[a-z0-9.]{4,32}$",
                    message = "账号只能使用小写字母、数字与点，长度 4–32")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String loginName,

            @NotBlank(message = "请输入姓名")
            @Size(max = 80, message = "姓名最多 80 个字符")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String displayName,

            @NotBlank(message = "请选择所属单位")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "单位业务编码")
            String unitCode,

            @NotEmpty(message = "请至少选择一个角色")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "角色编码列表")
            List<@NotBlank(message = "角色编码不能为空") String> roleCodes,

            /**
             * 初始密码。下限 6 位与本地演示账号保持一致；
             * 真实环境的密码策略（复杂度、有效期、首登改密）属后续阶段。
             */
            @NotBlank(message = "请输入初始密码")
            @Size(min = 6, max = PasswordLimits.MAX_BYTES, message = "初始密码至少 6 个字符，最多 72 个 UTF-8 字节")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "至少 6 个字符，UTF-8 编码不超过 72 字节")
            String password) {
    }

    /**
     * 编辑用户。账号不可修改，因此不在请求里；状态也不在这里改——
     * 启用与停用走各自的动作接口，避免同一条规则有两条写入路径。
     */
    public record UpdateUser(
            @NotNull(message = "缺少编辑版本，请刷新用户后重试")
            @PositiveOrZero(message = "编辑版本不能为负数")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "打开编辑表单时取得的用户版本")
            Integer version,

            @NotBlank(message = "请输入姓名")
            @Size(max = 80, message = "姓名最多 80 个字符")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String displayName,

            @NotBlank(message = "请选择所属单位")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "单位业务编码")
            String unitCode,

            @NotEmpty(message = "请至少选择一个角色")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "角色编码列表")
            List<@NotBlank(message = "角色编码不能为空") String> roleCodes,

            @Schema(description = "重置密码；省略或为 null 表示不修改；至少 6 个字符，UTF-8 编码不超过 72 字节")
            @Size(min = 6, max = PasswordLimits.MAX_BYTES, message = "新密码至少 6 个字符，最多 72 个 UTF-8 字节")
            String newPassword) {
    }

    /** 启用/停用的批量动作。单个与批量共用同一条路径，避免两套语义。 */
    public record ChangeStatus(
            @NotEmpty(message = "请至少选择一个用户")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "用户 id 列表")
            List<@NotBlank(message = "用户 id 不能为空") String> userIds) {
    }
}
