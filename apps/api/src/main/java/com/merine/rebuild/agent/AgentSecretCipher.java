package com.merine.rebuild.agent;

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

/**
 * Agent 凭据加密：版本前缀 + 随机 nonce + AES-256-GCM 密文；主密钥只从部署环境读取，与数据库分离。
 *
 * <p>AAD 由调用方给出，用于把密文绑定到具体记录与用途：模型连接用记录 id，
 * 数据源用 {@code datasource:<id>}。同一份密文换到别的记录或另一类凭据上都会解密失败。
 * 主密钥沿用 {@code MODEL_PROVIDER_ENCRYPTION_KEY}，缺失或非法时写路径返回 503，不退回明文。
 */
@Component
public class AgentSecretCipher {
    private final String masterKey;
    private final SecureRandom random = new SecureRandom();

    public AgentSecretCipher(@Value("${merine.agent.provider-encryption-key:}") String masterKey) {
        this.masterKey = masterKey;
    }

    public String encrypt(String aad, String plain) {
        byte[] nonce = new byte[12];
        random.nextBytes(nonce);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, aad, nonce);
            return "v1." + Base64.getEncoder().encodeToString(nonce) + "."
                    + Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw unavailable();
        }
    }

    public String decrypt(String aad, String encrypted) {
        try {
            String[] parts = encrypted.split("\\.", -1);
            if (parts.length != 3 || !parts[0].equals("v1")) throw new IllegalArgumentException();
            return new String(cipher(Cipher.DECRYPT_MODE, aad, Base64.getDecoder().decode(parts[1]))
                    .doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw unavailable();
        }
    }

    private Cipher cipher(int mode, String aad, byte[] nonce) throws GeneralSecurityException {
        if (!masterKey.matches("[0-9a-fA-F]{64}")) throw unavailable();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(HexFormat.of().parseHex(masterKey), "AES"),
                new GCMParameterSpec(128, nonce));
        cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AGENT_SECRET_UNAVAILABLE",
                "密钥保管配置不可用，请联系管理员检查服务端加密配置");
    }
}
