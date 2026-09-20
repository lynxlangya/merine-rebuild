package com.merine.rebuild.system.org;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 单位管理的入参。字段名与前端表单一致，校验失败可落回对应字段。 */
public final class UnitRequests {

    private static final String CODE_PATTERN = "^[A-Za-z0-9._-]{1,64}$";
    private static final String AREA_CODE_PATTERN = "^[0-9]{2,12}$";

    private UnitRequests() {
    }

    public record CreateUnit(
            @NotBlank(message = "请输入单位编码")
            @Pattern(regexp = CODE_PATTERN,
                    message = "单位编码只能使用字母、数字与 . _ -，长度 1–64")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String code,

            @NotBlank(message = "请输入单位名称")
            @Size(max = 80, message = "单位名称最多 80 个字符")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Pattern(regexp = CODE_PATTERN,
                    message = "上级单位编码格式不正确")
            @Schema(description = "上级单位编码；一级单位为 null", nullable = true)
            String parentCode,

            @NotBlank(message = "请输入行政区划代码")
            @Pattern(regexp = AREA_CODE_PATTERN,
                    message = "行政区划代码只能是 2–12 位数字")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "2–12 位数字")
            String areaCode) {
    }

    public record UpdateUnit(
            @NotNull(message = "缺少编辑版本，请刷新单位后重试")
            @PositiveOrZero(message = "编辑版本不能为负数")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "打开编辑表单时取得的单位版本")
            Integer version,

            @NotBlank(message = "请输入单位名称")
            @Size(max = 80, message = "单位名称最多 80 个字符")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String name,

            @Pattern(regexp = CODE_PATTERN,
                    message = "上级单位编码格式不正确")
            @Schema(description = "上级单位编码；一级单位为 null", nullable = true)
            String parentCode,

            @NotBlank(message = "请输入行政区划代码")
            @Pattern(regexp = AREA_CODE_PATTERN,
                    message = "行政区划代码只能是 2–12 位数字")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "2–12 位数字")
            String areaCode) {
    }
}
