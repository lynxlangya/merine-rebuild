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
@RequestMapping("/api/maritime/port-officers")
@Tag(name = "涉海档案 · 民警")
public class PortOfficerController {
    private final MaritimeService service;
    private final PermissionGuard guard;
    public PortOfficerController(MaritimeService service, PermissionGuard guard) {
        this.service = service; this.guard = guard;
    }
    @GetMapping
    @Operation(summary = "分页查询民警")
    public ApiResponse<PageResult<PortOfficerView>> list(Authentication authentication,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long policeStationId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME_POLICING_READ, "没有查看民警的权限");
        return ApiResponse.success(service.listPortOfficer(MaritimeService.query(keyword, region, status, null, policeStationId, null, null, page, pageSize)), request);
    }
    @GetMapping("/{id}")
    @Operation(summary = "查询民警完整档案")
    public ApiResponse<PortOfficerView> detail(@PathVariable long id, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME_POLICING_READ, "没有查看民警的权限");
        return ApiResponse.success(service.getPortOfficer(id), request);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新建民警")
    public ApiResponse<PortOfficerView> create(@Valid @RequestBody WritePortOfficer input, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME__PORT_OFFICER_CREATE, "没有新建民警的权限");
        return ApiResponse.success(service.createPortOfficer(input, null), request);
    }
    @PutMapping("/{id}")
    @Operation(summary = "编辑或启停民警，需最新版本")
    public ApiResponse<PortOfficerView> update(@PathVariable long id, @Valid @RequestBody WritePortOfficer input, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME__PORT_OFFICER_UPDATE, "没有维护民警的权限");
        return ApiResponse.success(service.updatePortOfficer(id, input), request);
    }
    @DeleteMapping("/{id}")
    @Operation(summary = "删除未被引用的民警，需最新版本")
    public ApiResponse<Void> delete(@PathVariable long id, @RequestParam int version, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME__PORT_OFFICER_DELETE, "没有删除民警的权限");
        service.deletePortOfficer(id, version);
        return ApiResponse.success(null, request);
    }
}
