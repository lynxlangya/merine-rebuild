package com.merine.rebuild.agent.provider;

import com.merine.rebuild.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** 版本前缀 + 随机 nonce + AES-GCM 密文；主密钥只从部署环境读取，与数据库分离。 */
@Component
public class ProviderKeyCipher {
    private final String masterKey;
    private final SecureRandom random = new SecureRandom();
    public ProviderKeyCipher(@Value("${merine.agent.provider-encryption-key:}") String masterKey) {
        this.masterKey = masterKey;
    }
    public String encrypt(String providerId, String plain) {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, providerId, nonce);
            return "v1." + Base64.getEncoder().encodeToString(nonce) + "."
                    + Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw unavailable();
        }
    }
    // 包内使用；没有暴露密钥读取接口。
    String decrypt(String providerId, String encrypted) {
        try {
            String[] parts = encrypted.split("\\.", -1);
            if (parts.length != 3 || !parts[0].equals("v1")) throw new IllegalArgumentException();
            return new String(cipher(Cipher.DECRYPT_MODE, providerId, Base64.getDecoder().decode(parts[1]))
                    .doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw unavailable();
        }
    }
    private Cipher cipher(int mode, String id, byte[] nonce) throws GeneralSecurityException {
        if (!masterKey.matches("[0-9a-fA-F]{64}")) throw unavailable();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(HexFormat.of().parseHex(masterKey), "AES"),
                new GCMParameterSpec(128, nonce));
        cipher.updateAAD(id.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_KEY_UNAVAILABLE",
                "密钥保管配置不可用，请联系管理员检查服务端加密配置");
    }
}
