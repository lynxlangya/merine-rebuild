package com.merine.rebuild.maritime;

import com.merine.rebuild.common.*;
import com.merine.rebuild.maritime.dto.ArchiveOption;
import com.merine.rebuild.system.security.*;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** 跨页面最小候选项；完整警务档案仍受 policing:read 保护。 */
@RestController
@RequestMapping("/api/maritime/options")
public class MaritimeOptionsController {
    private final MaritimeService service;
    private final PermissionGuard guard;
    public MaritimeOptionsController(MaritimeService service, PermissionGuard guard) { this.service=service; this.guard=guard; }
    @GetMapping("/ports")
    public ApiResponse<PageResult<ArchiveOption>> ports(Authentication authentication,
            @RequestParam(required=false) String keyword, @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int pageSize, HttpServletRequest request) {
        allowed(authentication);
        return ApiResponse.success(service.portOptions(MaritimeService.query(keyword,null,"ENABLED",null,null,null,null,page,pageSize)), request);
    }
    @GetMapping("/police-stations")
    public ApiResponse<PageResult<ArchiveOption>> stations(Authentication authentication,
            @RequestParam(required=false) String keyword, @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="20") int pageSize, HttpServletRequest request) {
        allowed(authentication);
        return ApiResponse.success(service.stationOptions(MaritimeService.query(keyword,null,"ENABLED",null,null,null,null,page,pageSize)), request);
    }
    @GetMapping("/port-officers")
    public ApiResponse<PageResult<ArchiveOption>> officers(Authentication authentication,
            @RequestParam(required=false) String keyword, @RequestParam(required=false) Long policeStationId,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize, HttpServletRequest request) {
        allowed(authentication);
        return ApiResponse.success(service.officerOptions(MaritimeService.query(keyword,null,"ENABLED",null,policeStationId,null,null,page,pageSize)), request);
    }
    @GetMapping("/wharf-relations")
    public ApiResponse<PageResult<com.merine.rebuild.maritime.dto.WharfRelationView>> wharfs(Authentication authentication,
            @RequestParam(required=false) Long policeStationId, @RequestParam(required=false) Long responsibleOfficerId,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.MARITIME_POLICING_READ, "没有读取警务责任关系的权限");
        return ApiResponse.success(service.wharfRelations(MaritimeService.query(null,null,null,null,policeStationId,responsibleOfficerId,null,page,pageSize)), request);
    }
    @GetMapping("/officer-users")
    public ApiResponse<PageResult<com.merine.rebuild.maritime.dto.OfficerUserOption>> officerUsers(Authentication authentication,
            @RequestParam long policeStationId, @RequestParam(required=false) String keyword,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize, HttpServletRequest request) {
        guard.requireAny(authentication,List.of(PermissionCodes.MARITIME__PORT_OFFICER_CREATE,PermissionCodes.MARITIME__PORT_OFFICER_UPDATE),"没有维护派出所民警的权限");
        return ApiResponse.success(service.officerUserOptions(policeStationId,keyword,page,pageSize),request);
    }
    private void allowed(Authentication authentication) {
        guard.requireAny(authentication, List.of(PermissionCodes.MARITIME_HARBOR_READ,PermissionCodes.MARITIME_POLICING_READ), "没有读取关联候选项的权限");
    }
}
