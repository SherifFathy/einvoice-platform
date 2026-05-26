package com.einvoice.zatca.build;

import java.io.ByteArrayOutputStream;
import org.apache.xml.security.c14n.Canonicalizer;
import org.springframework.stereotype.Component;

/** Canonicalises UBL XML using C14N 1.1 (omit comments) for ZATCA hashing. */
@Component
public class ZatcaUblCanonicaliser {

    /**
     * Canonicalise the given XML bytes.
     *
     * @param xmlBytes raw XML bytes
     * @return canonicalised XML bytes
     */
    public byte[] canonicalise(byte[] xmlBytes) {
        try {
            Canonicalizer canon = Canonicalizer.getInstance(
                    Canonicalizer.ALGO_ID_C14N11_OMIT_COMMENTS);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            canon.canonicalize(xmlBytes, bos, false);
            return bos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Canonicalisation failed", e);
        }
    }
}
