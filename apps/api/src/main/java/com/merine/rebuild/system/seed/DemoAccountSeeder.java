package com.merine.rebuild.system.seed;

import com.merine.rebuild.system.user.PasswordLimits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 本地演示账号初始化入口，只在 seed profile 下运行。
 *
 * 用法：./scripts/dev.sh seed（会交互式读取账号与密码，密码不回显、不进 shell 历史）。
 * 密码只以 BCrypt 哈希落库；本类不打印密码，日志里也没有它。
 * 重复执行是安全的：已有账号保持原样，不会被重置。
 */
@Component
@Profile("seed")
public class DemoAccountSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoAccountSeeder.class);

    /** 本地合成环境的密码下限，只挡住空密码和 1–5 位的输入。 */
    private static final int MIN_PASSWORD_LENGTH = 6;
    /** 低于这个长度只警告不阻止：本地演示账号常用 123456 这类密码。 */
    private static final int WARN_PASSWORD_LENGTH = 12;

    private final ConfigurableApplicationContext applicationContext;
    private final DemoAccountInitializer initializer;

    @Value("${SEED_LOGIN_NAME:}")
    private String loginName;
    @Value("${SEED_PASSWORD:}")
    private String password;
    @Value("${SEED_DISPLAY_NAME:陈知远}")
    private String displayName;
    @Value("${SEED_UNIT_CODE:ORG_001}")
    private String unitCode;
    @Value("${SEED_UNIT_NAME:浙江省公安厅海防总队}")
    private String unitName;
    @Value("${SEED_ROLE_CODE:SYSTEM_ADMIN}")
    private String roleCode;
    @Value("${SEED_ROLE_NAME:系统管理员}")
    private String roleName;

    public DemoAccountSeeder(ConfigurableApplicationContext applicationContext,
                             DemoAccountInitializer initializer) {
        this.applicationContext = applicationContext;
        this.initializer = initializer;
    }

    public record SeedRequest(String loginName, String password, String displayName,
                              String unitCode, String unitName, String roleCode, String roleName) {
    }

    public record SeedOutcome(boolean accountCreated, boolean roleGranted, long userId) {
    }

    @Override
    public void run(String... args) {
        System.exit(SpringApplication.exit(applicationContext, this::seedAndReport));
    }

    /** 返回进程退出码；写成方法是因为赋值两次的局部变量不再是 effectively final。 */
    private int seedAndReport() {
        try {
            SeedRequest request = validatedRequest();
            SeedOutcome outcome = initializer.initialize(request);
            if (outcome.accountCreated()) {
                log.info("已创建演示账号 {}（userId={}），密码以 BCrypt 哈希保存",
                        request.loginName(), outcome.userId());
            } else {
                log.info("演示账号 {} 已存在，保持原样：未重置密码，未改动归属与角色",
                        request.loginName());
            }
            if (outcome.roleGranted()) {
                log.info("已授予角色 {}", request.roleName());
            }
            return 0;
        } catch (RuntimeException error) {
            log.error("演示账号初始化失败：{}", error.getMessage());
            return 1;
        }
    }

    private SeedRequest validatedRequest() {
        if (loginName == null || loginName.isBlank()) {
            throw new IllegalStateException("缺少 SEED_LOGIN_NAME，无法确定演示账号");
        }
        // 与用户管理接口的账号规则保持一致：登录名是 ASCII 编码列，建出一个
        // 接口不接受、也登录不了的账号只会让人困惑
        if (!loginName.strip().matches("^[a-z0-9.]{4,32}$")) {
            throw new IllegalStateException(
                    "SEED_LOGIN_NAME 只能使用小写字母、数字与点，长度 4–32");
        }
        // 编码列同样是 ascii：中文编码既要不过比较，也写不进列，在入口就挡掉
        for (String[] code : new String[][] {{"SEED_UNIT_CODE", unitCode}, {"SEED_ROLE_CODE", roleCode}}) {
            if (!code[1].matches("^[A-Za-z0-9._-]{1,64}$")) {
                throw new IllegalStateException(
                        "%s 只能使用字母、数字与 . _ -，长度 1–64".formatted(code[0]));
            }
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "SEED_PASSWORD 至少 %d 个字符；密码只从环境读取，不写在代码或迁移里"
                            .formatted(MIN_PASSWORD_LENGTH));
        }
        if (password.length() < WARN_PASSWORD_LENGTH) {
            log.warn("演示账号密码长度不足 {} 位。这个密码只能用于本地合成数据，"
                            + "接入真实环境前必须改用单位密码策略下发放的凭据。",
                    WARN_PASSWORD_LENGTH);
        }
        PasswordLimits.requireSupportedLength(password, "password");
        return new SeedRequest(loginName.strip(), password, displayName,
                unitCode, unitName, roleCode, roleName);
    }
}
