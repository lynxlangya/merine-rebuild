package com.merine.rebuild.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 服务器时钟；业务自行选择统计时区，测试可用固定时钟验证时间边界。 */
@Configuration(proxyBeanMethods = false)
public class TimeConfiguration {
    @Bean
    Clock serverClock() {
        return Clock.systemUTC();
    }
}
