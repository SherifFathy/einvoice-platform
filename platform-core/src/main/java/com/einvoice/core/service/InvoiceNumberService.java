package com.einvoice.core.service;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.domain.enums.InvoiceResetPolicy;
import com.einvoice.core.repository.AuthorityConfigRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Generates sequential invoice numbers per authority config settings. */
@Service
public class InvoiceNumberService {

    private final AuthorityConfigRepository authorityConfigRepository;

    public InvoiceNumberService(
            AuthorityConfigRepository authorityConfigRepository) {
        this.authorityConfigRepository = authorityConfigRepository;
    }

    /**
     * Generates and assigns the next invoice number for the given invoice.
     *
     * @param invoice the invoice to generate a number for
     * @return the generated invoice number
     */
    @Transactional
    public String generate(Invoice invoice) {
        AuthorityConfig config = authorityConfigRepository
                .findWithLockByBranchIdAndAuthorityAndEnvironment(
                        invoice.getBranch().getId(),
                        invoice.getAuthority(),
                        invoice.getEnvironment())
                .orElseThrow(() -> new InvoiceNumberGenerationException(
                        "No authority config found for branch="
                                + invoice.getBranch().getId()
                                + " authority=" + invoice.getAuthority()
                                + " environment=" + invoice.getEnvironment()));

        if (shouldReset(config)) {
            config.setInvoiceCounter(0L);
        }

        Long nextNumber = config.getInvoiceCounter() + 1;
        config.setInvoiceCounter(nextNumber);

        String prefix = config.getInvoicePrefix() != null
                ? config.getInvoicePrefix() : "";
        String invoiceNumber = prefix + String.format("%06d", nextNumber);

        invoice.setInvoiceNumber(invoiceNumber);
        return invoiceNumber;
    }

    private boolean shouldReset(AuthorityConfig config) {
        InvoiceResetPolicy policy = config.getInvoiceResetPolicy();
        if (policy == null || policy == InvoiceResetPolicy.NEVER) {
            return false;
        }
        if (config.getInvoiceCounter() == null
                || config.getInvoiceCounter() == 0) {
            return false;
        }
        OffsetDateTime updatedAt = config.getUpdatedAt();
        if (updatedAt == null) {
            return false;
        }
        LocalDate lastUsed = updatedAt.toLocalDate();
        LocalDate today = LocalDate.now();
        if (policy == InvoiceResetPolicy.ANNUAL) {
            return today.getYear() != lastUsed.getYear();
        }
        if (policy == InvoiceResetPolicy.MONTHLY) {
            return today.getYear() != lastUsed.getYear()
                    || today.getMonthValue() != lastUsed.getMonthValue();
        }
        return false;
    }

    /** Thrown when invoice number generation fails. */
    public static class InvoiceNumberGenerationException
            extends RuntimeException {

        public InvoiceNumberGenerationException(String message) {
            super(message);
        }
    }
}
