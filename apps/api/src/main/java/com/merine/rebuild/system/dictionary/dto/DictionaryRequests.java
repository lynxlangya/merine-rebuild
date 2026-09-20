package com.merine.rebuild.system.dictionary.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 字典管理入参。字典编码与字典项取值都是不可变标识，因此编辑请求里没有它们。 */
public final class DictionaryRequests {

    /** 字典编码：小写字母开头，允许小写字母、数字、点、下划线与连字符，形如 vessel.type。 */
    private static final String CODE_PATTERN = "^[a-z][a-z0-9._-]{2,63}$";
    /** 字典项取值：与编码同形，另外允许大写（历史值里有 ENABLED 这类枚举风格取值）。 */
    private static final String VALUE_PATTERN = "^[A-Za-z0-9._-]{1,64}$";
    private static final String STATUS_PATTERN = "ENABLED|DISABLED";

    private DictionaryRequests() {
    }

    public record CreateDictionary(
            @NotBlank(message = "请输入字典编码")
            @Pattern(regexp = CODE_PATTERN,
                    message = "字典编码只能使用小写字母、数字与 . _ -，长度 3–64")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String code,

            @NotBlank(message = "请输入字典名称")
            @Size(max = 80, message = "字典名称最多 80 个字符")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Size(max = 200, message = "说明最多 200 个字符")
            @Schema(nullable = true)
            String description) {
    }

    public record UpdateDictionary(
            @NotNull(message = "缺少编辑版本，请刷新字典后重试")
            @PositiveOrZero(message = "编辑版本不能为负数")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Integer version,

            @NotBlank(message = "请输入字典名称")
            @Size(max = 80, message = "字典名称最多 80 个字符")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Size(max = 200, message = "说明最多 200 个字符")
            @Schema(nullable = true)
            String description,

            @NotBlank(message = "请选择状态")
            @Pattern(regexp = STATUS_PATTERN, message = "状态只能是 ENABLED 或 DISABLED")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String status) {
    }

    public record CreateDictionaryItem(
            @NotBlank(message = "请输入字典项取值")
            @Pattern(regexp = VALUE_PATTERN,
                    message = "字典项取值只能使用字母、数字与 . _ -，长度 1–64")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "业务数据里保存的值，创建后不可修改")
            String value,

            @NotBlank(message = "请输入字典项标签")
            @Size(max = 80, message = "字典项标签最多 80 个字符")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String label,

            @Size(max = 200, message = "说明最多 200 个字符")
            @Schema(nullable = true)
            String description,

            @PositiveOrZero(message = "排序不能为负数")
            @Schema(description = "同类型内排序，越小越靠前；缺省为 0")
            Integer sortOrder) {
    }

    public record UpdateDictionaryItem(
            @NotNull(message = "缺少编辑版本，请刷新字典项后重试")
            @PositiveOrZero(message = "编辑版本不能为负数")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Integer version,

            @NotBlank(message = "请输入字典项标签")
            @Size(max = 80, message = "字典项标签最多 80 个字符")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String label,

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
