package com.merine.rebuild.system.menu.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 菜单管理入参。
 *
 * 节点类型创建后不可修改：类型决定了 route key 与权限码的约束，
 * 允许中途改类型会让「页面必须有 route key」这类不变量失去意义；要换类型就删掉重建。
 */
public final class MenuRequests {

    private static final String TYPE_PATTERN = "DIRECTORY|PAGE|TAB|BUTTON";
    private static final String STATUS_PATTERN = "ENABLED|DISABLED";
    /** 权限码形如 system:user:toggle-status：小写字母、数字、连字符，用冒号分段。 */
    private static final String PERMISSION_CODE_PATTERN = "^[a-z][a-z0-9-]*(:[a-z0-9-]{1,32}){1,3}$";
    private static final String ROUTE_KEY_PATTERN = "^[a-zA-Z][a-zA-Z0-9._-]{2,63}$";
    private static final String ID_PATTERN = "^[0-9]{1,19}$";

    private MenuRequests() {
    }

    public record CreateMenu(
            @Pattern(regexp = ID_PATTERN, message = "上级节点 id 格式不正确")
            @Schema(description = "上级节点 id；顶层节点为 null", nullable = true)
            String parentId,

            @NotBlank(message = "请选择节点类型")
            @Pattern(regexp = TYPE_PATTERN, message = "节点类型只能是目录、页面、页签或按钮")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String type,

            @NotBlank(message = "请输入节点名称")
            @Size(max = 80, message = "节点名称最多 80 个字符")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Pattern(regexp = ROUTE_KEY_PATTERN, message = "路由 key 格式不正确")
            @Schema(description = "前端已注册的路由 key；仅页面节点需要", nullable = true)
            String routeKey,

            @Size(max = 64, message = "图标名称最多 64 个字符")
            @Schema(description = "前端已注册的图标名称；仅目录与页面可设置，留空表示默认图标",
                    nullable = true)
            String iconName,

            @Pattern(regexp = PERMISSION_CODE_PATTERN,
                    message = "权限码形如 system:user:create，只能使用小写字母、数字、连字符与冒号")
            @Schema(description = "权限码；页面、页签、按钮必填，目录留空", nullable = true)
            String permissionCode,

            @Size(max = 200, message = "说明最多 200 个字符")
            @Schema(nullable = true)
            String description,

            @PositiveOrZero(message = "排序不能为负数")
            @Schema(description = "同级排序，越小越靠前；缺省为 0")
            Integer sortOrder) {
    }

    public record UpdateMenu(
            @NotNull(message = "缺少编辑版本，请刷新菜单后重试")
            @PositiveOrZero(message = "编辑版本不能为负数")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Integer version,

            @Pattern(regexp = ID_PATTERN, message = "上级节点 id 格式不正确")
            @Schema(description = "上级节点 id；顶层节点为 null", nullable = true)
            String parentId,

            @NotBlank(message = "请输入节点名称")
            @Size(max = 80, message = "节点名称最多 80 个字符")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Pattern(regexp = ROUTE_KEY_PATTERN, message = "路由 key 格式不正确")
            @Schema(description = "前端已注册的路由 key；仅页面节点需要", nullable = true)
            String routeKey,

            @Size(max = 64, message = "图标名称最多 64 个字符")
            @Schema(description = "前端已注册的图标名称；仅目录与页面可设置，留空表示默认图标",
                    nullable = true)
            String iconName,

            @Size(max = 200, message = "说明最多 200 个字符")
            @Schema(nullable = true)
            String description,

            @NotNull(message = "请填写排序")
            @PositiveOrZero(message = "排序不能为负数")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Integer sortOrder,

            @NotBlank(message = "请选择状态")
            @Pattern(regexp = STATUS_PATTERN, message = "状态只能是 ENABLED 或 DISABLED")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String status) {
    }
}
