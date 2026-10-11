package com.merine.rebuild.agent.datasource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** 专用服务间路径不使用浏览器 Session；Controller 恒定时间校验部署级随机令牌。 */
@Configuration
class AgentBridgeSecurity {
    @Bean
    @Order(1)
    SecurityFilterChain agentBridgeChain(HttpSecurity http) throws Exception {
        return http.securityMatcher(AgentDataSourceBridge.PATH)
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, AgentDataSourceBridge.PATH).permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
