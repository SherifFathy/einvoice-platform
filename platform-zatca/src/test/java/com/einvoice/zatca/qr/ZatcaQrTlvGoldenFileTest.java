package com.einvoice.zatca.qr;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZatcaQrTlvGoldenFileTest {

    private ZatcaQrService qrService;

    @BeforeEach
    void setUp() {
        qrService = new ZatcaQrService();
    }

    @Test
    void tlvEncoding_coversAllNineTags() {
        List<byte[]> tags = List.of(
                "Seller Name".getBytes(StandardCharsets.UTF_8),
                "300000000000003".getBytes(StandardCharsets.UTF_8),
                "2026-05-19T14:30:00".getBytes(StandardCharsets.UTF_8),
                "345.00".getBytes(StandardCharsets.UTF_8),
                "45.00".getBytes(StandardCharsets.UTF_8),
                HexFormat.of().parseHex("a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2"),
                new byte[32],
                new byte[32],
                new byte[32]);

        String base64 = qrService.encodeTlvBase64(tags);

        assertThat(base64).isNotBlank();

        byte[] decoded = java.util.Base64.getDecoder().decode(base64);
        assertThat(decoded.length).isGreaterThan(0);

        int offset = 0;
        int tagIndex = 0;
        while (offset < decoded.length && tagIndex < 9) {
            int tag = decoded[offset] & 0xFF;
            assertThat(tag).isEqualTo(tagIndex + 1);
            offset++;
            if (offset >= decoded.length) {
                break;
            }
            int length = decoded[offset] & 0xFF;
            offset++;
            if (length >= 0x80) {
                int numBytes = length & 0x7F;
                length = 0;
                for (int b = 0; b < numBytes; b++) {
                    length = (length << 8) | (decoded[offset] & 0xFF);
                    offset++;
                }
            }
            assertThat(offset + length).isLessThanOrEqualTo(decoded.length);
            offset += length;
            tagIndex++;
        }
        assertThat(tagIndex).isEqualTo(9);
    }

    @Test
    void tlvEncoding_emptyTagProducesZeroLength() {
        List<byte[]> tags = List.of(new byte[0]);
        String base64 = qrService.encodeTlvBase64(tags);
        byte[] decoded = java.util.Base64.getDecoder().decode(base64);
        assertThat(decoded[0]).isEqualTo((byte) 1);
        assertThat(decoded[1]).isEqualTo((byte) 0);
    }

    @Test
    void tlvEncoding_valueExceeding127BytesUsesBerMultiByteLength() {
        byte[] longValue = new byte[200];
        java.util.Arrays.fill(longValue, (byte) 'A');
        List<byte[]> tags = List.of(longValue);

        String base64 = qrService.encodeTlvBase64(tags);
        byte[] decoded = java.util.Base64.getDecoder().decode(base64);

        assertThat(decoded[0]).isEqualTo((byte) 1);
        assertThat(decoded[1]).isEqualTo((byte) 0x81);
        assertThat(decoded[2] & 0xFF).isEqualTo(200);
        assertThat(decoded.length).isEqualTo(3 + 200);

        for (int i = 3; i < 3 + 200; i++) {
            assertThat(decoded[i]).isEqualTo((byte) 'A');
        }
    }

    @Test
    void tlvEncoding_valueExceeding255BytesUsesTwoByteLength() {
        byte[] longValue = new byte[300];
        java.util.Arrays.fill(longValue, (byte) 'B');
        List<byte[]> tags = List.of(longValue);

        String base64 = qrService.encodeTlvBase64(tags);
        byte[] decoded = java.util.Base64.getDecoder().decode(base64);

        assertThat(decoded[0]).isEqualTo((byte) 1);
        assertThat(decoded[1]).isEqualTo((byte) 0x82);
        assertThat(((decoded[2] & 0xFF) << 8) | (decoded[3] & 0xFF))
                .isEqualTo(300);
        assertThat(decoded.length).isEqualTo(4 + 300);
    }

    @Test
    void pngRenders_300x300() {
        List<byte[]> tags = List.of(
                "Test".getBytes(StandardCharsets.UTF_8));
        String base64 = qrService.encodeTlvBase64(tags);
        byte[] png = qrService.renderPng300x300(base64);

        assertThat(png).isNotNull();
        assertThat(png.length).isGreaterThan(0);
        assertThat(png[0] & 0xFF).isEqualTo(0x89);
        assertThat(png[1] & 0xFF).isEqualTo(0x50);
        assertThat(png[2] & 0xFF).isEqualTo(0x4E);
        assertThat(png[3] & 0xFF).isEqualTo(0x47);
    }
}
