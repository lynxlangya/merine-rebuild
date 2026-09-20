package com.merine.rebuild.system.org;

import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.system.user.UserAdminGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 单位管理与单位选项查询。
 *
 * 列表接口供用户管理选择所属单位；树与写接口供单位管理菜单使用。
 * 菜单和按钮只改善体验，所有接口都由系统管理门禁在后端判定。
 */
@RestController
@RequestMapping("/api/system/units")
@Tag(name = "单位管理", description = "组织树查询、新增、编辑与删除")
public class UnitController {
    private final UnitLookup units;
    private final UnitAdminService admin;
    private final UserAdminGuard guard;

    public UnitController(UnitLookup units, UnitAdminService admin, UserAdminGuard guard) {
        this.units = units;
        this.admin = admin;
        this.guard = guard;
    }

    @GetMapping
    @Operation(summary = "查询全部单位选项")
    public ApiResponse<List<UnitSummary>> list(Authentication authentication,
                                               HttpServletRequest request) {
        guard.require(authentication, "没有管理系统单位的权限");
        return ApiResponse.success(units.listAll(), request);
    }

    @GetMapping("/tree")
    @Operation(summary = "查询完整单位组织树")
    public ApiResponse<List<UnitTreeNode>> tree(Authentication authentication,
                                                HttpServletRequest request) {
        guard.require(authentication, "没有管理系统单位的权限");
        return ApiResponse.success(admin.tree(), request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增单位")
    public ApiResponse<UnitView> create(@Valid @RequestBody UnitRequests.CreateUnit input,
                                        Authentication authentication,
                                        HttpServletRequest request) {
        guard.require(authentication, "没有管理系统单位的权限");
        return ApiResponse.success(admin.create(input), request);
    }

    @PutMapping("/{code}")
    @Operation(summary = "编辑单位名称、上级或行政区划")
    public ApiResponse<UnitView> update(@PathVariable String code,
                                        @Valid @RequestBody UnitRequests.UpdateUnit input,
                                        Authentication authentication,
                                        HttpServletRequest request) {
        guard.require(authentication, "没有管理系统单位的权限");
        return ApiResponse.success(admin.update(code, input), request);
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "删除无下级、无用户的单位")
    public ApiResponse<Void> delete(@PathVariable String code,
                                    Authentication authentication,
                                    HttpServletRequest request) {
        guard.require(authentication, "没有管理系统单位的权限");
        admin.delete(code);
        return ApiResponse.success(null, request);
    }
}
