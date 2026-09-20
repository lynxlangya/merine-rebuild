package com.merine.rebuild.system.permission;

import com.merine.rebuild.common.ApiException;
import java.util.Collection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 权限码字典的写命令。
 *
 * 只有菜单管理会调用它：新增节点时建码、改节点名时同步权限名称、删除节点时删码。
 * 权限码本身创建后不可修改——它是判权用的 authority，也是角色授予关系的依据。
 * 这些方法都要求调用方已经开启事务（菜单节点与权限码必须一起成功），
 * 因此用 {@link Propagation#MANDATORY} 把「忘了事务」变成启动期就能发现的错误。
 */
@Service
public class PermissionCommands {

    private final PermissionMapper mapper;

    public PermissionCommands(PermissionMapper mapper) {
        this.mapper = mapper;
    }

    /** 新建权限码，返回新行主键；编码重复返回 409，不静默复用已有码。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public long create(String code, String name, String description) {
        try {
            mapper.insert(code, name, description);
        } catch (DuplicateKeyException error) {
            throw new ApiException(HttpStatus.CONFLICT, "PERMISSION_CODE_TAKEN",
                    "权限码已存在：" + code);
        }
        Long id = mapper.findIdByCode(code);
        if (id == null) {
            throw new IllegalStateException("新建权限码后未读到插入结果：" + code);
        }
        return id;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int rename(long id, String name, String description) {
        return mapper.updateName(id, name, description);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int deleteByIds(Collection<Long> ids) {
        return ids == null || ids.isEmpty() ? 0 : mapper.deleteByIds(ids);
    }
}
