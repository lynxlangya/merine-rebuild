package com.merine.rebuild.system.audit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 审计模块注册表（代码侧清单）。
 *
 * 模块维度与权限命名空间一致（`<模块>:<资源>:<动作>`），另加身份与初始化两个不占权限模块的码。
 * 登记在这里的名字用于审计页面筛选与展示；回归会断言库里出现过的模块都已登记。
 *
 * 任务与信息流转**不在其中**：它们的业务表本身就是留痕，审计不复制（见设计稿 §1 决策 7）。
 */
public final class AuditModules {

    public record Module(String code, String name, int sortOrder) {}

    private static final List<Module> ALL = List.of(
            new Module("system", "系统管理", 100),
            new Module("maritime", "涉海要素", 200),
            new Module("agent", "模型连接", 300),
            new Module("auth", "登录与退出", 400),
            new Module("bootstrap", "初始化", 500));

    private static final Map<String, Module> BY_CODE = index();

    private AuditModules() {}

    public static Optional<Module> find(String code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }

    public static String nameOf(String code) {
        return find(code).map(Module::name).orElse(code);
    }

    /** 供回归使用：库里出现过的模块都必须登记。 */
    public static boolean isRegistered(String code) {
        return BY_CODE.containsKey(code);
    }

    public static List<Module> all() {
        return ALL;
    }

    private static Map<String, Module> index() {
        Map<String, Module> index = new LinkedHashMap<>();
        ALL.forEach(module -> index.put(module.code(), module));
        return Map.copyOf(index);
    }
}
