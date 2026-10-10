package com.merine.rebuild.agent.chat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 海防助手对话的常驻装配：对话端点是正式端点（按 agent:chat:use 判定），
 * 执行器用虚拟线程承载阻塞写事件，并发名额由 {@link ChatRunRegistry} 原子控制——
 * 虚拟线程不是并发上限的替代品。脚本替身单独按演示开关注册（见 {@link ChatDemoOnly}）。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChatProperties.class)
@EnableScheduling
final class ChatConfiguration {

    @Bean(name = "chatRunExecutor", destroyMethod = "shutdown")
    ExecutorService chatRunExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean(name = "chatRunScheduler", destroyMethod = "shutdown")
    ScheduledExecutorService chatRunScheduler() {
        AtomicInteger counter = new AtomicInteger();
        return Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "chat-run-scheduler-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }
}
