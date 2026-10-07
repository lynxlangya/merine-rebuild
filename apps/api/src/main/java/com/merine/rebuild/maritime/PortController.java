package com.merine.rebuild.maritime;

import com.merine.rebuild.common.*;
import com.merine.rebuild.maritime.dto.*;
import com.merine.rebuild.system.security.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/maritime/ports")
@Tag(name = "涉海档案 · 港口")
public class PortController {
    private final MaritimeService service;
    private final PermissionGuard guard;
    public PortController(MaritimeService service, PermissionGuard guard) {
        this.service = service; this.guard = guard;
    }
    @GetMapping
    @Operation(summary = "分页查询港口")
    public ApiResponse<PageResult<PortView>> list(Authentication authentication,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME_HARBOR_READ, "没有查看港口的权限");
        return ApiResponse.success(service.listPort(MaritimeService.query(keyword, region, status, null, null, null, null, page, pageSize)), request);
    }
    @GetMapping("/{id}")
    @Operation(summary = "查询港口完整档案")
    public ApiResponse<PortView> detail(@PathVariable long id, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME_HARBOR_READ, "没有查看港口的权限");
        return ApiResponse.success(service.getPort(id), request);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新建港口")
    public ApiResponse<PortView> create(@Valid @RequestBody WritePort input, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME__PORT_CREATE, "没有新建港口的权限");
        return ApiResponse.success(service.createPort(input, null), request);
    }
    @PutMapping("/{id}")
    @Operation(summary = "编辑或启停港口，需最新版本")
    public ApiResponse<PortView> update(@PathVariable long id, @Valid @RequestBody WritePort input, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME__PORT_UPDATE, "没有维护港口的权限");
        return ApiResponse.success(service.updatePort(id, input), request);
    }
    @DeleteMapping("/{id}")
    @Operation(summary = "删除未被引用的港口，需最新版本")
    public ApiResponse<Void> delete(@PathVariable long id, @RequestParam int version, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME__PORT_DELETE, "没有删除港口的权限");
        service.deletePort(id, version);
        return ApiResponse.success(null, request);
    }
}
