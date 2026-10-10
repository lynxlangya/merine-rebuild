package com.merine.rebuild.agent.chat;

import org.springframework.boot.test.context.SpringBootTest;

/**
 * 演示（脚本替身）模式的真实 HTTP 上下文：整组对话端点 + 本地替身。
 * 真实上游模式见 {@code ProviderChatRegressionTest}。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "merine.agent.chat.mode=DEMO",
        "merine.agent.chat.demo-enabled=true",
        "merine.agent.chat.run-timeout=5s",
        "merine.agent.chat.heartbeat-interval=1s",
        "merine.agent.chat.max-active-per-user=4",
        "merine.agent.chat.max-active-global=8",
        "merine.agent.chat.max-records=20"
})
abstract class ChatStreamSupport extends ChatHttpSupport {
}
