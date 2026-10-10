package com.merine.rebuild.system.audit;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 采集：只用于登录与其它需要留痕的操作。
 *
 * 生产由 nginx 反代，真实客户端地址在 `X-Forwarded-For` 的第一段；本地直连时该头不存在，
 * 回落到 `remoteAddr`。这里只取 IP，不记录 User-Agent 等其它信息（必要性不足）。
 */
final class AuditClientIp {

    private static final int MAX_LENGTH = 45;

    private AuditClientIp() {}

    static String of(HttpServletRequest request) {
        if (request == null) return "";
        String forwarded = request.getHeader("X-Forwarded-For");
        String candidate = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",")[0].trim();
        if (candidate == null) return "";
        String trimmed = candidate.trim();
        return trimmed.length() <= MAX_LENGTH ? trimmed : trimmed.substring(0, MAX_LENGTH);
    }
}
