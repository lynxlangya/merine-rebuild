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
@RequestMapping("/api/maritime/islands")
@Tag(name = "涉海档案 · 海岛")
public class IslandController {
    private final MaritimeService service;
    private final PermissionGuard guard;
    public IslandController(MaritimeService service, PermissionGuard guard) {
        this.service = service; this.guard = guard;
    }
    @GetMapping
    @Operation(summary = "分页查询海岛")
    public ApiResponse<PageResult<IslandView>> list(Authentication authentication,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String inhabitationType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME_ISLAND_READ, "没有查看海岛的权限");
        return ApiResponse.success(service.listIsland(MaritimeService.query(keyword, region, status, null, null, null, inhabitationType, page, pageSize)), request);
    }
    @GetMapping("/{id}")
    @Operation(summary = "查询海岛完整档案")
    public ApiResponse<IslandView> detail(@PathVariable long id, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME_ISLAND_READ, "没有查看海岛的权限");
        return ApiResponse.success(service.getIsland(id), request);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新建海岛")
    public ApiResponse<IslandView> create(@Valid @RequestBody WriteIsland input, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME__ISLAND_CREATE, "没有新建海岛的权限");
        return ApiResponse.success(service.createIsland(input, null), request);
    }
    @PutMapping("/{id}")
    @Operation(summary = "编辑或启停海岛，需最新版本")
    public ApiResponse<IslandView> update(@PathVariable long id, @Valid @RequestBody WriteIsland input, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME__ISLAND_UPDATE, "没有维护海岛的权限");
        return ApiResponse.success(service.updateIsland(id, input), request);
    }
    @DeleteMapping("/{id}")
    @Operation(summary = "删除未被引用的海岛，需最新版本")
    public ApiResponse<Void> delete(@PathVariable long id, @RequestParam int version, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME__ISLAND_DELETE, "没有删除海岛的权限");
        service.deleteIsland(id, version);
        return ApiResponse.success(null, request);
    }
}
