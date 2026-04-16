package com.einvoice.security.encryption;

import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * AES-256-GCM encryption service for authority credentials.
 * Implements {@link com.einvoice.core.service.CryptoService} for use by platform-core.
 * Master key loaded from the {@code encryption.master-key} property.
 */
@Service
public class EncryptionService implements com.einvoice.core.service.CryptoService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Creates an EncryptionService with the given Base64-encoded master key.
     *
     * @param base64MasterKey Base64-encoded 256-bit AES key
     */
    public EncryptionService(
            @Value("${encryption.master-key}") String base64MasterKey) {
        byte[] keyBytes = Base64.getDecoder().decode(base64MasterKey);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "Encryption master key must be 256 bits (32 bytes), got " + keyBytes.length);
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypts plaintext using AES-256-GCM. Returns IV (12 bytes) || ciphertext || GCM tag.
     *
     * @param plaintext the data to encrypt
     * @return IV prepended to ciphertext and GCM auth tag
     */
    public byte[] encrypt(byte[] plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] result = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
            return result;
        } catch (Exception e) {
            throw new EncryptionException("Encryption failed", e);
        }
    }

    /**
     * Decrypts data produced by {@link #encrypt(byte[])}.
     *
     * @param encrypted the IV+ciphertext+tag bytes
     * @return the original plaintext
     */
    public byte[] decrypt(byte[] encrypted) {
        if (encrypted == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(encrypted, 0, iv, 0, GCM_IV_LENGTH);

            byte[] ciphertext = new byte[encrypted.length - GCM_IV_LENGTH];
            System.arraycopy(encrypted, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
            throw new EncryptionException("Decryption failed", e);
        }
    }

    /** Runtime exception for encryption/decryption failures. */
    public static class EncryptionException extends RuntimeException {
        EncryptionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
