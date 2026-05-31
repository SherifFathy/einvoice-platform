package com.einvoice.api.zatca.submission.service;

import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader;
import com.einvoice.core.error.MissingCryptographicStampException;

public final class SimplifiedStampGuard {

    private SimplifiedStampGuard() {}

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
