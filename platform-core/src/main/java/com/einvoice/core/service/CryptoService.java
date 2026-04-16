package com.einvoice.core.service;

/**
 * Abstraction for encryption and decryption of sensitive data.
 * Implemented by platform-security to decouple core from security internals.
 */
public interface CryptoService {

    /**
     * Encrypts the given plaintext bytes.
     *
     * @param plaintext the data to encrypt
     * @return the encrypted bytes, or null if input is null
     */
    byte[] encrypt(byte[] plaintext);

    /**
     * Decrypts the given encrypted bytes.
     *
     * @param encrypted the data to decrypt
     * @return the decrypted bytes, or null if input is null
     */
    byte[] decrypt(byte[] encrypted);
}
