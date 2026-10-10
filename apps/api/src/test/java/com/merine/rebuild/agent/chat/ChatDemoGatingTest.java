package com.merine.rebuild.agent.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.merine.rebuild.support.MockMvcRegressionSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

/**
 * 对话端点是正式端点（按 agent:chat:use 判定），不受演示开关影响；
 * 演示开关只决定本地脚本替身是否注册——生产与默认环境都不能有替身，
 * 也就不存在「真实调用失败悄悄回退到假数据」的路径。
 */
@TestPropertySource(properties = {
        "merine.agent.chat.demo-enabled=false",
        "merine.agent.chat.mode=PROVIDER"
})
class ChatDemoGatingTest extends MockMvcRegressionSupport {

    @Autowired
    ApplicationContext context;

    @Test
    void productionGroupIsRegisteredButScriptedStubIsNot() {
        assertThat(context.getBeanNamesForType(ChatController.class)).isNotEmpty();
        assertThat(context.getBeanNamesForType(ChatService.class)).isNotEmpty();
        assertThat(context.getBeanNamesForType(ChatRunRegistry.class)).isNotEmpty();
        assertThat(context.getBeanNamesForType(ChatProperties.class)).isNotEmpty();
        assertThat(context.getBeanNamesForType(ScriptedChatStub.class)).isEmpty();
    }
}
