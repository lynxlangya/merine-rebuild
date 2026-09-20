package com.merine.rebuild.system.menu;

import com.merine.rebuild.system.menu.dto.RestoreMenusResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 恢复默认菜单的命令行入口，只在 menus profile 下运行（`./scripts/dev.sh menus`）。
 *
 * 为什么需要它：菜单管理允许把节点删干净，包括菜单管理自己的入口与「恢复默认菜单」这个权限码；
 * 那时页面上已经没有可点的恢复按钮，只能从命令行补齐引导菜单。恢复只补缺失项，不删除也不覆盖。
 */
@Component
@Profile("menus")
public class MenuRestoreRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MenuRestoreRunner.class);

    private final ConfigurableApplicationContext applicationContext;
    private final MenuService menuService;

    public MenuRestoreRunner(ConfigurableApplicationContext applicationContext,
                             MenuService menuService) {
        this.applicationContext = applicationContext;
        this.menuService = menuService;
    }

    @Override
    public void run(String... args) {
        System.exit(SpringApplication.exit(applicationContext, this::restoreAndReport));
    }

    private int restoreAndReport() {
        try {
            RestoreMenusResult result = menuService.restore();
            log.info("默认菜单补齐完成：新增菜单节点 {} 个、权限码 {} 个（已有内容保持原样）",
                    result.createdMenus(), result.createdPermissions());
            return 0;
        } catch (RuntimeException error) {
            log.error("默认菜单补齐失败：{}", error.getMessage());
            return 1;
        }
    }
}
