package com.einvoice.api.zatca.submission.service;

import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader;
import com.einvoice.core.error.MissingCryptographicStampException;

/**
 * Guards that a ZATCA simplified document carries its cryptographic stamp and
 * signed-XML artifact before it may transition into a post-submission state
 * (BR-KSA-60).
 */
public final class SimplifiedStampGuard {

    private SimplifiedStampGuard() {
    }

    /**
     * Asserts that the simplified header carries a cryptographic stamp and a
     * signed-XML artifact when it is in (or moving into) {@code SUBMITTED},
     * {@code ACCEPTED}, or {@code IN_REVIEW}.
     *
     * @param h the simplified invoice header to validate
     */
    public static void assertPresent(ZatcaSimplifiedHeader h) {
        DocumentState s = h.getStatus();
        if (s == DocumentState.SUBMITTED
                || s == DocumentState.ACCEPTED
                || s == DocumentState.IN_REVIEW) {
            if (h.getCryptographicStampValue() == null
                    || h.getSignedXmlArtifactId() == null) {
                throw new MissingCryptographicStampException(
                        "BR-KSA-60: Simplified document " + h.getId()
                                + " cannot transition to " + s
                                + " without a cryptographic stamp"
                                + " + signed XML artifact");
            }
        }
    }
}
