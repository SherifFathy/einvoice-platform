package com.einvoice.core.service;

import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.enums.InvoiceStatus;
import com.einvoice.core.exception.InvalidTransitionException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Manages invoice lifecycle state transitions with audit logging. */
@Service
public class InvoiceStateMachine {

    private static final Map<InvoiceStatus, Set<InvoiceStatus>> TRANSITIONS;

    static {
        Map<InvoiceStatus, Set<InvoiceStatus>> map = new EnumMap<>(InvoiceStatus.class);
        map.put(InvoiceStatus.DRAFT, EnumSet.of(
                InvoiceStatus.VALIDATED,
                InvoiceStatus.CANCELLED));
        map.put(InvoiceStatus.VALIDATED, EnumSet.of(
                InvoiceStatus.READY_FOR_SUBMISSION));
        map.put(InvoiceStatus.READY_FOR_SUBMISSION, EnumSet.of(
                InvoiceStatus.SUBMISSION_IN_PROGRESS));
        map.put(InvoiceStatus.SUBMISSION_IN_PROGRESS, EnumSet.of(
                InvoiceStatus.CLEARED,
                InvoiceStatus.REPORTED,
                InvoiceStatus.ACCEPTED,
                InvoiceStatus.IN_REVIEW,
                InvoiceStatus.REJECTED,
                InvoiceStatus.FAILED_RETRYABLE,
                InvoiceStatus.FAILED_NON_RETRYABLE,
                InvoiceStatus.SUBMISSION_AMBIGUOUS));
        map.put(InvoiceStatus.REJECTED, EnumSet.of(
                InvoiceStatus.DRAFT));
        map.put(InvoiceStatus.FAILED_RETRYABLE, EnumSet.of(
                InvoiceStatus.SUBMISSION_IN_PROGRESS));
        map.put(InvoiceStatus.IN_REVIEW, EnumSet.of(
                InvoiceStatus.ACCEPTED,
                InvoiceStatus.REJECTED));
        map.put(InvoiceStatus.ACCEPTED, EnumSet.of(
                InvoiceStatus.CANCELLED));
        TRANSITIONS = Collections.unmodifiableMap(map);
    }

    private final AuditService auditService;

    public InvoiceStateMachine(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Transitions invoice to target state if allowed, otherwise throws.
     *
     * @param invoice the invoice to transition
     * @param target the target status
     */
    public void transition(Invoice invoice, InvoiceStatus target) {
        InvoiceStatus current = invoice.getStatus();
        Set<InvoiceStatus> allowed = TRANSITIONS.get(current);
        if (allowed == null || !allowed.contains(target)) {
            throw new InvalidTransitionException(current, target);
        }
        invoice.setStatus(target);
        auditService.log(
                "STATUS_TRANSITION",
                "Invoice",
                invoice.getId().toString(),
                current.name(),
                target.name(),
                invoice.getCompany().getId()
        );
    }

    public boolean canTransition(InvoiceStatus from, InvoiceStatus to) {
        Set<InvoiceStatus> allowed = TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }

    public Set<InvoiceStatus> getAllowedTransitions(InvoiceStatus from) {
        return TRANSITIONS.getOrDefault(from, Collections.emptySet());
    }
}
