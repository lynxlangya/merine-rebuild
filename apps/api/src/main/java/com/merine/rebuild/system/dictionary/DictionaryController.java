package com.merine.rebuild.system.dictionary;

import com.merine.rebuild.common.ApiResponse;
import com.merine.rebuild.system.dictionary.dto.DictionaryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 使用侧字典读取：登录即可，不占权限码。
 *
 * 字典是参考数据，不是敏感业务数据。如果读字典也要单独授权，每个用到字典的角色都得额外配一次权，
 * 页面就会出现「能进去、下拉是空的」这种难排查的状态；写字典才需要 system:dict:* 权限。
 */
@RestController
@RequestMapping("/api/dictionaries")
@Tag(name = "字典", description = "读取字典与字典项（登录即可）")
public class DictionaryController {

    private final DictionaryLookup lookup;

    public DictionaryController(DictionaryLookup lookup) {
        this.lookup = lookup;
    }

    @GetMapping
    @Operation(summary = "批量读取字典；不传 codes 时返回全部字典（含停用项）")
    public ApiResponse<List<DictionaryView>> list(@RequestParam(required = false) String codes,
                                                  HttpServletRequest request) {
        return ApiResponse.success(lookup.find(parseCodes(codes)), request);
    }

    private static List<String> parseCodes(String codes) {
        if (codes == null || codes.isBlank()) {
            return List.of();
        }
        return Arrays.stream(codes.split(","))
                .map(String::strip)
                .filter(code -> !code.isEmpty())
                .distinct()
                .toList();
    }
}
