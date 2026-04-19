package com.einvoice.eta.polling;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.InvoiceStatus;
import com.einvoice.core.repository.AuthorityConfigRepository;
import com.einvoice.core.repository.InvoiceRepository;
import com.einvoice.core.service.InvoiceStateMachine;
import com.einvoice.eta.client.EtaStatusClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Scheduled service that polls ETA for invoice status updates.
 */
@Service
public class EtaStatusPollingService {

    private static final Logger log = LoggerFactory.getLogger(EtaStatusPollingService.class);

    private final InvoiceRepository invoiceRepository;
    private final AuthorityConfigRepository authorityConfigRepository;
    private final EtaStatusClient statusClient;
    private final InvoiceStateMachine stateMachine;

    private volatile OffsetDateTime lastPollAt;

    /**
     * Creates a new EtaStatusPollingService.
     *
     * @param invoiceRepository the invoice repository
     * @param authorityConfigRepository the authority config repository
     * @param statusClient the ETA status client
     * @param stateMachine the invoice state machine
     */
    public EtaStatusPollingService(InvoiceRepository invoiceRepository,
            AuthorityConfigRepository authorityConfigRepository,
            EtaStatusClient statusClient,
            InvoiceStateMachine stateMachine) {
        this.invoiceRepository = invoiceRepository;
        this.authorityConfigRepository = authorityConfigRepository;
        this.statusClient = statusClient;
        this.stateMachine = stateMachine;
    }

    /**
     * Scheduled task that polls ETA for invoices in IN_REVIEW status.
     */
    @Scheduled(fixedDelayString = "${polling.eta.interval-ms:300000}")
    @Transactional
    public void pollInReviewInvoices() {
        List<Invoice> inReviewInvoices = invoiceRepository
                .findByStatusAndAuthority(InvoiceStatus.IN_REVIEW, Authority.ETA);

        if (inReviewInvoices.isEmpty()) {
            return;
        }

        log.debug("Polling {} ETA invoices in IN_REVIEW status", inReviewInvoices.size());

        for (Invoice invoice : inReviewInvoices) {
            try {
                Optional<AuthorityConfig> configOpt =
                        authorityConfigRepository.findByBranchIdAndAuthorityAndEnvironment(
                                invoice.getBranch().getId(),
                                Authority.ETA,
                                invoice.getEnvironment());
                if (configOpt.isEmpty() || !Boolean.TRUE.equals(configOpt.get().getPollingEnabled())) {
                    continue;
                }

                checkSingleInvoiceStatus(invoice, configOpt.get());
            } catch (Exception e) {
                log.warn("Error polling ETA status for invoice {}: {}",
                        invoice.getId(), e.getMessage());
            }
        }

        lastPollAt = OffsetDateTime.now();
    }

    /**
     * Checks the status of a single invoice against ETA.
     *
     * @param invoice the invoice to check
     * @param config the authority configuration
     * @return the current invoice status
     */
    @Transactional
    public InvoiceStatus checkSingleInvoiceStatus(Invoice invoice, AuthorityConfig config) {
        String externalRef = invoice.getExternalInvoiceReference();
        if (externalRef == null || externalRef.isBlank()) {
            externalRef = invoice.getInvoiceNumber();
        }

        if (externalRef == null) {
            return invoice.getStatus();
        }

        Optional<JsonNode> details = statusClient.getDocumentDetails(externalRef, config);
        if (details.isEmpty()) {
            return invoice.getStatus();
        }

        JsonNode doc = details.get();
        String status = doc.path("status").asText("");

        InvoiceStatus newStatus = switch (status.toUpperCase()) {
          case "ACCEPTED", "VALID" -> InvoiceStatus.ACCEPTED;
          case "REJECTED", "INVALID" -> InvoiceStatus.REJECTED;
          case "CANCELLED" -> InvoiceStatus.CANCELLED;
          default -> null;
        };

        if (newStatus != null && newStatus != invoice.getStatus()) {
            InvoiceStatus previousStatus = invoice.getStatus();
            stateMachine.transition(invoice, newStatus);
            invoiceRepository.save(invoice);
            log.info("ETA invoice {} status updated from {} to {}",
                    invoice.getId(), previousStatus, newStatus);
        }

        return invoice.getStatus();
    }

    /**
     * Gets the timestamp of the last poll.
     *
     * @return the last poll time, or empty if never polled
     */
    public Optional<OffsetDateTime> getLastPollAt() {
        return Optional.ofNullable(lastPollAt);
    }

    /**
     * Counts the number of invoices currently in IN_REVIEW status for ETA.
     *
     * @param companyId the company identifier for tenant scoping
     * @return the count of in-review invoices
     */
    public int countInReviewInvoices(Long companyId) {
        return (int) invoiceRepository.countByStatusAndAuthorityAndCompanyId(
                InvoiceStatus.IN_REVIEW, Authority.ETA, companyId);
    }
}
