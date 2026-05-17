package com.einvoice.core.domain.eta.document;

/** Javadoc. */
public enum EtaReceiptDocumentType {
    r, rr, rrwr, cr, crr,
    gs, gsr, rt, rtr, tr, trr,
    bk, bkr, ed, edr, pr, prr,
    sh, shr, en, enr, ut, utr;

    /**
     * Whether this receipt subtype requires an original receipt reference.
     *
     * @return true if this subtype requires an original receipt
     */
    public boolean requiresOriginalDocument() {
        return this == cr || this == crr
                || this == rr || this == rrwr
                || this == rt || this == rtr
                || this == tr || this == trr
                || this == bk || this == bkr
                || this == ed || this == edr
                || this == pr || this == prr
                || this == sh || this == shr
                || this == en || this == enr
                || this == ut || this == utr;
    }
}
