package com.merine.rebuild.agent.chat.dto;

/**
 * 流内失败的稳定错误码。HTTP 层的冲突/校验错误仍用 {@code ApiException} 的字符串码，
 * 这里只覆盖已经开始写流之后的终止原因。
 */
public enum ChatErrorCode {
    MODEL_AUTH_FAILED,
    MODEL_RATE_LIMITED,
    MODEL_TIMEOUT,
    MODEL_UNAVAILABLE,
    /** 上游因请求内容拒绝（400/404/422）：模型标识、参数或档位不受支持。 */
    MODEL_REQUEST_REJECTED,
    UPSTREAM_PROTOCOL_ERROR,
    INTERNAL
}
