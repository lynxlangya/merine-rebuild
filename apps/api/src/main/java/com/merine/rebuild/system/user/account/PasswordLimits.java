package com.merine.rebuild.system.user.account;

import com.merine.rebuild.common.ApiException;
import com.merine.rebuild.common.ApiResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpStatus;

/** 账号凭据的编码边界；认证、用户管理和初始化共用，密码不截断、不归一化。 */
public final class PasswordLimits {
    public static final int MAX_BYTES = 72;
    public static final String MESSAGE = "密码不能超过 72 个 UTF-8 字节（中文通常占 3 字节）";

    private PasswordLimits() {
    }

    public static void requireSupportedLength(String password, String field) {
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", MESSAGE,
                    List.of(new ApiResponse.FieldError(field, MESSAGE)));
        }
    }
}
