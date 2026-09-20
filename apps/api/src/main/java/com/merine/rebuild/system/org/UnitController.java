package com.merine.rebuild.system.org;

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
 * 单位列表：供用户管理的筛选与表单选择使用。
 * 本轮没有单位的增删改用例，因此只有查询；授权沿用用户管理这道门。
 */
@RestController
@RequestMapping("/api/system/units")
@Tag(name = "单位", description = "单位查询，供用户管理选择所属单位")
public class UnitController {
    private final UnitLookup units;
    private final UserAdminGuard guard;

    public UnitController(UnitLookup units, UserAdminGuard guard) {
        this.units = units;
        this.guard = guard;
    }

    @GetMapping
    @Operation(summary = "查询全部单位")
    public ApiResponse<List<UnitSummary>> list(Authentication authentication,
                                               HttpServletRequest request) {
        guard.require(authentication);
        return ApiResponse.success(units.listAll(), request);
    }
}
