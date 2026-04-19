package com.einvoice.eta.validation;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.InvoiceType;
import com.einvoice.core.repository.AuthorityConfigRepository;
import com.einvoice.core.service.validation.ValidationError;
import com.einvoice.core.service.validation.ValidationLayer;
import com.einvoice.core.service.validation.ValidationRule;
import com.einvoice.core.service.validation.ValidationSeverity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * ETA-specific compliance validation rules for invoices.
 */
@Component
public class EtaComplianceRules implements ValidationRule {

    private static final String ETA_001 = "ETA-001";
    private static final String ETA_002 = "ETA-002";

    private final AuthorityConfigRepository authorityConfigRepository;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new EtaComplianceRules.
     *
     * @param authorityConfigRepository the authority config repository
     * @param objectMapper the JSON object mapper
     */
    public EtaComplianceRules(AuthorityConfigRepository authorityConfigRepository,
            ObjectMapper objectMapper) {
        this.authorityConfigRepository = authorityConfigRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ValidationError> validate(Invoice invoice, Authority authority) {
        if (authority != Authority.ETA) {
            return List.of();
        }

        List<ValidationError> errors = new ArrayList<>();
        errors.addAll(validateDocumentTypeSchema(invoice));
        errors.addAll(validateEnabledDocumentType(invoice));
        return errors;
    }

    private List<ValidationError> validateDocumentTypeSchema(Invoice invoice) {
        List<ValidationError> errors = new ArrayList<>();

        if (invoice.getCompany() == null) {
            errors.add(createError(ETA_001, "company",
                    "Company data is required for ETA invoices"));
            return errors;
        }

        if (invoice.getBuyer() == null && invoice.getBuyerData() == null) {
            if (invoice.getType() == InvoiceType.TAX_INVOICE) {
                errors.add(createWarning(ETA_001, "buyer",
                        "B2B tax invoices should have buyer information"));
            }
        }

        if (invoice.getTotalWithVat() == null) {
            errors.add(createError(ETA_001, "totalWithVat",
                    "Total with VAT is required for ETA invoices"));
        }

        if (invoice.getTotalVat() == null) {
            errors.add(createError(ETA_001, "totalVat",
                    "Total VAT is required for ETA invoices"));
        }

        return errors;
    }

    private List<ValidationError> validateEnabledDocumentType(Invoice invoice) {
        List<ValidationError> errors = new ArrayList<>();

        if (invoice.getBranch() == null) {
            return errors;
        }

        var configOpt = authorityConfigRepository
                .findByBranchIdAndAuthorityAndEnvironment(
                        invoice.getBranch().getId(),
                        Authority.ETA,
                        invoice.getEnvironment());

        if (configOpt.isEmpty()) {
            errors.add(createError(ETA_002, "authority",
                    "ETA authority configuration not found for this branch"));
            return errors;
        }

        AuthorityConfig config = configOpt.get();
        String enabledDocTypes = config.getEnabledDocumentTypes();

        if (enabledDocTypes == null || enabledDocTypes.isBlank()
                || "[]".equals(enabledDocTypes.trim())) {
            return errors;
        }

        String docTypeCode = resolveDocTypeCode(invoice.getType());
        if (docTypeCode == null) {
            return errors;
        }

        try {
            JsonNode typesArray = objectMapper.readTree(enabledDocTypes);
            boolean found = false;
            if (typesArray.isArray()) {
                for (JsonNode node : typesArray) {
                    if (docTypeCode.equals(node.asText())) {
                        found = true;
                        break;
                    }
                }
            }
            if (!found) {
                errors.add(createError(ETA_002, "type",
                        "Document type " + docTypeCode
                                + " is not enabled in ETA configuration"));
            }
        } catch (Exception e) {
            errors.add(createWarning(ETA_002, "enabledDocumentTypes",
                    "Could not parse enabled document types configuration"));
        }

        return errors;
    }

    private String resolveDocTypeCode(InvoiceType type) {
        return switch (type) {
          case TAX_INVOICE -> "I";
          case SIMPLIFIED_TAX_INVOICE -> "S";
          case CREDIT_NOTE -> "C";
          case DEBIT_NOTE -> "D";
        };
    }

    private ValidationError createError(String ruleId, String field, String message) {
        return new ValidationError(ValidationLayer.COMPLIANCE, Authority.ETA,
                ruleId, field, message, ValidationSeverity.ERROR);
    }

    private ValidationError createWarning(String ruleId, String field, String message) {
        return new ValidationError(ValidationLayer.COMPLIANCE, Authority.ETA,
                ruleId, field, message, ValidationSeverity.WARNING);
    }
}
