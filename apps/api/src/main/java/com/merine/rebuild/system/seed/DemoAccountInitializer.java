package com.merine.rebuild.system.seed;

import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 演示账号的可重复初始化：缺什么建什么，已存在的账号原样保留。
 *
 * 单独成类而不是写成 runner 的私有方法：同类自调用不会经过 Spring 代理，
 * 注解上的事务不会生效，多张表的写入就会各写各的。
 */
@Service
@Profile("seed")
public class DemoAccountInitializer {
    private final DemoAccountMapper mapper;
    private final PasswordEncoder passwordEncoder;

    public DemoAccountInitializer(DemoAccountMapper mapper, PasswordEncoder passwordEncoder) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public DemoAccountSeeder.SeedOutcome initialize(DemoAccountSeeder.SeedRequest request) {
        long unitId = ensureUnit(request.unitCode(), request.unitName());
        long roleId = ensureRole(request.roleCode(), request.roleName());

        Long existingId = mapper.findUserId(request.loginName());
        boolean accountCreated = existingId == null;
        long userId;
        if (accountCreated) {
            // 密码在这里才编码；明文不落库、不写日志、不写迁移
            mapper.insertUser(request.loginName(), request.displayName(),
                    passwordEncoder.encode(request.password()), unitId);
            userId = mapper.findUserId(request.loginName());
        } else {
            userId = existingId;
        }

        boolean roleGranted = false;
        if (mapper.countUserRole(userId, roleId) == 0) {
            mapper.insertUserRole(userId, roleId);
            roleGranted = true;
        }
        return new DemoAccountSeeder.SeedOutcome(accountCreated, roleGranted, userId);
    }

    private long ensureUnit(String code, String name) {
        Long id = mapper.findUnitId(code);
        if (id != null) {
            return id;
        }
        mapper.insertUnit(code, name);
        return mapper.findUnitId(code);
    }

    private long ensureRole(String code, String name) {
        Long id = mapper.findRoleId(code);
        if (id != null) {
            return id;
        }
        mapper.insertRole(code, name);
        return mapper.findRoleId(code);
    }
}
