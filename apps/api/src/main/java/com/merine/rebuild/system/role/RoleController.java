package com.merine.rebuild.system.role;

import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.system.user.UserAdminGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 角色列表：供用户管理的筛选与表单选择使用。
 * 本轮没有角色定义的增删改用例，因此只有查询；授权沿用用户管理这道门。
 */
@RestController
@RequestMapping("/api/system/roles")
@Tag(name = "角色", description = "角色查询，供用户管理选择与筛选")
public class RoleController {
    private final RoleLookup roles;
    private final UserAdminGuard guard;

    public RoleController(RoleLookup roles, UserAdminGuard guard) {
        this.roles = roles;
        this.guard = guard;
    }

    @GetMapping
    @Operation(summary = "查询全部角色")
    public ApiResponse<List<RoleSummary>> list(Authentication authentication,
                                               HttpServletRequest request) {
        guard.require(authentication);
        return ApiResponse.success(roles.listAll(), request);
    }
}
