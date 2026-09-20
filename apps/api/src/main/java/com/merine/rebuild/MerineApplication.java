package com.merine.rebuild;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * 排除 Spring Boot 默认的内存用户自动配置：
 * 本项目按 sys_user 自行认证，不使用 UserDetailsService，也不需要那个用不到的默认账号。
 * 保留它会在每次启动时向日志打印一个生成密码，违反“任何级别都不记录密码”。
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class MerineApplication {
    public static void main(String[] args) {
        SpringApplication.run(MerineApplication.class, args);
    }
}
