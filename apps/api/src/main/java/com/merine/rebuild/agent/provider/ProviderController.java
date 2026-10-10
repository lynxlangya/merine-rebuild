package com.merine.rebuild.agent.provider;

import com.merine.rebuild.agent.provider.dto.EffortCatalog;
import com.merine.rebuild.agent.provider.dto.ModelCatalog;
import com.merine.rebuild.agent.provider.dto.ProviderRequests.Discover;
import com.merine.rebuild.agent.provider.dto.ProviderRequests.EffortLookup;
import com.merine.rebuild.agent.provider.dto.ProviderRequests.Save;
import com.merine.rebuild.agent.provider.dto.ProviderView;
import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.common.PageResult;
import com.merine.rebuild.system.security.PermissionCodes;
import com.merine.rebuild.system.security.PermissionGuard;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/agent/providers")
@Tag(name = "模型供应商", description = "配置管理；密钥只写不读，仅「获取模型列表」会带密钥出站")
public class ProviderController {
    private final ProviderService service;
    private final PermissionGuard guard;

    public ProviderController(ProviderService service, PermissionGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    public ApiResponse<PageResult<ProviderView>> list(
            @RequestParam(defaultValue = "") @Size(max = 80) String search,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(60) int pageSize,
            Authentication auth, HttpServletRequest request) {
        guard.require(auth, PermissionCodes.PROVIDER_READ, "没有查看模型供应商的权限");
        return ApiResponse.success(service.list(search, page, pageSize), request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProviderView> create(@Valid @RequestBody Save input,
                                            Authentication auth, HttpServletRequest request) {
        guard.require(auth, PermissionCodes.PROVIDER_CREATE, "没有新增模型供应商的权限");
        return ApiResponse.success(service.create(input), request);
    }

    @PutMapping("/{id}")
    public ApiResponse<ProviderView> update(@PathVariable String id, @Valid @RequestBody Save input,
                                            Authentication auth, HttpServletRequest request) {
        guard.require(auth, PermissionCodes.PROVIDER_UPDATE, "没有编辑模型供应商的权限");
        return ApiResponse.success(service.update(id, input), request);
    }

    /** 批量查推理强度目录：配置页展示用，读权限即可。 */
    @PostMapping("/reasoning-efforts")
    public ApiResponse<EffortCatalog> reasoningEfforts(@Valid @RequestBody EffortLookup input,
                                                       Authentication auth, HttpServletRequest request) {
        guard.require(auth, PermissionCodes.PROVIDER_READ, "没有查看模型供应商的权限");
        return ApiResponse.success(service.effortCatalog(input.vendor(), input.modelIds()), request);
    }

    /** 读取上游模型清单：新建场景要新增权限，已保存的连接要编辑权限。 */
    @PostMapping("/models/discover")
    public ApiResponse<ModelCatalog> discover(@Valid @RequestBody Discover input,
                                              Authentication auth, HttpServletRequest request) {
        guard.require(auth,
                input.providerId() == null ? PermissionCodes.PROVIDER_CREATE : PermissionCodes.PROVIDER_UPDATE,
                "没有获取模型列表的权限");
        return ApiResponse.success(service.discover(input), request);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id, @RequestParam @Min(0) int version,
                                    Authentication auth, HttpServletRequest request) {
        guard.require(auth, PermissionCodes.PROVIDER_DELETE, "没有删除模型供应商的权限");
        service.delete(id, version);
        return ApiResponse.success(null, request);
    }
}
