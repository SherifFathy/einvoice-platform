package com.einvoice.eta.status;

import com.einvoice.core.authority.AuthorityResponse;
import com.einvoice.core.authority.StatusInput;
import com.einvoice.core.domain.eta.EtaInvoiceHeader;
import com.einvoice.core.domain.eta.EtaReceiptHeader;
import com.einvoice.eta.engine.EtaAuthorityEngine;
import org.springframework.stereotype.Service;

/**
 * Service for checking the submission status of ETA documents with the authority.
 */
@Service
public class EtaStatusService {

    private final EtaAuthorityEngine engine;

    public EtaStatusService(EtaAuthorityEngine engine) {
        this.engine = engine;
    }

    /**
     * Check the status of an invoice submission with ETA.
     *
     * @param header the invoice header
     * @return the authority response
     */
    public AuthorityResponse checkStatus(EtaInvoiceHeader header) {
        return engine.checkStatus(new StatusInput(
                header.getCompanyId(),
                header.getAuthorityEnvironmentId(),
                "INVOICE",
                header.getId(),
                header.getEtaSubmissionId()));
    }

    /**
     * Check the status of a receipt submission with ETA.
     *
     * @param header the receipt header
     * @return the authority response
     */
    public AuthorityResponse checkStatus(EtaReceiptHeader header) {
        return engine.checkStatus(new StatusInput(
                header.getCompanyId(),
                header.getAuthorityEnvironmentId(),
                "RECEIPT",
                header.getId(),
                header.getEtaSubmissionId()));
    }
}
