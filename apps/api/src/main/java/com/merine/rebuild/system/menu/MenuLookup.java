package com.merine.rebuild.system.menu;

import com.merine.rebuild.system.menu.dto.MenuNode;
import com.merine.rebuild.system.menu.persistence.MenuMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * system.menu 对外的只读能力：完整菜单树。
 *
 * 角色模块用它渲染权限勾选树（页面/页签/按钮带权限码，目录只是分组），
 * 菜单管理页与导航接口也基于同一份数据，避免三处各写一套层级口径。
 */
@Service
public class MenuLookup {

    private final MenuMapper mapper;

    public MenuLookup(MenuMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<MenuNode> tree() {
        return MenuTree.build(mapper.findAll());
    }

    /** 已被菜单节点引用的权限码；角色权限树用它识别「未挂在菜单上」的码。 */
    @Transactional(readOnly = true)
    public List<String> listBoundPermissionCodes() {
        return mapper.findBoundPermissionCodes();
    }
}
