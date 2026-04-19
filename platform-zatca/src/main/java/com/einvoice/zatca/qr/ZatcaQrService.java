package com.einvoice.zatca.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

/**
 * TLV encoding service for ZATCA QR code generation.
 */
@Service
public class ZatcaQrService {

    /**
     * Encodes invoice data into TLV (Tag-Length-Value) byte format.
     *
     * @param sellerName the seller name
     * @param vatNumber the VAT registration number
     * @param timestamp the invoice timestamp
     * @param totalWithVat the total amount including VAT
     * @param totalVat the total VAT amount
     * @param invoiceHash the invoice hash
     * @param signature the digital signature bytes
     * @param publicKey the public key bytes
     * @return the TLV-encoded byte array
     */
    public byte[] encodeTlv(String sellerName, String vatNumber, String timestamp,
            BigDecimal totalWithVat, BigDecimal totalVat,
            String invoiceHash, byte[] signature, byte[] publicKey) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            writeTag(baos, (byte) 1, sellerName.getBytes(StandardCharsets.UTF_8));
            writeTag(baos, (byte) 2, vatNumber.getBytes(StandardCharsets.UTF_8));
            writeTag(baos, (byte) 3, timestamp.getBytes(StandardCharsets.UTF_8));
            writeTag(baos, (byte) 4, formatAmount(totalWithVat).getBytes(StandardCharsets.UTF_8));
            writeTag(baos, (byte) 5, formatAmount(totalVat).getBytes(StandardCharsets.UTF_8));
            writeTag(baos, (byte) 6, invoiceHash != null
                    ? invoiceHash.getBytes(StandardCharsets.UTF_8) : new byte[0]);
            writeTag(baos, (byte) 7, signature != null ? signature : new byte[0]);
            writeTag(baos, (byte) 8, publicKey != null ? publicKey : new byte[0]);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode TLV", e);
        }
    }

    /**
     * Encodes invoice data into a Base64-encoded TLV string.
     *
     * @param sellerName the seller name
     * @param vatNumber the VAT registration number
     * @param timestamp the invoice timestamp
     * @param totalWithVat the total amount including VAT
     * @param totalVat the total VAT amount
     * @param invoiceHash the invoice hash
     * @param signature the digital signature bytes
     * @param publicKey the public key bytes
     * @return the Base64-encoded TLV string
     */
    public String encodeTlvBase64(String sellerName, String vatNumber, String timestamp,
            BigDecimal totalWithVat, BigDecimal totalVat,
            String invoiceHash, byte[] signature, byte[] publicKey) {
        byte[] tlvBytes = encodeTlv(sellerName, vatNumber, timestamp,
                totalWithVat, totalVat, invoiceHash, signature, publicKey);
        return Base64.getEncoder().encodeToString(tlvBytes);
    }

    /**
     * Generates a QR code PNG image from the TLV-encoded invoice data.
     *
     * @param sellerName the seller name
     * @param vatNumber the VAT registration number
     * @param timestamp the invoice timestamp
     * @param totalWithVat the total amount including VAT
     * @param totalVat the total VAT amount
     * @param invoiceHash the invoice hash
     * @param signature the digital signature bytes
     * @param publicKey the public key bytes
     * @param width the QR code image width in pixels
     * @param height the QR code image height in pixels
     * @return the PNG image bytes
     */
    public byte[] generateQrPng(String sellerName, String vatNumber, String timestamp,
            BigDecimal totalWithVat, BigDecimal totalVat,
            String invoiceHash, byte[] signature, byte[] publicKey,
            int width, int height) {
        byte[] tlvBytes = encodeTlv(sellerName, vatNumber, timestamp,
                totalWithVat, totalVat, invoiceHash, signature, publicKey);
        return generateQrPngFromBytes(tlvBytes, width, height);
    }

    /**
     * Generates a QR code PNG image from a Base64 TLV string.
     *
     * @param tlvBase64 the Base64-encoded TLV string
     * @param width the QR code image width in pixels
     * @param height the QR code image height in pixels
     * @return the PNG image bytes
     */
    public byte[] generateQrPngFromBase64(String tlvBase64, int width, int height) {
        byte[] tlvBytes = Base64.getDecoder().decode(tlvBase64);
        return generateQrPngFromBytes(tlvBytes, width, height);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private byte[] generateQrPngFromBytes(byte[] data, int width, int height) {
        try {
            QRCodeWriter qrWriter = new QRCodeWriter();
            BitMatrix matrix = qrWriter.encode(
                    Base64.getEncoder().encodeToString(data),
                    BarcodeFormat.QR_CODE, width, height);
            java.awt.image.BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        } catch (WriterException | IOException e) {
            throw new RuntimeException("Failed to generate QR PNG image", e);
        }
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private void writeTag(ByteArrayOutputStream baos, byte tag, byte[] value) {
        baos.write(tag);
        writeLength(baos, value.length);
        if (value.length > 0) {
            baos.write(value, 0, value.length);
        }
    }

    /**
     * Writes a TLV length using BER short/long form.
     *
     * <p>Short form: if length ≤ 127, emit one byte.
     * Long form: emit 0x80 | numLengthBytes, then numLengthBytes big-endian length bytes.</p>
     *
     * @param baos target stream
     * @param length non-negative value length in bytes
     */
    private void writeLength(ByteArrayOutputStream baos, int length) {
        if (length < 0) {
            throw new IllegalArgumentException("TLV length must be non-negative: " + length);
        }
        if (length <= 0x7F) {
            baos.write(length);
            return;
        }
        int numBytes = 0;
        int tmp = length;
        while (tmp > 0) {
            numBytes++;
            tmp >>>= 8;
        }
        baos.write(0x80 | numBytes);
        for (int i = numBytes - 1; i >= 0; i--) {
            baos.write((length >>> (i * 8)) & 0xFF);
        }
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private String formatAmount(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
