package com.einvoice.core.domain.eta.document;

/** Javadoc. */
public enum EtaInvoiceDocumentType {
    i,
    c,
    d,
    ei,
    ec,
    ed;

    /**
     * Whether this document type requires an original document reference.
     *
     * @return true if this type requires an original document
     */
    public boolean requiresOriginalDocument() {
        return this == c || this == d || this == ec || this == ed;
    }
}
