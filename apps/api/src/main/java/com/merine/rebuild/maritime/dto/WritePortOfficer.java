package com.merine.rebuild.maritime.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

/** 民警可维护字段；fixtureKey 仅初始化入口可以写入。新建不传 version，编辑必传。 */
public record WritePortOfficer(
        @PositiveOrZero @Schema(nullable = true, description = "新建不传；编辑必填，缺失返回400，旧版本返回409") Integer version,
        @NotBlank @Size(max = 120) @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = false) String name,
        @NotBlank @Pattern(regexp = "ENABLED|DISABLED") @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = false) String status,
        @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = false) String policeStationId,
        @Size(max = 120) @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) String duty
) {}
