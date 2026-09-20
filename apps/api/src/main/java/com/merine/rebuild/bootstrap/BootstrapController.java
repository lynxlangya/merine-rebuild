package com.merine.rebuild.bootstrap;

import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.system.security.PermissionCodes;
import com.merine.rebuild.system.security.PermissionGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bootstrap")
public class BootstrapController {
    private final BootstrapService service;
    private final PermissionGuard guard;

    public BootstrapController(BootstrapService service, PermissionGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    @Operation(summary = "查询本地工程与数据库联调状态")
    public ApiResponse<BootstrapStatus> status(Authentication authentication,
                                              HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.DIAGNOSTICS_READ, "没有查看工程诊断的权限");
        return ApiResponse.success(service.status(), request);
    }

    @PostMapping("/probes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "写入一条本地合成联调记录")
    public ApiResponse<ProbeRecord> create(@Valid @RequestBody CreateProbe input,
                                           Authentication authentication,
                                           HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.DIAGNOSTICS_WRITE, "没有写入探针记录的权限");
        return ApiResponse.success(service.create(input.note()), request);
    }

    public record CreateProbe(
            @NotBlank(message = "请填写联调记录")
            @Size(max = 120, message = "联调记录最多 120 个字符")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            String note) {
    }
}
