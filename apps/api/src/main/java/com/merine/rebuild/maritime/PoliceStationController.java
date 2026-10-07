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
@RequestMapping("/api/maritime/police-stations")
@Tag(name = "涉海档案 · 派出所")
public class PoliceStationController {
    private final MaritimeService service;
    private final PermissionGuard guard;
    public PoliceStationController(MaritimeService service, PermissionGuard guard) {
        this.service = service; this.guard = guard;
    }
    @GetMapping
    @Operation(summary = "分页查询派出所")
    public ApiResponse<PageResult<PoliceStationView>> list(Authentication authentication,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME_POLICING_READ, "没有查看派出所的权限");
        return ApiResponse.success(service.listPoliceStation(MaritimeService.query(keyword, region, status, null, null, null, null, page, pageSize)), request);
    }
    @GetMapping("/{id}")
    @Operation(summary = "查询派出所完整档案")
    public ApiResponse<PoliceStationView> detail(@PathVariable long id, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME_POLICING_READ, "没有查看派出所的权限");
        return ApiResponse.success(service.getPoliceStation(id), request);
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新建派出所")
    public ApiResponse<PoliceStationView> create(@Valid @RequestBody WritePoliceStation input, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME__POLICE_STATION_CREATE, "没有新建派出所的权限");
        return ApiResponse.success(service.createPoliceStation(input, null), request);
    }
    @PutMapping("/{id}")
    @Operation(summary = "编辑或启停派出所，需最新版本")
    public ApiResponse<PoliceStationView> update(@PathVariable long id, @Valid @RequestBody WritePoliceStation input, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME__POLICE_STATION_UPDATE, "没有维护派出所的权限");
        return ApiResponse.success(service.updatePoliceStation(id, input), request);
    }
    @DeleteMapping("/{id}")
    @Operation(summary = "删除未被引用的派出所，需最新版本")
    public ApiResponse<Void> delete(@PathVariable long id, @RequestParam int version, Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME__POLICE_STATION_DELETE, "没有删除派出所的权限");
        service.deletePoliceStation(id, version);
        return ApiResponse.success(null, request);
    }
}
