package com.merine.rebuild.system.menu.persistence;

import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 菜单资源树的读写。多表查询与批量删除在 mapper/system/menu/MenuMapper.xml。
 *
 * 写 path 只做行级操作；父子层级、类型约束、权限码约束都在 MenuService 里判定——
 * 菜单表只有几十行，一次读全树再校验比写一堆碎片 SQL 更清楚。
 */
@Mapper
public interface MenuMapper {

    List<MenuRow> findAll();

    MenuRow findById(@Param("id") long id);

    MenuRow findByRouteKey(@Param("routeKey") String routeKey);

    MenuRow findByPermissionCode(@Param("permissionCode") String permissionCode);

    void insert(@Param("parentId") Long parentId,
                @Param("type") String type,
                @Param("name") String name,
                @Param("routeKey") String routeKey,
                @Param("permissionId") Long permissionId,
                @Param("sortOrder") int sortOrder,
                @Param("status") String status,
                @Param("description") String description);

    int updateBasic(@Param("id") long id,
                    @Param("parentId") Long parentId,
                    @Param("name") String name,
                    @Param("routeKey") String routeKey,
                    @Param("sortOrder") int sortOrder,
                    @Param("status") String status,
                    @Param("description") String description,
                    @Param("version") int version);

    int deleteByIds(@Param("ids") Collection<Long> ids);

    /** 同事务内取刚插入的自增主键：菜单节点与权限码要在同一次请求里回读。 */
    @Select("SELECT LAST_INSERT_ID()")
    long lastInsertId();
}
