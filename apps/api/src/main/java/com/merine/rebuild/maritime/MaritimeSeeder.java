package com.merine.rebuild.maritime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("seed-maritime")
public class MaritimeSeeder implements CommandLineRunner {
    private static final Logger log=LoggerFactory.getLogger(MaritimeSeeder.class);
    private final ConfigurableApplicationContext context;
    private final MaritimeFixtureInitializer initializer;
    public MaritimeSeeder(ConfigurableApplicationContext context, MaritimeFixtureInitializer initializer) {
        this.context=context; this.initializer=initializer;
    }
    @Override public void run(String... args) {
        System.exit(SpringApplication.exit(context, () -> {
            try { log.info("涉海样例新增 {} 条；已有档案保持原样", initializer.initialize()); return 0; }
            catch (RuntimeException error) { log.error("涉海样例填充失败：{}", error.getMessage()); return 1; }
        }));
    }
}
