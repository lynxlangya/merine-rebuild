package com.merine.rebuild.system.dictionary.persistence;

import java.time.Instant;

/** 字典类型的数据库投射。 */
public record DictionaryTypeRow(
        long id,
        String code,
        String name,
        String description,
        String status,
        int version,
        Instant updatedAt,
        int itemCount) {
}
