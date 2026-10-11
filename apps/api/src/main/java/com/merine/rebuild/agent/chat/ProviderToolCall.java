package com.merine.rebuild.agent.chat;

/** 完整上游工具请求；流式片段只有组装并校验后才能转成这个值。 */
record ProviderToolCall(String id, String name, String arguments) {}
