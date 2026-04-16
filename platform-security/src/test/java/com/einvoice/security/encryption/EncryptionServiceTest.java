package com.einvoice.security.encryption;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EncryptionServiceTest {

    private static final String MASTER_KEY_BASE64 =
            Base64.getEncoder().encodeToString(new byte[32]);

    private EncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new EncryptionService(MASTER_KEY_BASE64);
    }

    @Test
    void encryptDecryptRoundTrip_returnsOriginalPlaintext() {
        byte[] plaintext = "sensitive-credential-data".getBytes();

        byte[] encrypted = encryptionService.encrypt(plaintext);
        byte[] decrypted = encryptionService.decrypt(encrypted);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_returnsDifferentCiphertextEachTime() {
        byte[] plaintext = "same-input".getBytes();

        byte[] encrypted1 = encryptionService.encrypt(plaintext);
        byte[] encrypted2 = encryptionService.encrypt(plaintext);

        assertFalse(java.util.Arrays.equals(encrypted1, encrypted2),
                "Two encryptions of the same plaintext should produce different ciphertext "
                        + "due to random IV");
    }

    @Test
    void encrypt_ciphertextDiffersFromPlaintext() {
        byte[] plaintext = "hello-world".getBytes();

        byte[] encrypted = encryptionService.encrypt(plaintext);

        assertFalse(java.util.Arrays.equals(plaintext, encrypted));
    }

    @Test
    void encrypt_and_decrypt_withBinaryData() {
        byte[] binaryData = new byte[256];
        for (int i = 0; i < binaryData.length; i++) {
            binaryData[i] = (byte) i;
        }

        byte[] encrypted = encryptionService.encrypt(binaryData);
        byte[] decrypted = encryptionService.decrypt(encrypted);

        assertArrayEquals(binaryData, decrypted);
    }

    @Test
    void encrypt_returnsNullForNullInput() {
        byte[] result = encryptionService.encrypt(null);
        assertTrue(result == null);
    }

    @Test
    void decrypt_returnsNullForNullInput() {
        byte[] result = encryptionService.decrypt(null);
        assertTrue(result == null);
    }

    @Test
    void constructor_rejectsShortKey() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThrows(IllegalArgumentException.class,
                () -> new EncryptionService(shortKey));
    }

    @Test
    void decrypt_throwsOnGarbageInput() {
        assertThrows(EncryptionService.EncryptionException.class,
                () -> encryptionService.decrypt("garbage".getBytes()));
    }

    @Test
    void keyRotation_decryptFailsWithOldKey() {
        byte[] plaintext = "encrypted-with-old-key".getBytes();

        byte[] encrypted = encryptionService.encrypt(plaintext);

        byte[] differentKey = new byte[32];
        differentKey[0] = 1;
        String newKeyBase64 = Base64.getEncoder().encodeToString(differentKey);
        EncryptionService newKeyService = new EncryptionService(newKeyBase64);

        assertThrows(EncryptionService.EncryptionException.class,
                () -> newKeyService.decrypt(encrypted));
    }

    @Test
    void encrypt_emptyPlaintext_succeeds() {
        byte[] plaintext = new byte[0];

        byte[] encrypted = encryptionService.encrypt(plaintext);
        assertNotNull(encrypted);

        byte[] decrypted = encryptionService.decrypt(encrypted);
        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_largePayload_succeeds() {
        byte[] largeData = new byte[1_000_000];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        byte[] encrypted = encryptionService.encrypt(largeData);
        byte[] decrypted = encryptionService.decrypt(encrypted);

        assertArrayEquals(largeData, decrypted);
    }
}
