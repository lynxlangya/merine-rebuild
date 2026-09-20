package com.merine.rebuild.system.dictionary;

import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.system.dictionary.dto.DictionaryItemView;
import com.merine.rebuild.system.dictionary.dto.DictionaryListItem;
import com.merine.rebuild.system.dictionary.dto.DictionaryRequests;
import com.merine.rebuild.system.dictionary.dto.DictionaryView;
import com.merine.rebuild.system.dictionary.persistence.DictionaryItemRow;
import com.merine.rebuild.system.dictionary.persistence.DictionaryMapper;
import com.merine.rebuild.system.dictionary.persistence.DictionaryTypeRow;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 字典管理用例。
 *
 * 硬规则：取值（dict_code / item_value）是稳定标识，创建后不可修改——业务数据与代码判定
 * 比较的都是这个值，改值等于改语义。字典类型与字典项都只停用、不删除：字典是全局参考数据，
 * 误删会让页面标签与选择项立刻退化，停用已经够表达"不再可选"，历史数据也仍能解析标签。
 */
@Service
public class DictionaryService {

    private final DictionaryMapper mapper;
    private final DictionaryLookup lookup;

    public DictionaryService(DictionaryMapper mapper, DictionaryLookup lookup) {
        this.mapper = mapper;
        this.lookup = lookup;
    }

    @Transactional(readOnly = true)
    public List<DictionaryListItem> list() {
        return mapper.findAllTypes().stream()
                .map(row -> new DictionaryListItem(row.code(), row.name(), row.description(),
                        row.status(), row.version(), row.itemCount(), row.updatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DictionaryView get(String rawCode) {
        return lookup.toViews(List.of(requireType(normalizeCode(rawCode)))).getFirst();
    }

    @Transactional
    public DictionaryView create(DictionaryRequests.CreateDictionary request) {
        String code = request.code().strip();
        if (mapper.findByCode(code) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "DICT_CODE_TAKEN",
                    "该字典编码已存在：" + code);
        }
        try {
            mapper.insertType(code, request.name().strip(), blankToNull(request.description()));
        } catch (DuplicateKeyException error) {
            throw new ApiException(HttpStatus.CONFLICT, "DICT_CODE_TAKEN",
                    "该字典编码已存在：" + code);
        }
        return lookup.toViews(List.of(requireType(code))).getFirst();
    }

    @Transactional
    public DictionaryView update(String rawCode, DictionaryRequests.UpdateDictionary request) {
        String code = normalizeCode(rawCode);
        DictionaryTypeRow current = requireType(code);
        if (current.version() != request.version()) {
            throw versionConflict();
        }
        if (mapper.updateType(current.id(), request.name().strip(),
                blankToNull(request.description()), request.status(), request.version()) == 0) {
            throw versionConflict();
        }
        return lookup.toViews(List.of(requireType(code))).getFirst();
    }

    @Transactional
    public DictionaryItemView createItem(String rawCode,
                                         DictionaryRequests.CreateDictionaryItem request) {
        String code = normalizeCode(rawCode);
        DictionaryTypeRow type = requireType(code);
        String value = request.value().strip();
        try {
            mapper.insertItem(type.id(), value, request.label().strip(),
                    blankToNull(request.description()),
                    request.sortOrder() == null ? 0 : request.sortOrder());
        } catch (DuplicateKeyException error) {
            throw new ApiException(HttpStatus.CONFLICT, "DICT_ITEM_VALUE_TAKEN",
                    "该字典项取值已存在：" + value);
        }
        DictionaryItemRow created = mapper.findItem(type.id(), value);
        if (created == null) {
            throw new IllegalStateException("新建字典项后未读到插入结果：" + value);
        }
        return DictionaryLookup.toItemView(created);
    }

    @Transactional
    public DictionaryItemView updateItem(String rawCode, String rawValue,
                                         DictionaryRequests.UpdateDictionaryItem request) {
        String code = normalizeCode(rawCode);
        DictionaryTypeRow type = requireType(code);
        String value = rawValue == null ? "" : rawValue.strip();
        DictionaryItemRow current = mapper.findItem(type.id(), value);
        if (current == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DICT_ITEM_NOT_FOUND", "字典项不存在");
        }
        if (current.version() != request.version()
                || mapper.updateItem(current.id(), request.label().strip(),
                        blankToNull(request.description()), request.sortOrder(), request.status(),
                        request.version()) == 0) {
            throw itemVersionConflict();
        }
        return DictionaryLookup.toItemView(mapper.findItem(type.id(), value));
    }

    private DictionaryTypeRow requireType(String code) {
        DictionaryTypeRow row = mapper.findByCode(code);
        if (row == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DICT_NOT_FOUND", "字典不存在");
        }
        return row;
    }

    private static String normalizeCode(String rawCode) {
        return rawCode == null ? "" : rawCode.strip();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static ApiException versionConflict() {
        return new ApiException(HttpStatus.CONFLICT, "DICT_VERSION_CONFLICT",
                "该字典已被其他操作修改，请重新加载后核对并保存");
    }

    private static ApiException itemVersionConflict() {
        return new ApiException(HttpStatus.CONFLICT, "DICT_ITEM_VERSION_CONFLICT",
                "该字典项已被其他操作修改，请重新加载后核对并保存");
    }
}
