package com.einvoice.core.service;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.SubmissionAttempt;
import com.einvoice.core.domain.enums.SubmissionResult;
import com.einvoice.core.repository.SubmissionAttemptRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing submission attempt records. */
@Service
public class SubmissionAttemptService {

    private final SubmissionAttemptRepository attemptRepository;

    public SubmissionAttemptService(SubmissionAttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    /**
     * Creates a new submission attempt record.
     *
     * @param invoice the parent invoice
     * @param attemptNumber the sequential attempt number
     * @param config the authority configuration
     * @return the created attempt
     */
    @Transactional
    public SubmissionAttempt createAttempt(Invoice invoice, int attemptNumber,
                                           AuthorityConfig config) {
        SubmissionAttempt attempt = SubmissionAttempt.builder()
                .invoice(invoice)
                .attemptNumber(attemptNumber)
                .environment(invoice.getEnvironment())
                .authority(invoice.getAuthority())
                .result(SubmissionResult.ERROR)
                .submittedAt(OffsetDateTime.now())
                .build();
        return attemptRepository.save(attempt);
    }

    /**
     * Updates attempt record with the submission result.
     *
     * @param attempt the attempt to update
     * @param result the submission result
     */
    @Transactional
    public void completeAttempt(SubmissionAttempt attempt, SubmissionResultDto result) {
        attempt.setStatusCode(result.httpStatusCode());
        attempt.setResult(result.status());
        if (result.errors() != null && !result.errors().isEmpty()) {
            attempt.setErrorSummary(String.join("; ", result.errors()));
        }
        attempt.setCompletedAt(OffsetDateTime.now());
        attemptRepository.save(attempt);
    }

    public int getNextAttemptNumber(UUID invoiceId) {
        return attemptRepository.countByInvoiceId(invoiceId) + 1;
    }

    public List<SubmissionAttempt> getAttempts(UUID invoiceId) {
        return attemptRepository.findByInvoiceIdOrderByAttemptNumberAsc(invoiceId);
    }
}
