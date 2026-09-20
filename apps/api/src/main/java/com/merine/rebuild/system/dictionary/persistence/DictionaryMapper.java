package com.merine.rebuild.system.dictionary.persistence;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 字典类型与字典项的读写；多表查询在 mapper/system/dictionary/DictionaryMapper.xml。
 *
 * 只提供「新增与修改」：字典类型与字典项按约定只停用、不删除，
 * 因为删掉取值会让页面标签与选择项立刻退化，而停用已经够表达"不再可选"。
 */
@Mapper
public interface DictionaryMapper {

    List<DictionaryTypeRow> findAllTypes();

    DictionaryTypeRow findByCode(@Param("code") String code);

    void insertType(@Param("code") String code,
                    @Param("name") String name,
                    @Param("description") String description);

    int updateType(@Param("id") long id,
                   @Param("name") String name,
                   @Param("description") String description,
                   @Param("status") String status,
                   @Param("version") int version);

    List<DictionaryItemRow> findItemsByTypeIds(@Param("typeIds") Collection<Long> typeIds);

    DictionaryItemRow findItem(@Param("typeId") long typeId, @Param("value") String value);

    void insertItem(@Param("typeId") long typeId,
                    @Param("value") String value,
                    @Param("label") String label,
                    @Param("description") String description,
                    @Param("sortOrder") int sortOrder);

    int updateItem(@Param("id") long id,
                   @Param("label") String label,
                   @Param("description") String description,
                   @Param("sortOrder") int sortOrder,
                   @Param("status") String status,
                   @Param("version") int version);
}
