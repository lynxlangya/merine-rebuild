package com.merine.rebuild.agent.provider;

import com.merine.rebuild.agent.provider.dto.ModelOption;
import com.merine.rebuild.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 使用侧模型读取：登录即可，不占权限码。
 *
 * 助手要让所有登录用户选择已启用的模型；若复用管理接口的 agent:provider:read，
 * 每个使用助手的角色都要额外配权，页面会出现「能进助手、模型列表是空的」这种难排查的状态；
 * 因此这里只返回已启用连接下的启用模型（标识、显示名与图标），不返回地址、备注和密钥。
 */
@RestController
@RequestMapping("/api/agent/models")
@Tag(name = "模型选项", description = "读取已启用的模型选项（登录即可）；配置管理需要 agent:provider:* 权限")
public class ModelOptionController {

    private final ProviderService service;

    public ModelOptionController(ProviderService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "读取已启用连接下的启用模型；不返回地址、备注与密钥")
    public ApiResponse<List<ModelOption>> list(HttpServletRequest request) {
        return ApiResponse.success(service.modelOptions(), request);
    }
}
