package com.merine.rebuild.system.dictionary;

import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.system.dictionary.dto.DictionaryItemView;
import com.merine.rebuild.system.dictionary.dto.DictionaryView;
import com.merine.rebuild.system.dictionary.persistence.DictionaryItemRow;
import com.merine.rebuild.system.dictionary.persistence.DictionaryMapper;
import com.merine.rebuild.system.dictionary.persistence.DictionaryTypeRow;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * system.dictionary 对外的只读能力：按编码批量取字典（使用侧语义）。
 *
 * 语义约定：
 *   * 不传 codes 时返回全部字典（前端会话级预取就走这条）；
 *   * 传了 codes 而其中有不存在的编码 → 400，拼错字典名要立刻可见，而不是静默给空下拉；
 *   * 被停用的字典返回空字典项列表（使用侧据此禁用选择），
 *     启用字典则返回**全部字典项（含停用项）**，让历史数据仍能解析出标签。
 *
 * 注意「停用即空项」只属于使用侧：管理端详情（{@link DictionaryService#get}）要能继续维护
 * 停用字典的字典项，因此直接调包内的 {@link #toViews}，不走这里。
 */
@Service
public class DictionaryLookup {

    private final DictionaryMapper mapper;

    public DictionaryLookup(DictionaryMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<DictionaryView> find(Collection<String> requestedCodes) {
        return toConsumerViews(toViews(resolveTypes(requestedCodes)));
    }

    /** 解析请求的编码：空集合表示全部；有编码不存在就直接 400，不静默给空下拉。 */
    private List<DictionaryTypeRow> resolveTypes(Collection<String> requestedCodes) {
        if (requestedCodes == null || requestedCodes.isEmpty()) {
            return mapper.findAllTypes();
        }
        List<DictionaryTypeRow> types = new ArrayList<>();
        for (String code : requestedCodes) {
            DictionaryTypeRow row = mapper.findByCode(code);
            if (row == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_DICTIONARY",
                        "字典不存在：" + code);
            }
            types.add(row);
        }
        return types;
    }

    /**
     * 使用侧视图：停用的字典不下发字典项（选择器自然没有可选值），
     * 但字典本身仍返回，前端才能把历史数据里的取值标成「已停用」而不是退化成裸值。
     */
    private static List<DictionaryView> toConsumerViews(List<DictionaryView> views) {
        return views.stream().map(DictionaryLookup::toConsumerView).toList();
    }

    private static DictionaryView toConsumerView(DictionaryView view) {
        if ("ENABLED".equals(view.status())) {
            return view;
        }
        return new DictionaryView(view.code(), view.name(), view.description(), view.status(),
                view.version(), view.updatedAt(), List.of());
    }

    /** 批量把类型行组装成字典视图：一次取回全部字典项，避免逐字典查询。 */
    List<DictionaryView> toViews(List<DictionaryTypeRow> types) {
        if (types.isEmpty()) {
            return List.of();
        }
        Map<Long, List<DictionaryItemRow>> itemsByType = new LinkedHashMap<>();
        for (DictionaryItemRow item : mapper.findItemsByTypeIds(
                types.stream().map(DictionaryTypeRow::id).toList())) {
            itemsByType.computeIfAbsent(item.typeId(), ignored -> new ArrayList<>()).add(item);
        }
        return types.stream()
                .map(type -> toView(type, itemsByType.getOrDefault(type.id(), List.of())))
                .toList();
    }

    /** 管理端视图：无论启停都带上全部字典项（含停用项），停用后仍要能继续维护。 */
    static DictionaryView toView(DictionaryTypeRow type, List<DictionaryItemRow> items) {
        return new DictionaryView(type.code(), type.name(), type.description(), type.status(),
                type.version(), type.updatedAt(),
                items.stream().map(DictionaryLookup::toItemView).toList());
    }

    static DictionaryItemView toItemView(DictionaryItemRow item) {
        return new DictionaryItemView(item.value(), item.label(), item.description(),
                item.sortOrder(), item.status(), item.version());
    }
}
