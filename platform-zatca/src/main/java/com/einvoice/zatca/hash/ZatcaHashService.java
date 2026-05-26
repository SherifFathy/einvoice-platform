package com.einvoice.zatca.hash;

import com.einvoice.zatca.build.ZatcaUblCanonicaliser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/** Computes SHA-256 hashes over canonicalised ZATCA UBL documents. */
@Component
public class ZatcaHashService {

    private final ZatcaUblCanonicaliser canonicaliser;

    public ZatcaHashService(ZatcaUblCanonicaliser canonicaliser) {
        this.canonicaliser = canonicaliser;
    }

    /**
     * Computes SHA-256 over the canonicalised (C14N 1.1) signed UBL.
     * TODO: verify this matches ZATCA's expected hash input — ZATCA may
     * hash the raw signed XML or the canonicalised invoice element only.
     * Tracked as a known divergence to be resolved during integration
     * testing against the ZATCA sandbox.
     *
     * @param signedUblXml the signed UBL XML bytes
     * @return the hex-encoded SHA-256 hash
     */
    public String computeHash(byte[] signedUblXml) {
        try {
            byte[] canonical = canonicaliser.canonicalise(signedUblXml);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Hash computation failed", e);
        }
    }
}
