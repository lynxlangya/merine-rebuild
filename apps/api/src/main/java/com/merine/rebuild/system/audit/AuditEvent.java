package com.merine.rebuild.system.audit;

/**
 * 待写入的审计事件。
 *
 * <p>{@code summary} 由调用方按模板写死（对象名称、数量、状态之类的白名单内容），
 * **禁止**把密码、密钥、情报正文、对话内容或完整请求体拼进来——这是硬约束，
 * 负面清单回归会直接在表里搜这些串。
 *
 * <p>{@code result} 只有两种：写操作成功记 {@code SUCCEEDED}，登录失败记 {@code FAILED}。
 */
public record AuditEvent(
        String module,
        String action,
        String result,
        String targetType,
        String targetId,
        String targetLabel,
        String summary) {

    public static AuditEvent succeeded(String module, String action, String targetType,
                                       String targetId, String targetLabel, String summary) {
        return new AuditEvent(module, action, "SUCCEEDED", targetType, targetId, targetLabel, summary);
    }

    /** 失败事件：目前只有登录失败；它不进业务事务，独立写入。 */
    public static AuditEvent failed(String module, String action, String targetType, String targetId,
                                    String targetLabel, String summary) {
        return new AuditEvent(module, action, "FAILED", targetType, targetId, targetLabel, summary);
    }
}
