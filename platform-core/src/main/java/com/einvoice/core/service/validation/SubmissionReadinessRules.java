package com.einvoice.core.service.validation;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.repository.AuthorityConfigRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Validates that an invoice is ready for submission to an authority. */
@Component
public class SubmissionReadinessRules implements ValidationRule {

    private final AuthorityConfigRepository authorityConfigRepository;

    public SubmissionReadinessRules(AuthorityConfigRepository authorityConfigRepository) {
        this.authorityConfigRepository = authorityConfigRepository;
    }

    @Override
    public List<ValidationError> validate(Invoice invoice, Authority authority) {
        List<ValidationError> errors = new ArrayList<>();
        AuthorityConfig config = resolveConfig(invoice, authority);
        validateConfigActive(config, errors);
        validateCredentialsNotExpired(config, errors);
        validateSequenceValid(invoice, config, errors);
        return errors;
    }

    private AuthorityConfig resolveConfig(Invoice invoice, Authority authority) {
        if (invoice.getBranch() == null) {
            return null;
        }
        return authorityConfigRepository
                .findByBranchIdAndAuthorityAndEnvironment(
                        invoice.getBranch().getId(),
                        authority,
                        invoice.getEnvironment())
                .orElse(null);
    }

    private void validateConfigActive(AuthorityConfig config, List<ValidationError> errors) {
        if (config == null) {
            errors.add(new ValidationError(
                    ValidationLayer.READINESS, null, "READY-001",
                    "authorityConfig", "Authority configuration not found for this branch",
                    ValidationSeverity.ERROR));
            return;
        }
        if (!Boolean.TRUE.equals(config.getIsActive())) {
            errors.add(new ValidationError(
                    ValidationLayer.READINESS, null, "READY-001",
                    "authorityConfig", "Authority configuration is not active",
                    ValidationSeverity.ERROR));
        }
    }

    private void validateCredentialsNotExpired(AuthorityConfig config, List<ValidationError> errors) {
        if (config == null) {
            return;
        }
        boolean hasCredentials = config.getCsidEncrypted() != null
                || config.getTokenDataEncrypted() != null;
        if (!hasCredentials) {
            errors.add(new ValidationError(
                    ValidationLayer.READINESS, null, "READY-002",
                    "credentials", "No credentials configured for this authority",
                    ValidationSeverity.ERROR));
        }
        if (config.getCertificateExpiryDate() != null
                && config.getCertificateExpiryDate().isBefore(OffsetDateTime.now())) {
            errors.add(new ValidationError(
                    ValidationLayer.READINESS, null, "READY-002",
                    "certificateExpiry", "Authority certificate has expired",
                    ValidationSeverity.ERROR));
        }
    }

    private void validateSequenceValid(Invoice invoice, AuthorityConfig config, List<ValidationError> errors) {
        if (config == null) {
            return;
        }
        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()) {
            errors.add(new ValidationError(
                    ValidationLayer.READINESS, null, "READY-003",
                    "invoiceNumber", "Invoice must have a sequence number assigned",
                    ValidationSeverity.ERROR));
        }
    }
}
