package com.einvoice.core.repository;

import com.einvoice.core.domain.SubmissionAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Append-only repository for {@link SubmissionAttempt} data access. */
public interface SubmissionAttemptRepository extends Repository<SubmissionAttempt, Long> {

    SubmissionAttempt save(SubmissionAttempt entity);

    List<SubmissionAttempt> findByInvoiceIdOrderByAttemptNumberAsc(UUID invoiceId);

    int countByInvoiceId(UUID invoiceId);
}
