package com.merine.rebuild.agent.semantics;

import com.merine.rebuild.agent.semantics.dto.SemanticCatalogView;
import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.system.security.PermissionCodes;
import com.merine.rebuild.system.security.PermissionGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent/semantics")
@Tag(name = "海防助手 · 业务定义", description = "只读业务概念与指标口径，不提供业务数据或统计执行")
final class SemanticCatalogController {
    private final SemanticCatalogService service;
    private final PermissionGuard guard;

    SemanticCatalogController(SemanticCatalogService service, PermissionGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    @Operation(summary = "读取资料库业务概念与指标定义")
    ApiResponse<SemanticCatalogView> get(Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.CHAT_USE, "没有查看海防助手资料库的权限");
        return ApiResponse.success(service.catalog(), request);
    }
}
