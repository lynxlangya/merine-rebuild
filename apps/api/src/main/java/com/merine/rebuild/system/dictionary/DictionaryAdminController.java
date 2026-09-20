package com.merine.rebuild.system.dictionary;

import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.system.dictionary.dto.DictionaryItemView;
import com.merine.rebuild.system.dictionary.dto.DictionaryListItem;
import com.merine.rebuild.system.dictionary.dto.DictionaryRequests;
import com.merine.rebuild.system.dictionary.dto.DictionaryView;
import com.merine.rebuild.system.security.PermissionCodes;
import com.merine.rebuild.system.security.PermissionGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典管理接口：查看用 system:dict:read，新建用 system:dict:create，
 * 编辑与启停用 system:dict:update。
 *
 * 字典类型与字典项都**只停用、不删除**：字典是全局参考数据，删掉取值会让页面标签
 * 与选择项立刻退化，误删的代价远大于收益，停用已经够表达"不再可选"。
 */
@RestController
@RequestMapping("/api/system/dictionaries")
@Tag(name = "字典管理", description = "字典类型与字典项维护；取值不可改，只停用不删除")
public class DictionaryAdminController {

    private final DictionaryService service;
    private final PermissionGuard guard;

    public DictionaryAdminController(DictionaryService service, PermissionGuard guard) {
        this.service = service;
        this.guard = guard;
    }

    @GetMapping
    @Operation(summary = "查询字典类型列表")
    public ApiResponse<List<DictionaryListItem>> list(Authentication authentication,
                                                      HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.DICT_READ, "没有查看字典的权限");
        return ApiResponse.success(service.list(), request);
    }

    @GetMapping("/{code}")
    @Operation(summary = "查询字典详情（含全部字典项与停用项）")
    public ApiResponse<DictionaryView> detail(@PathVariable String code,
                                              Authentication authentication,
                                              HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.DICT_READ, "没有查看字典的权限");
        return ApiResponse.success(service.get(code), request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新建字典类型")
    public ApiResponse<DictionaryView> create(
            @Valid @RequestBody DictionaryRequests.CreateDictionary input,
            Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.DICT_CREATE, "没有新建字典的权限");
        return ApiResponse.success(service.create(input), request);
    }

    @PutMapping("/{code}")
    @Operation(summary = "编辑字典名称、说明与状态")
    public ApiResponse<DictionaryView> update(
            @PathVariable String code,
            @Valid @RequestBody DictionaryRequests.UpdateDictionary input,
            Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.DICT_UPDATE, "没有编辑字典的权限");
        return ApiResponse.success(service.update(code, input), request);
    }

    @PostMapping("/{code}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增字典项（取值创建后不可修改）")
    public ApiResponse<DictionaryItemView> createItem(
            @PathVariable String code,
            @Valid @RequestBody DictionaryRequests.CreateDictionaryItem input,
            Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.DICT_CREATE, "没有新建字典的权限");
        return ApiResponse.success(service.createItem(code, input), request);
    }

    @PutMapping("/{code}/items/{value}")
    @Operation(summary = "编辑字典项标签、说明、排序与状态")
    public ApiResponse<DictionaryItemView> updateItem(
            @PathVariable String code,
            @PathVariable String value,
            @Valid @RequestBody DictionaryRequests.UpdateDictionaryItem input,
            Authentication authentication, HttpServletRequest request) {
        guard.require(authentication, PermissionCodes.DICT_UPDATE, "没有编辑字典的权限");
        return ApiResponse.success(service.updateItem(code, value, input), request);
    }

}
