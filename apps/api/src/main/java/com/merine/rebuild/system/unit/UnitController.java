package com.merine.rebuild.system.unit;

import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.system.security.PermissionCodes;
import com.merine.rebuild.system.security.PermissionGuard;
import com.merine.rebuild.system.unit.dto.UnitRequests;
import com.merine.rebuild.system.unit.dto.UnitSummary;
import com.merine.rebuild.system.unit.dto.UnitTreeNode;
import com.merine.rebuild.system.unit.dto.UnitView;
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
 * 菜单和按钮只改善体验，所有接口都由功能权限门禁在后端判定。
 */
@RestController
@RequestMapping("/api/system/units")
@Tag(name = "单位管理", description = "组织树查询、新增、编辑与删除")
public class UnitController {
    private final UnitLookup units;
    private final UnitService admin;
    private final PermissionGuard guard;

    public UnitController(UnitLookup units, UnitService admin, PermissionGuard guard) {
        this.units = units;
        this.admin = admin;
        this.guard = guard;
    }

    @GetMapping
    @Operation(summary = "查询全部单位选项")
    public ApiResponse<List<UnitSummary>> list(Authentication authentication,
                                               HttpServletRequest request) {
        // 单位选项同时服务单位管理页与用户表单：拥有任一相关权限即可读取，
        // 否则只做用户管理的人会因为没有单位管理权限而选不了单位。
        guard.requireAny(authentication,
                List.of(PermissionCodes.UNIT_READ, PermissionCodes.USER_READ),
                "没有查看单位选项的权限");
        return ApiResponse.success(units.listAll(), request);
    }

    @GetMapping("/tree")
    @Operation(summary = "查询完整单位组织树")
    public ApiResponse<List<UnitTreeNode>> tree(Authentication authentication,
                                                HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.UNIT_READ, "没有查看单位的权限");
        return ApiResponse.success(admin.tree(), request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增单位")
    public ApiResponse<UnitView> create(@Valid @RequestBody UnitRequests.CreateUnit input,
                                        Authentication authentication,
                                        HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.UNIT_CREATE, "没有新增单位的权限");
        return ApiResponse.success(admin.create(input), request);
    }

    @PutMapping("/{code}")
    @Operation(summary = "编辑单位名称、上级或行政区划")
    public ApiResponse<UnitView> update(@PathVariable String code,
                                        @Valid @RequestBody UnitRequests.UpdateUnit input,
                                        Authentication authentication,
                                        HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.UNIT_UPDATE, "没有编辑单位的权限");
        return ApiResponse.success(admin.update(code, input), request);
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "删除无下级、无用户的单位")
    public ApiResponse<Void> delete(@PathVariable String code,
                                    Authentication authentication,
                                    HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.UNIT_DELETE, "没有删除单位的权限");
        admin.delete(code);
        return ApiResponse.success(null, request);
    }
}
