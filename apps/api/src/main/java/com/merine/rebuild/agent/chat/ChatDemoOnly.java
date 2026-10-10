package com.merine.rebuild.agent.chat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;

/**
 * 本地演示对话的注册条件：{@code (dev | test) & !prod} 且显式开关打开。
 *
 * <p>整个演示组（控制器、编排、替身、内存记录与执行器）共用这一个条件；
 * 默认环境与生产都不注册，演示实现里也没有可回退到真实模型的分支。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Profile("(dev | test) & !prod")
@ConditionalOnProperty(name = "merine.agent.chat.demo-enabled", havingValue = "true")
@interface ChatDemoOnly {}
