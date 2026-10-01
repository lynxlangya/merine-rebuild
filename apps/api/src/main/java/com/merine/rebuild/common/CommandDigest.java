package com.merine.rebuild.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;

/** 对规范化命令生成有边界的摘要；不包含凭据或传输层JSON顺序。 */
public final class CommandDigest {
    private CommandDigest() { }
    public static String key(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,80}"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "请求键需为8–80位字母、数字或 . _ : -");
        return key;
    }
    public static String digest(Object... values) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                byte[] data = (value == null ? "<NULL>" : value.toString()).getBytes(StandardCharsets.UTF_8);
                hash.update(Integer.toString(data.length).getBytes(StandardCharsets.US_ASCII));
                hash.update((byte) ':'); hash.update(data);
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
