package com.einvoice.zatca.status;

import com.einvoice.core.authority.AuthorityResponse;
import com.einvoice.core.authority.StatusInput;
import com.einvoice.core.domain.shared.TransactionType;
import com.einvoice.zatca.engine.ZatcaAuthorityEngine;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Checks the processing status of a previously submitted ZATCA document. */
@Service
public class ZatcaStatusService {

    private final ZatcaAuthorityEngine engine;

    public ZatcaStatusService(ZatcaAuthorityEngine engine) {
        this.engine = engine;
    }

    /**
     * Queries ZATCA for the current status of a submitted document.
     *
     * @param companyId the owning company identifier
     * @param authorityEnvironmentId the ZATCA environment identifier
     * @param transactionType the invoice transaction type
     * @param documentId the internal document identifier
     * @param zatcaUuid the UUID returned by ZATCA upon submission
     * @return the authority response containing the current status
     */
    public AuthorityResponse checkStatus(UUID companyId,
            Short authorityEnvironmentId,
            TransactionType transactionType,
            UUID documentId, String zatcaUuid) {
        return engine.checkStatus(new StatusInput(
                companyId, authorityEnvironmentId,
                transactionType.name(), documentId,
                zatcaUuid));
    }
}
