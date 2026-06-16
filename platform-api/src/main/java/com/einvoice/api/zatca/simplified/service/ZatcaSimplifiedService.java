package com.einvoice.api.zatca.simplified.service;

import com.einvoice.api.audit.service.AuditService;
import com.einvoice.api.zatca.simplified.service.ZatcaSimplifiedFormMapper.ZatcaSimplifiedResponse;
import com.einvoice.api.zatca.simplified.service.ZatcaSimplifiedFormMapper.ZatcaSimplifiedWriteForm;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.error.DocumentNotDraftException;
import com.einvoice.core.error.DuplicateSimplifiedNumberException;
import com.einvoice.core.error.InvalidLifecycleTransitionException;
import com.einvoice.core.error.InvalidSimplifiedTransactionTypeException;
import com.einvoice.core.error.MissingOriginalDocumentException;
import com.einvoice.core.error.OptimisticLockConflictException;
import com.einvoice.core.error.TotalsInconsistentException;
import com.einvoice.core.error.VatExemptionReasonRequiredException;
import com.einvoice.core.error.WrongOriginalClassException;
import com.einvoice.core.money.ZatcaMoneyMath;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.core.repository.support.OperationalRepositorySupport;
import com.einvoice.core.repository.support.ZatcaSimplifiedSpecifications;
import com.einvoice.core.repository.zatca.ZatcaSimplifiedHeaderRepository;
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

/** Service layer for ZATCA Simplified (B2C) document CRUD. */
@Service
@Transactional
public class ZatcaSimplifiedService {

    private static final String UQ_ZATCA_SIMPLIFIED_NUMBER =
            "uq_zatca_simplified_number";

    private final ZatcaSimplifiedHeaderRepository repository;
    private final UserCompanyTransactionRoleRepository uctrRepository;
    private final CompanyRepository companyRepository;
    private final AuditService auditService;

    /**
     * Inject dependencies.
     *
     * @param repository the simplified header repository
     * @param uctrRepository the user-company transaction role repository
     * @param companyRepository the company repository
     * @param auditService the audit service
     */
    public ZatcaSimplifiedService(
            ZatcaSimplifiedHeaderRepository repository,
            UserCompanyTransactionRoleRepository uctrRepository,
            CompanyRepository companyRepository,
            AuditService auditService) {
        this.repository = repository;
        this.uctrRepository = uctrRepository;
        this.companyRepository = companyRepository;
        this.auditService = auditService;
    }

    /**
     * List simplified documents with optional filters.
     *
     * @param status optional document status filter
     * @param filterCompanyId optional company filter
     * @param branchId optional branch filter
     * @param dateFrom optional start date filter
     * @param dateTo optional end date filter
     * @param page the page number
     * @param size the page size
     * @return paginated list of simplified document responses
     */
    @Transactional(readOnly = true)
    public Page<ZatcaSimplifiedResponse> list(String status,
            UUID filterCompanyId, UUID branchId, String dateFrom,
            String dateTo, int page, int size) {
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();

        Specification<com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader>
                spec =
                ZatcaSimplifiedSpecifications.inActiveTenantAndAssignedCompany(
                        getAssignedCompanyIds(), authEnvId);

        if (filterCompanyId != null) {
            spec = spec.and(ZatcaSimplifiedSpecifications.forCompany(
                    filterCompanyId));
        }
        if (branchId != null) {
            spec = spec.and(ZatcaSimplifiedSpecifications.forBranch(branchId));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and(ZatcaSimplifiedSpecifications.inState(
                    DocumentState.valueOf(status)));
        }
        if (dateFrom != null || dateTo != null) {
            spec = spec.and(ZatcaSimplifiedSpecifications.issuedBetween(
                    dateFrom != null ? java.time.LocalDate.parse(dateFrom)
                            : null,
                    dateTo != null ? java.time.LocalDate.parse(dateTo)
                            : null));
        }

        return repository.findAll(spec,
                PageRequest.of(page, size,
                        Sort.by(Sort.Direction.DESC, "issueDate")))
                .map(ZatcaSimplifiedFormMapper::toResponse);
    }

    /**
     * Find a simplified document by ID within the current tenant context.
     *
     * @param docId document id
     * @return the simplified-document response
     */
    @Transactional(readOnly = true)
    public ZatcaSimplifiedResponse findById(UUID docId) {
        return ZatcaSimplifiedFormMapper.toResponse(
                loadWithinTenant(docId));
    }

    /**
     * Create a new simplified document in DRAFT state.
     *
     * @param companyId the owning company
     * @param form the validated write form
     * @return the created document response
     */
    public ZatcaSimplifiedResponse create(UUID companyId,
            ZatcaSimplifiedWriteForm form) {
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();

        validateTransactionTypeCode(form.transactionTypeCode());
        validateVatExemptionReason(form);
        validateOriginalDocument(form, companyId, authEnvId);

        com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader header =
                ZatcaSimplifiedFormMapper.toEntity(form, companyId,
                        authEnvId, TenantContext.getUserId());

        reconcileTotals(header);

        try {
            header = repository.saveAndFlush(header);
        } catch (DataIntegrityViolationException e) {
            throwIfUniqueConstraint(e, UQ_ZATCA_SIMPLIFIED_NUMBER,
                    () -> new DuplicateSimplifiedNumberException(
                            "Duplicate simplified number: "
                                    + form.invoiceNumber(),
                            companyId, form.invoiceNumber()));
            throw e;
        }

        auditService.record("CREATE", "ZATCA_SIMPLIFIED",
                header.getId().toString(), null,
                Map.of("invoiceNumber", form.invoiceNumber()));
        return ZatcaSimplifiedFormMapper.toResponse(header);
    }

    /**
     * Update an existing draft simplified document under optimistic locking.
     *
     * @param docId document id
     * @param form validated write form
     * @param ifMatchVersion version supplied via If-Match (nullable)
     * @return the updated document response
     */
    public ZatcaSimplifiedResponse update(UUID docId,
            ZatcaSimplifiedWriteForm form, Long ifMatchVersion) {
        com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader header =
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
                    ZatcaSimplifiedFormMapper.toResponse(header));
        }

        validateTransactionTypeCode(form.transactionTypeCode());
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

        ZatcaSimplifiedFormMapper.promoteSellerFields(header, form.sellerData());
        ZatcaSimplifiedFormMapper.promoteBuyerFields(header, form.buyerData());

        header.getLines().clear();
        repository.flush();
        com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader persistentHeader =
                header;
        ZatcaSimplifiedFormMapper.toEntity(form, header.getCompanyId(),
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
            throwIfUniqueConstraint(e, UQ_ZATCA_SIMPLIFIED_NUMBER,
                    () -> new DuplicateSimplifiedNumberException(
                            "Duplicate simplified number: "
                                    + form.invoiceNumber(),
                            editCompanyId, form.invoiceNumber()));
            throw e;
        }

        auditService.record("EDIT", "ZATCA_SIMPLIFIED",
                header.getId().toString(), null,
                Map.of("version", header.getVersion()));
        return ZatcaSimplifiedFormMapper.toResponse(header);
    }

    /**
     * Delete a draft simplified document.
     *
     * @param docId document id
     */
    public void delete(UUID docId) {
        com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader header =
                loadWithinTenant(docId);
        if (header.getStatus() != DocumentState.DRAFT) {
            throw new DocumentNotDraftException(
                    "Only DRAFT documents can be deleted",
                    header.getStatus().name());
        }
        repository.delete(header);
        auditService.record("DELETE", "ZATCA_SIMPLIFIED",
                docId.toString(), null, null);
    }

    /**
     * Clone an existing document as a new DRAFT carrying a fresh invoice number.
     *
     * @param sourceDocId source document id
     * @param newInvoiceNumber invoice number for the new draft
     * @return the newly-created draft response
     */
    public ZatcaSimplifiedResponse cloneAsDraft(UUID sourceDocId,
            String newInvoiceNumber) {
        com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader source =
                loadWithinTenant(sourceDocId);

        UUID companyId = source.getCompanyId();
        Short authEnvId = source.getAuthorityEnvironmentId();

        com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader clone =
                com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader
                        .builder()
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
                        .currency(source.getCurrency())
                        .taxCurrency(source.getTaxCurrency())
                        .lineExtensionAmount(
                                source.getLineExtensionAmount())
                        .taxExclusiveAmount(
                                source.getTaxExclusiveAmount())
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
                        .originalInvoiceId(
                                source.getOriginalInvoiceId())
                        .createdBy(TenantContext.getUserId())
                        .build();

        try {
            clone = repository.saveAndFlush(clone);
        } catch (DataIntegrityViolationException e) {
            throwIfUniqueConstraint(e, UQ_ZATCA_SIMPLIFIED_NUMBER,
                    () -> new DuplicateSimplifiedNumberException(
                            "Duplicate simplified number: "
                                    + newInvoiceNumber,
                            companyId, newInvoiceNumber));
            throw e;
        }

        auditService.record("CLONE_TO_NEW_DRAFT", "ZATCA_SIMPLIFIED",
                clone.getId().toString(),
                Map.of("sourceId", sourceDocId.toString()),
                Map.of("invoiceNumber", newInvoiceNumber));
        return ZatcaSimplifiedFormMapper.toResponse(clone);
    }

    /**
     * Load a header within the current tenant context.
     *
     * @param docId document id
     * @return the matching simplified-header entity
     */
    public com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader
            loadWithinTenant(UUID docId) {
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();
        Specification<com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader>
                spec =
                OperationalRepositorySupport.<com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader>
                        authorityEnvironmentIdEquals(authEnvId)
                        .and(OperationalRepositorySupport.idEquals(docId));
        UUID ctxCompanyId = TenantContext.getCompanyId();
        if (ctxCompanyId != null) {
            spec = spec.and(OperationalRepositorySupport.companyIdEquals(ctxCompanyId));
        }
        return repository.findOne(spec)
                .orElseThrow(
                        () -> new com.einvoice.core.error.ItemNotFoundException(
                                "Simplified document not found"));
    }

    private void validateTransactionTypeCode(String code) {
        if (code == null || !code.startsWith("02")) {
            throw new InvalidSimplifiedTransactionTypeException(
                    "Simplified transaction type code must start with 02,"
                            + " but was: " + code,
                    code);
        }
    }

    private void reconcileTotals(
            com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader header) {
        BigDecimal lineExtSum = BigDecimal.ZERO;
        BigDecimal vatSum = BigDecimal.ZERO;
        BigDecimal allowanceSum = BigDecimal.ZERO;

        for (var line : header.getLines()) {
            BigDecimal lineExt = ZatcaMoneyMath.round2(
                    line.getQuantity().multiply(line.getItemNetPrice(),
                            ZatcaMoneyMath.MC));
            line.setLineExtensionAmount(lineExt);

            BigDecimal lineAllowances = line.allowanceTotal();
            line.setNetAmount(ZatcaMoneyMath.round2(
                    lineExt.subtract(lineAllowances)));

            lineExtSum = lineExtSum.add(lineExt);
            vatSum = vatSum.add(line.getVatAmount() != null
                    ? line.getVatAmount() : BigDecimal.ZERO);
            allowanceSum = allowanceSum.add(lineAllowances);
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

    private void validateVatExemptionReason(
            ZatcaSimplifiedWriteForm form) {
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

    private void validateOriginalDocument(
            ZatcaSimplifiedWriteForm form,
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

        Specification<com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader>
                spec =
                OperationalRepositorySupport.<com.einvoice.core.domain.zatca.ZatcaSimplifiedHeader>
                        authorityEnvironmentIdEquals(authEnvId)
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
                    "Original document must be a simplified invoice (388),"
                            + " but was: "
                            + original.getInvoiceTypeCode(),
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
