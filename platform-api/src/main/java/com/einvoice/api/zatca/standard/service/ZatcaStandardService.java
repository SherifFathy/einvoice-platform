package com.einvoice.api.zatca.standard.service;

import com.einvoice.api.audit.service.AuditService;
import com.einvoice.api.zatca.standard.service.ZatcaStandardFormMapper.ZatcaStandardResponse;
import com.einvoice.api.zatca.standard.service.ZatcaStandardFormMapper.ZatcaStandardWriteForm;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.error.DocumentNotDraftException;
import com.einvoice.core.error.DuplicateStandardNumberException;
import com.einvoice.core.error.InvalidLifecycleTransitionException;
import com.einvoice.core.error.MissingBuyerForStandardException;
import com.einvoice.core.error.MissingOriginalDocumentException;
import com.einvoice.core.error.OptimisticLockConflictException;
import com.einvoice.core.error.TotalsInconsistentException;
import com.einvoice.core.error.VatExemptionReasonRequiredException;
import com.einvoice.core.error.WrongOriginalClassException;
import com.einvoice.core.money.ZatcaMoneyMath;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.core.repository.support.OperationalRepositorySupport;
import com.einvoice.core.repository.support.ZatcaStandardSpecifications;
import com.einvoice.core.repository.zatca.ZatcaStandardHeaderRepository;
import com.einvoice.security.tenant.TenantContext;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ZatcaStandardService {

    private static final String UQ_ZATCA_STANDARD_NUMBER =
            "uq_zatca_standard_number";

    private final ZatcaStandardHeaderRepository repository;
    private final UserCompanyTransactionRoleRepository uctrRepository;
    private final CompanyRepository companyRepository;
    private final AuditService auditService;

    public ZatcaStandardService(
            ZatcaStandardHeaderRepository repository,
            UserCompanyTransactionRoleRepository uctrRepository,
            CompanyRepository companyRepository,
            AuditService auditService) {
        this.repository = repository;
        this.uctrRepository = uctrRepository;
        this.companyRepository = companyRepository;
        this.auditService = auditService;
    }

    /** List standard documents with optional filters. */
    @Transactional(readOnly = true)
    public Page<ZatcaStandardResponse> list(String status,
            UUID filterCompanyId, String dateFrom, String dateTo,
            int page, int size) {
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();
        List<UUID> assigned = getAssignedCompanyIds();

        Specification<com.einvoice.core.domain.zatca.ZatcaStandardHeader> spec =
                ZatcaStandardSpecifications.inActiveTenantAndAssignedCompany(
                        assigned, authEnvId);

        if (filterCompanyId != null) {
            if (assigned.contains(filterCompanyId)) {
                spec = spec.and(ZatcaStandardSpecifications.forCompany(
                        filterCompanyId));
            } else {
                spec = spec.and((root, q, cb) -> cb.disjunction());
            }
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and(ZatcaStandardSpecifications.inState(
                    DocumentState.valueOf(status)));
        }
        if (dateFrom != null || dateTo != null) {
            spec = spec.and(ZatcaStandardSpecifications.issuedBetween(
                    dateFrom != null ? java.time.LocalDate.parse(dateFrom)
                            : null,
                    dateTo != null ? java.time.LocalDate.parse(dateTo)
                            : null));
        }

        return repository.findAll(spec,
                PageRequest.of(page, size,
                        Sort.by(Sort.Direction.DESC, "issueDate")))
                .map(ZatcaStandardFormMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public ZatcaStandardResponse findById(UUID docId) {
        return ZatcaStandardFormMapper.toResponse(loadWithinTenant(docId));
    }

    /** Create a new standard document. */
    public ZatcaStandardResponse create(ZatcaStandardWriteForm form) {
        UUID companyId = TenantContext.getCompanyId();
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();

        validateBuyerRequired(form);
        validateVatExemptionReason(form);
        validateOriginalDocument(form, companyId, authEnvId);

        com.einvoice.core.domain.zatca.ZatcaStandardHeader header =
                ZatcaStandardFormMapper.toEntity(form, companyId,
                        authEnvId, TenantContext.getUserId());

        reconcileTotals(header);

        try {
            header = repository.saveAndFlush(header);
        } catch (DataIntegrityViolationException e) {
            throwIfUniqueConstraint(e, UQ_ZATCA_STANDARD_NUMBER,
                    () -> new DuplicateStandardNumberException(
                            "Duplicate standard number: "
                                    + form.invoiceNumber(),
                            companyId, form.invoiceNumber()));
            throw e;
        }

        auditService.record("CREATE", "ZATCA_STANDARD",
                header.getId().toString(), null,
                Map.of("invoiceNumber", form.invoiceNumber()));
        return ZatcaStandardFormMapper.toResponse(header);
    }

    /** Update an existing draft standard document. */
    public ZatcaStandardResponse update(UUID docId,
            ZatcaStandardWriteForm form, Long ifMatchVersion) {
        com.einvoice.core.domain.zatca.ZatcaStandardHeader header =
                loadWithinTenant(docId);

        if (header.getStatus() != DocumentState.DRAFT) {
            throw new DocumentNotDraftException(
                    "Only DRAFT documents can be edited",
                    header.getStatus().name());
        }
        if (ifMatchVersion != null
                && !ifMatchVersion.equals(
                        header.getVersion())) {
            throw new OptimisticLockConflictException("Version conflict",
                    ifMatchVersion != null
                            ? ifMatchVersion.intValue() : null,
                    header.getVersion() != null
                            ? header.getVersion().intValue() : null,
                    ZatcaStandardFormMapper.toResponse(header));
        }

        validateBuyerRequired(form);
        validateVatExemptionReason(form);
        validateOriginalDocument(form, header.getCompanyId(),
                header.getAuthorityEnvironmentId());

        header.setInvoiceNumber(form.invoiceNumber());
        header.setInvoiceTypeCode(form.invoiceTypeCode() != null
                ? form.invoiceTypeCode() : header.getInvoiceTypeCode());
        header.setTransactionTypeCode(form.transactionTypeCode());
        header.setBusinessProcessCode(form.businessProcessCode() != null
                ? form.businessProcessCode() : header.getBusinessProcessCode());
        header.setIssuanceReason(form.issuanceReason());
        header.setBillingReferenceId(form.billingReferenceId());
        header.setOriginalInvoiceNumber(form.originalInvoiceNumber());
        header.setErpReferenceId(form.erpReferenceId());
        header.setIssueDate(form.issueDate());
        header.setIssueTime(form.issueTime());
        header.setSupplyDate(form.supplyDate());
        header.setSupplyEndDate(form.supplyEndDate());
        header.setSellerData(form.sellerData());
        header.setBuyerData(form.buyerData());
        header.setCurrency(form.currency());
        header.setTaxCurrency(form.taxCurrency());
        header.setPrepaidAmount(form.prepaidAmount());
        header.setPaymentMeansCode(form.paymentMeansCode());
        header.setPaymentMeansText(form.paymentMeansText());
        header.setOriginalInvoiceId(form.originalInvoiceId());

        ZatcaStandardFormMapper.promoteSellerFields(header, form.sellerData());
        ZatcaStandardFormMapper.promoteBuyerFields(header, form.buyerData());

        header.getLines().clear();
        repository.flush();
        com.einvoice.core.domain.zatca.ZatcaStandardHeader persistentHeader =
                header;
        ZatcaStandardFormMapper.toEntity(form, header.getCompanyId(),
                header.getAuthorityEnvironmentId(),
                header.getCreatedBy())
                .getLines().forEach(line -> {
                    line.setHeader(persistentHeader);
                    persistentHeader.getLines().add(line);
                });

        reconcileTotals(header);

        UUID editCompanyId = header.getCompanyId();
        try {
            header = repository.saveAndFlush(header);
        } catch (DataIntegrityViolationException e) {
            throwIfUniqueConstraint(e, UQ_ZATCA_STANDARD_NUMBER,
                    () -> new DuplicateStandardNumberException(
                            "Duplicate standard number: "
                                    + form.invoiceNumber(),
                            editCompanyId, form.invoiceNumber()));
            throw e;
        }

        auditService.record("EDIT", "ZATCA_STANDARD",
                header.getId().toString(), null,
                Map.of("version", header.getVersion()));
        return ZatcaStandardFormMapper.toResponse(header);
    }

    /**
     * Delete a draft standard document.
     *
     * @param docId the document identifier
     */
    public void delete(UUID docId) {
        com.einvoice.core.domain.zatca.ZatcaStandardHeader header =
                loadWithinTenant(docId);
        if (header.getStatus() != DocumentState.DRAFT) {
            throw new DocumentNotDraftException(
                    "Only DRAFT documents can be deleted",
                    header.getStatus().name());
        }
        repository.delete(header);
        auditService.record("DELETE", "ZATCA_STANDARD",
                docId.toString(), null, null);
    }

    /**
     * Clone an existing document as a new draft.
     *
     * @param sourceDocId the source document identifier
     * @param newInvoiceNumber the invoice number for the clone
     * @return the cloned document response
     */
    public ZatcaStandardResponse cloneAsDraft(UUID sourceDocId,
            String newInvoiceNumber) {
        com.einvoice.core.domain.zatca.ZatcaStandardHeader source =
                loadWithinTenant(sourceDocId);

        UUID companyId = source.getCompanyId();
        Short authEnvId = source.getAuthorityEnvironmentId();

        com.einvoice.core.domain.zatca.ZatcaStandardHeader clone =
                com.einvoice.core.domain.zatca.ZatcaStandardHeader.builder()
                        .companyId(companyId)
                        .authorityEnvironmentId(authEnvId)
                        .invoiceNumber(newInvoiceNumber)
                        .invoiceTypeCode(source.getInvoiceTypeCode())
                        .transactionTypeCode(
                                source.getTransactionTypeCode())
                        .businessProcessCode(source.getBusinessProcessCode())
                        .issuanceReason(source.getIssuanceReason())
                        .billingReferenceId(source.getBillingReferenceId())
                        .originalInvoiceNumber(source.getOriginalInvoiceNumber())
                        .erpReferenceId(source.getErpReferenceId())
                        .issueDate(source.getIssueDate())
                        .issueTime(source.getIssueTime())
                        .supplyDate(source.getSupplyDate())
                        .supplyEndDate(source.getSupplyEndDate())
                        .sellerData(source.getSellerData())
                        .buyerData(source.getBuyerData())
                        .sellerVatNumber(source.getSellerVatNumber())
                        .sellerCountryCode(source.getSellerCountryCode())
                        .buyerVatNumber(source.getBuyerVatNumber())
                        .buyerCountryCode(source.getBuyerCountryCode())
                        .currency(source.getCurrency())
                        .taxCurrency(source.getTaxCurrency())
                        .lineExtensionAmount(
                                source.getLineExtensionAmount())
                        .taxExclusiveAmount(source.getTaxExclusiveAmount())
                        .taxAmount(source.getTaxAmount())
                        .taxAmountAccountingCurrency(
                                source.getTaxAmountAccountingCurrency())
                        .taxInclusiveAmount(
                                source.getTaxInclusiveAmount())
                        .prepaidAmount(source.getPrepaidAmount())
                        .roundingAmount(source.getRoundingAmount())
                        .payableAmount(source.getPayableAmount())
                        .paymentMeansCode(source.getPaymentMeansCode())
                        .paymentMeansText(source.getPaymentMeansText())
                        .originalInvoiceId(source.getOriginalInvoiceId())
                        .createdBy(TenantContext.getUserId())
                        .build();

        try {
            clone = repository.saveAndFlush(clone);
        } catch (DataIntegrityViolationException e) {
            throwIfUniqueConstraint(e, UQ_ZATCA_STANDARD_NUMBER,
                    () -> new DuplicateStandardNumberException(
                            "Duplicate standard number: "
                                    + newInvoiceNumber,
                            companyId, newInvoiceNumber));
            throw e;
        }

        auditService.record("CLONE_TO_NEW_DRAFT", "ZATCA_STANDARD",
                clone.getId().toString(),
                Map.of("sourceId", sourceDocId.toString()),
                Map.of("invoiceNumber", newInvoiceNumber));
        return ZatcaStandardFormMapper.toResponse(clone);
    }

    /**
     * Load a header within the current tenant context.
     *
     * @param docId the document identifier
     * @return the loaded header entity
     */
    public com.einvoice.core.domain.zatca.ZatcaStandardHeader loadWithinTenant(
            UUID docId) {
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();
        List<UUID> assigned = getAssignedCompanyIds();
        Specification<com.einvoice.core.domain.zatca.ZatcaStandardHeader> spec =
                ZatcaStandardSpecifications.inActiveTenantAndAssignedCompany(
                        assigned, authEnvId)
                        .and(OperationalRepositorySupport.idEquals(docId));
        return repository.findOne(spec)
                .orElseThrow(() -> new com.einvoice.core.error.ItemNotFoundException(
                        "Standard document not found"));
    }

    private void reconcileTotals(
            com.einvoice.core.domain.zatca.ZatcaStandardHeader header) {
        BigDecimal lineExtSum = BigDecimal.ZERO;
        BigDecimal vatSum = BigDecimal.ZERO;
        BigDecimal discountSum = BigDecimal.ZERO;
        BigDecimal allowanceSum = BigDecimal.ZERO;

        for (var line : header.getLines()) {
            BigDecimal lineExt = ZatcaMoneyMath.round2(
                    line.getQuantity().multiply(line.getUnitPrice(),
                            ZatcaMoneyMath.MC));
            line.setLineExtensionAmount(lineExt);

            BigDecimal net = lineExt
                    .subtract(line.getDiscountAmount() != null
                            ? line.getDiscountAmount() : BigDecimal.ZERO)
                    .subtract(line.getAllowanceAmount() != null
                            ? line.getAllowanceAmount() : BigDecimal.ZERO);
            line.setNetAmount(ZatcaMoneyMath.round2(net));

            lineExtSum = lineExtSum.add(lineExt);
            vatSum = vatSum.add(line.getVatAmount() != null
                    ? line.getVatAmount() : BigDecimal.ZERO);
            discountSum = discountSum.add(
                    line.getDiscountAmount() != null
                            ? line.getDiscountAmount() : BigDecimal.ZERO);
            allowanceSum = allowanceSum.add(
                    line.getAllowanceAmount() != null
                            ? line.getAllowanceAmount()
                            : BigDecimal.ZERO);
        }

        header.setLineExtensionAmount(ZatcaMoneyMath.round2(lineExtSum));
        header.setTaxExclusiveAmount(ZatcaMoneyMath.round2(
                lineExtSum.subtract(allowanceSum)));
        header.setTaxAmount(ZatcaMoneyMath.round2(vatSum));
        header.setTaxInclusiveAmount(ZatcaMoneyMath.round2(
                header.getTaxExclusiveAmount().add(vatSum)));
        header.setPayableAmount(ZatcaMoneyMath.round2(
                header.getTaxInclusiveAmount()
                        .subtract(header.getPrepaidAmount())
                        .add(header.getRoundingAmount() != null
                                ? header.getRoundingAmount()
                                : BigDecimal.ZERO)));

        if ("SAR".equals(header.getCurrency())) {
            header.setTaxAmountAccountingCurrency(header.getTaxAmount());
        }
    }

    private void validateBuyerRequired(ZatcaStandardWriteForm form) {
        if (form.buyerData() == null || form.buyerData().isEmpty()) {
            throw new MissingBuyerForStandardException(
                    "Buyer data is required for Standard documents");
        }
    }

    private void validateVatExemptionReason(
            ZatcaStandardWriteForm form) {
        if (form.lines() == null) {
            return;
        }
        for (var line : form.lines()) {
            if (line.vatCategoryCode() != null
                    && ("E".equals(line.vatCategoryCode())
                    || "O".equals(line.vatCategoryCode()))) {
                if (line.exemptionReasonCode() == null
                        || line.exemptionReasonCode().isBlank()
                        || line.exemptionReasonText() == null
                        || line.exemptionReasonText().isBlank()) {
                    throw new VatExemptionReasonRequiredException(
                            "VAT exemption reason required for category "
                                    + line.vatCategoryCode(),
                            line.vatCategoryCode());
                }
            }
        }
    }

    private void validateOriginalDocument(ZatcaStandardWriteForm form,
            UUID companyId, Short authEnvId) {
        if (form.invoiceTypeCode() == null) {
            return;
        }
        if (!"381".equals(form.invoiceTypeCode())
                && !"383".equals(form.invoiceTypeCode())) {
            return;
        }
        if (form.originalInvoiceId() == null) {
            throw new MissingOriginalDocumentException(
                    "Credit/debit note requires an original document",
                    null, form.invoiceTypeCode());
        }

        List<UUID> assigned = getAssignedCompanyIds();
        Specification<com.einvoice.core.domain.zatca.ZatcaStandardHeader> spec =
                ZatcaStandardSpecifications.inActiveTenantAndAssignedCompany(
                        assigned, authEnvId)
                        .and(OperationalRepositorySupport.idEquals(
                                form.originalInvoiceId()));
        var original = repository.findOne(spec)
                .orElseThrow(() -> new MissingOriginalDocumentException(
                        "Original document not found: "
                                + form.originalInvoiceId(),
                        form.originalInvoiceId(),
                        form.invoiceTypeCode()));

        if (!"388".equals(original.getInvoiceTypeCode())) {
            throw new WrongOriginalClassException(
                    "Original document must be a standard invoice (388), "
                            + "but was: " + original.getInvoiceTypeCode(),
                    "388", original.getInvoiceTypeCode());
        }
    }

    private List<UUID> getAssignedCompanyIds() {
        if (TenantContext.isSuperUser()) {
            return companyRepository.findByIsActiveTrue().stream()
                    .map(Company::getId).toList();
        }
        return uctrRepository.findDistinctAssignedCompanyIds(
                TenantContext.getUserId(),
                TenantContext.getAuthorityEnvironmentId());
    }

    private void throwIfUniqueConstraint(
            DataIntegrityViolationException e, String constraintName,
            Supplier<? extends RuntimeException> exSupplier) {
        Throwable cause = e.getMostSpecificCause();
        if (cause instanceof SQLException sqlEx) {
            if ("23505".equals(sqlEx.getSQLState())
                    && sqlEx.getMessage() != null
                    && sqlEx.getMessage().contains(constraintName)) {
                throw exSupplier.get();
            }
        }
    }
}
