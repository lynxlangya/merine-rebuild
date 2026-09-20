package com.merine.rebuild.system.user;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.merine.rebuild.common.ApiExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 未预期错误（500）的日志回归。
 *
 * 这条链路上真正危险的不是状态码，而是日志内容：MyBatis / JDBC 的异常消息里会带上
 * 结果集里的字段值（例如 “Cannot convert string '演示单位甲' to ...”），
 * 因此 {@code log.error(..., error)} 等于把库里的业务数据写进日志文件。
 * {@link ApiExceptionHandler} 现在只在 ERROR 记异常类型与请求 URI，完整堆栈降级到 DEBUG。
 *
 * 为了让这条链路被真实走到，本用例用 {@link UnexpectedErrorEndpoint} 注册了一个
 * 只属于本测试类的端点：它先做一次真实查询，再抛出一个消息里带着该查询结果的非业务异常，
 * 等价于上面那类真实故障。其余部分（过滤器链、异常处理器、日志实现）都是产品代码本身。
 */
@DisplayName("未预期错误日志回归")
class UnexpectedErrorLogRegressionTest extends UserAdminRegressionSupport {

    /** 只在本用例的 Spring 上下文里注册的端点；不改变其他用例的上下文。 */
    @TestConfiguration
    static class UnexpectedErrorEndpoint {

        @Bean
        ThrowingProbeController throwingProbeController(JdbcTemplate jdbcTemplate) {
            return new ThrowingProbeController(jdbcTemplate);
        }
    }

    /**
     * 制造一次未预期错误：读一行真实数据，然后把读到的值放进异常消息里抛出。
     * 修复前这条消息会随堆栈整条打进 ERROR 日志。
     */
    @RestController
    static class ThrowingProbeController {

        static final String PATH = "/api/regression/unexpected-error";

        /** 最近一次抛出的异常消息，用来证明触发条件真的带着库里的值。 */
        static volatile String lastThrownMessage;

        private final JdbcTemplate jdbcTemplate;

        ThrowingProbeController(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @GetMapping(PATH)
        Object unexpectedError() {
            String displayName = jdbcTemplate.queryForObject(
                    "SELECT display_name FROM sys_user WHERE login_name = ?",
                    String.class, CHINESE_LOGIN);
            String message = "Cannot convert string '" + displayName + "' to java.time.Instant";
            lastThrownMessage = message;
            throw new IllegalStateException(message);
        }
    }

    @Test
    @DisplayName("未预期错误返回 500 INTERNAL_ERROR：ERROR 只记异常类型与请求 URI，库中字段值不出现在日志里")
    void unexpectedErrorKeepsTypeAndUriWithoutLoggingTheException() throws Exception {
        MockHttpSession admin = adminSession();
        assertThat(displayNameInDatabase(CHINESE_LOGIN))
                .as("用例前提：合成账号的姓名就是库里的字段值")
                .isEqualTo(CHINESE_DISPLAY);

        ListAppender<ILoggingEvent> appender = attachToRootLogger();
        MvcResult result;
        try {
            result = getJson(ThrowingProbeController.PATH, admin);
        } finally {
            detachFromRootLogger(appender);
        }

        assertError(result, 500, "INTERNAL_ERROR");
        assertThat(bodyOf(result))
                .as("500 响应体不得回显异常详情")
                .doesNotContain(CHINESE_DISPLAY)
                .doesNotContain("IllegalStateException");

        // 正对照：这次的异常消息确实取自库里的字段值。
        // 没有这条，下面的“日志里不出现它”只可能是触发条件没带上它，而不是日志做对了。
        assertThat(ThrowingProbeController.lastThrownMessage)
                .as("触发条件必须真的把库中字段值带进异常消息")
                .contains(CHINESE_DISPLAY);

        List<ILoggingEvent> errors = eventsOf(appender, Level.ERROR);
        assertThat(errors).as("未预期错误必须在 ERROR 上留有记录").isNotEmpty();
        ILoggingEvent handled = errors.stream()
                .filter(event -> ApiExceptionHandler.class.getName().equals(event.getLoggerName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("ApiExceptionHandler 没有在 ERROR 上记录任何内容"));
        assertThat(handled.getThrowableProxy())
                .as("ERROR 不得打印异常对象——它的消息里就带着库里的字段值")
                .isNull();
        assertThat(loggedText(appender))
                .as("捕获到的日志文本不得出现库中的字段值")
                .doesNotContain(CHINESE_DISPLAY)
                .doesNotContain(UNIT_ALPHA_NAME)
                .doesNotContain(passwordHashInDatabase(CHINESE_LOGIN));
        assertThat(handled.getFormattedMessage())
                .as("ERROR 仍要能定位问题：异常类型与请求路径")
                .contains("type=java.lang.IllegalStateException")
                .contains(ThrowingProbeController.PATH);
    }

    @Test
    @DisplayName("DEBUG 打开时才输出堆栈（含库中字段值）：证明上一条的“日志里没有它”来自级别而不是捕获失效")
    void theStackIncludingTheDatabaseValueIsOnlyPrintedAtDebugLevel() throws Exception {
        MockHttpSession admin = adminSession();
        Logger handlerLogger = (Logger) LoggerFactory.getLogger(ApiExceptionHandler.class);
        Level previousLevel = handlerLogger.getLevel();

        ListAppender<ILoggingEvent> appender = attachToRootLogger();
        MvcResult result;
        try {
            handlerLogger.setLevel(Level.DEBUG);
            result = getJson(ThrowingProbeController.PATH, admin);
        } finally {
            handlerLogger.setLevel(previousLevel);
            detachFromRootLogger(appender);
        }

        assertError(result, 500, "INTERNAL_ERROR");

        // 同一个异常在这一档里能看到堆栈与消息：说明捕获本身拿得到异常对象，
        // 上面那条 ERROR 断言判的是“没打印”，不是“没捕获到”。
        ILoggingEvent debugEvent = eventsOf(appender, Level.DEBUG).stream()
                .filter(event -> ApiExceptionHandler.class.getName().equals(event.getLoggerName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("DEBUG 档下没有记录未预期错误的堆栈"));
        assertThat(debugEvent.getThrowableProxy()).as("DEBUG 档保留完整堆栈").isNotNull();
        assertThat(debugEvent.getThrowableProxy().getMessage())
                .as("DEBUG 档的堆栈消息里就有库中的字段值")
                .contains(CHINESE_DISPLAY);

        // 关键：即使 DEBUG 打开，ERROR 这一档依然只有类型与路径，不带异常对象
        ILoggingEvent errorEvent = eventsOf(appender, Level.ERROR).stream()
                .filter(event -> ApiExceptionHandler.class.getName().equals(event.getLoggerName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("DEBUG 档下缺少 ERROR 记录"));
        assertThat(errorEvent.getThrowableProxy())
                .as("ERROR 与日志级别无关，始终不打印异常对象")
                .isNull();
        assertThat(errorEvent.getFormattedMessage()).doesNotContain(CHINESE_DISPLAY);
    }

    // ---- 日志捕获：直接读日志事件，避免依赖控制台输出是否被测试框架接管 ----

    private static ListAppender<ILoggingEvent> attachToRootLogger() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        return appender;
    }

    private static void detachFromRootLogger(ListAppender<ILoggingEvent> appender) {
        appender.stop();
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(appender);
    }

    private static List<ILoggingEvent> eventsOf(ListAppender<ILoggingEvent> appender, Level level) {
        return appender.list.stream().filter(event -> event.getLevel() == level).toList();
    }

    /** 事件文本 + 挂在其上的异常消息：两者都算“进了日志”。 */
    private static String loggedText(ListAppender<ILoggingEvent> appender) {
        StringBuilder text = new StringBuilder();
        for (ILoggingEvent event : appender.list) {
            text.append(event.getFormattedMessage()).append('\n');
            if (event.getThrowableProxy() != null) {
                text.append(event.getThrowableProxy().getClassName())
                        .append(": ")
                        .append(event.getThrowableProxy().getMessage())
                        .append('\n');
            }
        }
        return text.toString();
    }
}
