package com.merine.rebuild.system.permission;

import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * system.permission 对外的只读能力：权限字典与全部权限码。
 *
 * 消费者：登录时把内置管理员角色展开为全部权限码；菜单与角色模块校验权限码是否存在。
 */
@Service
public class PermissionLookup {

    private final PermissionMapper mapper;

    public PermissionLookup(PermissionMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<PermissionSummary> listAll() {
        return mapper.findAll();
    }

    @Transactional(readOnly = true)
    public List<String> listAllCodes() {
        return mapper.findAllCodes();
    }

    @Transactional(readOnly = true)
    public List<PermissionSummary> findByCodes(Collection<String> codes) {
        return codes == null || codes.isEmpty() ? List.of() : mapper.findByCodes(codes);
    }
}
