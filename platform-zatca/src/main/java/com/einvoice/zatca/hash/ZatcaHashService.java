package com.einvoice.zatca.hash;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import org.apache.xml.security.Init;
import org.apache.xml.security.c14n.Canonicalizer;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

/**
 * Hash computation service for ZATCA invoice XML documents.
 */
@Service
public class ZatcaHashService {

    private static final String SEED_HASH =
            "NWZlY2ViNjZmZmM4NmYzOGQ5NTI3ODZjNmQ2OTZjNzljMjJkY2MwZjYyNGU5MTQ0Y2MzMjM1NGY0YjUyMzZmMQ==";

    static {
        Init.init();
    }

    /**
     * Computes a Base64-encoded SHA-256 hash of the given XML content.
     *
     * @param xmlContent the XML content to hash
     * @return the Base64-encoded hash
     */
    public String computeHash(String xmlContent) {
        try {
            String canonicalized = canonicalizeExclusive(xmlContent);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonicalized.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute hash", e);
        }
    }

    /**
     * Computes a Base64-encoded SHA-256 hash of the given DOM document.
     *
     * @param doc the DOM document to hash
     * @return the Base64-encoded hash
     */
    public String computeHash(Document doc) {
        try {
            Canonicalizer canon = Canonicalizer.getInstance(
                    Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            canon.canonicalizeSubtree(doc.getDocumentElement(), baos);
            byte[] canonicalized = baos.toByteArray();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonicalized);
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute hash from document", e);
        }
    }

    /**
     * Returns the previous invoice hash, or the seed hash if none is stored.
     *
     * @param storedPreviousHash the stored previous hash, may be null or blank
     * @return the previous hash or seed hash
     */
    public String getPreviousHashBase64(String storedPreviousHash) {
        if (storedPreviousHash == null || storedPreviousHash.isBlank()) {
            return SEED_HASH;
        }
        return storedPreviousHash;
    }

    public String getSeedHash() {
        return SEED_HASH;
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private String canonicalizeExclusive(String xml) throws Exception {
        javax.xml.parsers.DocumentBuilderFactory factory =
                javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new java.io.ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_8)));

        Canonicalizer canon = Canonicalizer.getInstance(
                Canonicalizer.ALGO_ID_C14N_EXCL_OMIT_COMMENTS);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        canon.canonicalizeSubtree(doc.getDocumentElement(), baos);
        byte[] result = baos.toByteArray();
        return new String(result, StandardCharsets.UTF_8);
    }
}
