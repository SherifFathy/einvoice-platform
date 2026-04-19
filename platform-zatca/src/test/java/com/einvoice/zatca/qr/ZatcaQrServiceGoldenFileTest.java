package com.einvoice.zatca.qr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZatcaQrServiceGoldenFileTest {

    private ZatcaQrService qrService;

    @BeforeEach
    void setUp() {
        qrService = new ZatcaQrService();
    }

    @Test
    void shouldEncodeTlvWithAllFields() {
        byte[] result = qrService.encodeTlv(
                "Test Company",
                "300000000000003",
                "2026-04-15T10:30:00+03:00",
                new java.math.BigDecimal("1150.00"),
                new java.math.BigDecimal("150.00"),
                "dGVzdGhhc2g=",
                new byte[]{0x01, 0x02, 0x03},
                new byte[]{0x04, 0x05, 0x06}
        );

        assertNotNull(result);
        assertTrue(result.length > 0);

        int offset = 0;
        assertEquals(0x01, result[offset++]);
        assertEquals(12, result[offset++]);
        assertEquals("Test Company", new String(result, offset, 12));
        offset += 12;

        assertEquals(0x02, result[offset++]);
        assertEquals(15, result[offset++]);
        assertEquals("300000000000003", new String(result, offset, 15));
        offset += 15;

        assertEquals(0x03, result[offset++]);
        assertEquals(25, result[offset++]);
        assertEquals("2026-04-15T10:30:00+03:00", new String(result, offset, 25));
        offset += 25;
    }

    @Test
    void shouldEncodeTlvBase64() {
        String base64 = qrService.encodeTlvBase64(
                "Seller",
                "VAT123",
                "2026-01-01T00:00:00Z",
                new java.math.BigDecimal("100.00"),
                new java.math.BigDecimal("15.00"),
                "hash123",
                null,
                null
        );

        assertNotNull(base64);
        assertTrue(base64.length() > 0);
        assertTrue(base64.matches("[A-Za-z0-9+/=]+"));
    }

    @Test
    void shouldHandleEmptySignatureAndPublicKey() {
        byte[] result = qrService.encodeTlv(
                "Seller",
                "VAT123",
                "2026-01-01T00:00:00Z",
                new java.math.BigDecimal("100.00"),
                new java.math.BigDecimal("15.00"),
                "hash",
                null,
                null
        );

        assertNotNull(result);
        assertTrue(result.length > 0);
    }

    @Test
    void shouldProduceDeterministicOutput() {
        byte[] first = qrService.encodeTlv(
                "Test",
                "VAT",
                "2026-01-01T00:00:00Z",
                new java.math.BigDecimal("100.00"),
                new java.math.BigDecimal("15.00"),
                "hash",
                new byte[]{0x01},
                new byte[]{0x02}
        );
        byte[] second = qrService.encodeTlv(
                "Test",
                "VAT",
                "2026-01-01T00:00:00Z",
                new java.math.BigDecimal("100.00"),
                new java.math.BigDecimal("15.00"),
                "hash",
                new byte[]{0x01},
                new byte[]{0x02}
        );

        assertArrayEquals(first, second);
    }

    @Test
    void shouldHandleZeroAmounts() {
        String base64 = qrService.encodeTlvBase64(
                "Seller",
                "VAT",
                "2026-01-01T00:00:00Z",
                java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO,
                "",
                null,
                null
        );

        assertNotNull(base64);
        assertTrue(base64.length() > 0);
    }

    @Test
    void shouldEncodeTlvWithLongValue() {
        byte[] bigSignature = new byte[300];
        for (int i = 0; i < bigSignature.length; i++) {
            bigSignature[i] = (byte) (i & 0xFF);
        }
        byte[] result = qrService.encodeTlv(
                "S", "V", "2026-01-01T00:00:00Z",
                new java.math.BigDecimal("1.00"),
                new java.math.BigDecimal("0.15"),
                "h",
                bigSignature,
                new byte[]{0x01, 0x02}
        );
        int idx = findTag(result, (byte) 0x07);
        assertEquals((byte) 0x07, result[idx]);
        assertEquals((byte) 0x82, result[idx + 1]);
        int len = ((result[idx + 2] & 0xFF) << 8) | (result[idx + 3] & 0xFF);
        assertEquals(300, len);
        assertEquals((byte) 0x00, result[idx + 4]);
    }

    private int findTag(byte[] tlv, byte tag) {
        int i = 0;
        while (i < tlv.length) {
            byte t = tlv[i++];
            int len;
            int first = tlv[i++] & 0xFF;
            if ((first & 0x80) == 0) {
                len = first;
            } else {
                int numLenBytes = first & 0x7F;
                len = 0;
                for (int j = 0; j < numLenBytes; j++) {
                    len = (len << 8) | (tlv[i++] & 0xFF);
                }
            }
            if (t == tag) {
                return i - (len >= 0x80
                        ? 2 + (32 - Integer.numberOfLeadingZeros(len) + 7) / 8
                        : 2);
            }
            i += len;
        }
        throw new AssertionError("Tag not found: " + tag);
    }
}
