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
@RequestMapping("/api/maritime/anchorages")
@Tag(name = "涉海档案 · 锚地")
public class AnchorageController {
    private final MaritimeService service;
    private final PermissionGuard guard;
    public AnchorageController(MaritimeService service, PermissionGuard guard) {
        this.service = service; this.guard = guard;
    }
    @GetMapping
    @Operation(summary = "分页查询锚地")
    public ApiResponse<PageResult<AnchorageView>> list(Authentication authentication,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME_HARBOR_READ, "没有查看锚地的权限");
        return ApiResponse.success(service.listAnchorage(MaritimeService.query(keyword, region, status, null, null, null, null, page, pageSize)), request);
    }
    @GetMapping("/{id}")
    @Operation(summary = "查询锚地完整档案")
    public ApiResponse<AnchorageView> detail(@PathVariable long id, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME_HARBOR_READ, "没有查看锚地的权限");
        return ApiResponse.success(service.getAnchorage(id), request);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新建锚地")
    public ApiResponse<AnchorageView> create(@Valid @RequestBody WriteAnchorage input, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME__ANCHORAGE_CREATE, "没有新建锚地的权限");
        return ApiResponse.success(service.createAnchorage(input, null), request);
    }
    @PutMapping("/{id}")
    @Operation(summary = "编辑或启停锚地，需最新版本")
    public ApiResponse<AnchorageView> update(@PathVariable long id, @Valid @RequestBody WriteAnchorage input, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME__ANCHORAGE_UPDATE, "没有维护锚地的权限");
        return ApiResponse.success(service.updateAnchorage(id, input), request);
    }
    @DeleteMapping("/{id}")
    @Operation(summary = "删除未被引用的锚地，需最新版本")
    public ApiResponse<Void> delete(@PathVariable long id, @RequestParam int version, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME__ANCHORAGE_DELETE, "没有删除锚地的权限");
        service.deleteAnchorage(id, version);
        return ApiResponse.success(null, request);
    }
}
