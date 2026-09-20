package com.merine.rebuild.system.role;

import com.merine.rebuild.system.role.persistence.RoleMapper;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * system.role 对外提供的角色-权限写命令。
 *
 * sys_role_permission 的写入 owner 是 role 模块：菜单管理删除节点（连带删权限码）时，
 * 必须由这里解除授权并交回「哪些角色受影响」，再由调用方让这些角色的持有者会话失效；
 * 菜单模块不直接写这张表。
 */
@Service
public class RolePermissionCommands {

    private final RoleMapper mapper;

    public RolePermissionCommands(RoleMapper mapper) {
        this.mapper = mapper;
    }

    /** 只查不写：给删除预览与删除回执用，回答「哪些角色会因此失去授权」。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> roleCodesGrantedPermissionIds(Collection<Long> permissionIds) {
        return permissionIds == null || permissionIds.isEmpty()
                ? List.of()
                : mapper.findRoleCodesByPermissionIds(permissionIds);
    }

    /** 解除这批权限码上的全部授予关系，返回受影响的角色编码。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<String> removeGrantsByPermissionIds(Collection<Long> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return List.of();
        }
        List<String> affected = mapper.findRoleCodesByPermissionIds(permissionIds);
        mapper.deleteGrantsByPermissionIds(permissionIds);
        return affected;
    }
}
