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
@RequestMapping("/api/maritime/wharfs")
@Tag(name = "涉海档案 · 码头")
public class WharfController {
    private final MaritimeService service;
    private final PermissionGuard guard;
    public WharfController(MaritimeService service, PermissionGuard guard) {
        this.service = service; this.guard = guard;
    }
    @GetMapping
    @Operation(summary = "分页查询码头")
    public ApiResponse<PageResult<WharfView>> list(Authentication authentication,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long portId,
            @RequestParam(required = false) Long policeStationId,
            @RequestParam(required = false) Long responsibleOfficerId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME_HARBOR_READ, "没有查看码头的权限");
        return ApiResponse.success(service.listWharf(MaritimeService.query(keyword, region, status, portId, policeStationId, responsibleOfficerId, null, page, pageSize)), request);
    }
    @GetMapping("/{id}")
    @Operation(summary = "查询码头完整档案")
    public ApiResponse<WharfView> detail(@PathVariable long id, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME_HARBOR_READ, "没有查看码头的权限");
        return ApiResponse.success(service.getWharf(id), request);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新建码头")
    public ApiResponse<WharfView> create(@Valid @RequestBody WriteWharf input, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME__WHARF_CREATE, "没有新建码头的权限");
        return ApiResponse.success(service.createWharf(input, null), request);
    }
    @PutMapping("/{id}")
    @Operation(summary = "编辑或启停码头，需最新版本")
    public ApiResponse<WharfView> update(@PathVariable long id, @Valid @RequestBody WriteWharf input, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME__WHARF_UPDATE, "没有维护码头的权限");
        return ApiResponse.success(service.updateWharf(id, input), request);
    }
    @DeleteMapping("/{id}")
    @Operation(summary = "删除未被引用的码头，需最新版本")
    public ApiResponse<Void> delete(@PathVariable long id, @RequestParam int version, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME__WHARF_DELETE, "没有删除码头的权限");
        service.deleteWharf(id, version);
        return ApiResponse.success(null, request);
    }
}
