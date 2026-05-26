package com.einvoice.zatca.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Encodes ZATCA QR TLV data and renders QR code PNG images. */
@Component
public class ZatcaQrService {

    /**
     * Encodes the supplied TLV tag values into a Base64 string per ZATCA spec.
     *
     * @param tags the ordered list of TLV tag byte values
     * @return the Base64-encoded TLV string
     */
    public String encodeTlvBase64(List<byte[]> tags) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (int i = 0; i < tags.size(); i++) {
            byte[] valueBytes = tags.get(i);
            if (valueBytes == null) {
                valueBytes = new byte[0];
            }
            bos.write(i + 1);
            writeBerLength(bos, valueBytes.length);
            bos.write(valueBytes, 0, valueBytes.length);
        }
        return Base64.getEncoder().encodeToString(bos.toByteArray());
    }

    private void writeBerLength(ByteArrayOutputStream bos, int length) {
        if (length < 128) {
            bos.write(length);
        } else if (length < 256) {
            bos.write(0x81);
            bos.write(length);
        } else {
            bos.write(0x82);
            bos.write((length >> 8) & 0xFF);
            bos.write(length & 0xFF);
        }
    }

    /**
     * Renders the Base64 TLV string as a QR code PNG image.
     *
     * @param base64Tlv the Base64-encoded TLV data
     * @param width the image width in pixels
     * @param height the image height in pixels
     * @return the PNG image bytes
     */
    public byte[] renderPng(String base64Tlv, int width, int height) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
            hints.put(EncodeHintType.CHARACTER_SET, "ISO-8859-1");

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(
                    base64Tlv, BarcodeFormat.QR_CODE, width, height, hints);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", bos);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("QR PNG rendering failed", e);
        }
    }

    public byte[] renderPng300x300(String base64Tlv) {
        return renderPng(base64Tlv, 300, 300);
    }
}
