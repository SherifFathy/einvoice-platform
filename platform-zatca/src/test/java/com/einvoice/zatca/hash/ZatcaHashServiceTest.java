package com.einvoice.zatca.hash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZatcaHashServiceTest {

    private ZatcaHashService hashService;

    @BeforeEach
    void setUp() {
        hashService = new ZatcaHashService();
    }

    @Test
    void shouldComputeHashForXmlContent() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Invoice>test</Invoice>";
        String hash = hashService.computeHash(xml);

        assertNotNull(hash);
        assertTrue(hash.length() > 0);
        assertTrue(hash.matches("[A-Za-z0-9+/=]+"));
    }

    @Test
    void shouldProduceDeterministicHash() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Invoice>test</Invoice>";
        String hash1 = hashService.computeHash(xml);
        String hash2 = hashService.computeHash(xml);

        assertEquals(hash1, hash2);
    }

    @Test
    void shouldProduceDifferentHashesForDifferentContent() {
        String xml1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Invoice>test1</Invoice>";
        String xml2 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Invoice>test2</Invoice>";

        String hash1 = hashService.computeHash(xml1);
        String hash2 = hashService.computeHash(xml2);

        assertNotEquals(hash1, hash2);
    }

    @Test
    void shouldReturnSeedHashWhenNoPreviousHash() {
        String seedHash = hashService.getPreviousHashBase64(null);
        assertNotNull(seedHash);
        assertEquals(hashService.getSeedHash(), seedHash);
    }

    @Test
    void shouldReturnSeedHashWhenEmptyPreviousHash() {
        String seedHash = hashService.getPreviousHashBase64("");
        assertEquals(hashService.getSeedHash(), seedHash);

        String seedHash2 = hashService.getPreviousHashBase64("   ");
        assertEquals(hashService.getSeedHash(), seedHash2);
    }

    @Test
    void shouldReturnStoredHashWhenPresent() {
        String storedHash = "c3RvcmVkSGFzaFZhbHVl";
        String result = hashService.getPreviousHashBase64(storedHash);
        assertEquals(storedHash, result);
    }

    @Test
    void shouldComputeBase64EncodedHash() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Invoice>test</Invoice>";
        String hash = hashService.computeHash(xml);

        byte[] decoded = java.util.Base64.getDecoder().decode(hash);
        assertEquals(32, decoded.length);
    }

    @Test
    void shouldChainHashesCorrectly() {
        String xml1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\">"
                + "<ID>1</ID></Invoice>";
        String xml2 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\">"
                + "<ID>2</ID></Invoice>";

        String pihForFirst = hashService.getPreviousHashBase64(null);
        assertEquals(hashService.getSeedHash(), pihForFirst,
                "first invoice uses the seed hash as PIH");

        String hash1 = hashService.computeHash(xml1);
        String pihForSecond = hashService.getPreviousHashBase64(hash1);
        assertEquals(hash1, pihForSecond,
                "second invoice's PIH is the first invoice's hash");

        String hash2 = hashService.computeHash(xml2);
        assertNotEquals(hash1, hash2);
        assertNotEquals(pihForSecond, hash2);
    }
}
