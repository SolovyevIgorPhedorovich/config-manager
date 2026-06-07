package com.uniikm.configmanager.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Шифрование секретов (пароли SSH/WinRM) для хранения в БД.
 * AES-256/GCM, ключ берётся из переменной окружения / конфигурации
 * {@code security.encryption.key} (Base64, 32 байта). Формат значения в БД:
 * {@code enc:<base64(IV(12) || ciphertext+tag)>}.
 */
@Slf4j
@Component
public class SecretCipher {

    private static final String PREFIX = "enc:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    // dev-ключ по умолчанию (Base64 от ровно 32 байт). В проде ОБЯЗАТЕЛЬНО переопределить.
    private static final String DEV_KEY = "Y2ZnbWdyLWRldi1kZWZhdWx0LXNlY3JldC1rZXktMzI=";

    private final SecretKeySpec keySpec;
    private final boolean usingDevKey;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${security.encryption.key:}") String configuredKey) {
        String keyBase64 = (configuredKey == null || configuredKey.isBlank()) ? DEV_KEY : configuredKey;
        this.usingDevKey = keyBase64.equals(DEV_KEY);
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalStateException(
                "security.encryption.key должен быть Base64 от 16/24/32 байт, получено: " + keyBytes.length);
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    @PostConstruct
    void warnIfDevKey() {
        if (usingDevKey) {
            log.warn("SecretCipher использует dev-ключ по умолчанию! Задайте security.encryption.key " +
                     "(переменная окружения SECURITY_ENCRYPTION_KEY) в продакшене.");
        }
    }

    /** Шифрует строку. null/пустую строку возвращает как есть. */
    public String encrypt(String plain) {
        if (plain == null || plain.isEmpty()) return plain;
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось зашифровать секрет", e);
        }
    }

    /**
     * Расшифровывает строку. Значения без префикса {@code enc:} считаются
     * незашифрованными (legacy) и возвращаются как есть — для плавной миграции.
     */
    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) return stored;
        if (!stored.startsWith(PREFIX)) return stored; // legacy plaintext
        try {
            byte[] data = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(data, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось расшифровать секрет", e);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
