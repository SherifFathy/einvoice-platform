package com.einvoice.core.domain.enums;

/** Types of artifacts generated or received during invoice lifecycle. */
public enum ArtifactType {
    SIGNED_XML,
    SIGNED_JSON,
    QR_CODE,
    CLEARED_XML,
    ETA_RESPONSE,
    ZATCA_RESPONSE,
    ETA_PDF,
    ETA_CADES_SIG
}
