package com.merine.rebuild.system.dictionary.persistence;

import java.time.Instant;

/** 字典项的数据库投射。 */
public record DictionaryItemRow(
        long id,
        long typeId,
        String value,
        String label,
        String description,
        int sortOrder,
        String status,
        int version,
        Instant updatedAt) {
}
