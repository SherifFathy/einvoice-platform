package com.einvoice.api.eta.receipt.service;

import com.einvoice.api.eta.receipt.service.EtaReceiptFormMapper.EtaReceiptResponse;
import com.einvoice.api.eta.receipt.service.EtaReceiptFormMapper.EtaReceiptWriteForm;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.eta.EtaReceiptHeader;
import com.einvoice.core.domain.eta.document.EtaReceiptDocumentType;
import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.domain.shared.LifecycleAction;
import com.einvoice.core.error.DocumentNotDraftException;
import com.einvoice.core.error.DuplicateReceiptNumberException;
import com.einvoice.core.error.IncompatibleOriginalDocumentException;
import com.einvoice.core.error.InvalidLifecycleTransitionException;
import com.einvoice.core.error.InvalidUnitValueException;
import com.einvoice.core.error.MissingOriginalDocumentException;
import com.einvoice.core.error.OptimisticLockConflictException;
import com.einvoice.core.error.TotalsInconsistentException;
import com.einvoice.core.money.EtaMoneyMath;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.eta.EtaReceiptHeaderRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.core.repository.support.EtaReceiptSpecifications;
import com.einvoice.core.repository.support.OperationalRepositorySupport;
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

/** Service for ETA receipt CRUD and lifecycle operations. */
@Service
@Transactional
public class EtaReceiptService {

    private static final String UQ_ETA_RECEIPT_NUMBER = "uq_eta_receipt_number";

    private final EtaReceiptHeaderRepository repository;
    private final UserCompanyTransactionRoleRepository uctrRepository;
    private final CompanyRepository companyRepository;
    private final com.einvoice.api.audit.service.AuditService auditService;

    /**
     * Constructs an EtaReceiptService.
     *
     * @param repository the receipt header repository
     * @param uctrRepository the user-company-role repository
     * @param companyRepository the company repository
     * @param auditService the audit service
     */
    public EtaReceiptService(EtaReceiptHeaderRepository repository,
            UserCompanyTransactionRoleRepository uctrRepository,
            CompanyRepository companyRepository,
            com.einvoice.api.audit.service.AuditService auditService) {
        this.repository = repository;
        this.uctrRepository = uctrRepository;
        this.companyRepository = companyRepository;
        this.auditService = auditService;
    }

    /**
     * Lists receipts with optional filters.
     *
     * @param status optional state filter
     * @param filterCompanyId optional company filter
     * @param receiptType optional document type filter
     * @param dateFrom optional start date
     * @param dateTo optional end date
     * @param page page number
     * @param size page size
     * @return paged results
     */
    @Transactional(readOnly = true)
    public Page<EtaReceiptResponse> list(String status, UUID filterCompanyId,
            String receiptType, String dateFrom, String dateTo,
            int page, int size) {
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();

        Specification<EtaReceiptHeader> spec =
                OperationalRepositorySupport.<EtaReceiptHeader>
                        authorityEnvironmentIdEquals(authEnvId);

        if (filterCompanyId != null) {
            spec = spec.and(EtaReceiptSpecifications.forCompany(
                    filterCompanyId));
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and(EtaReceiptSpecifications.inState(
                    DocumentState.valueOf(status)));
        }
        if (receiptType != null && !receiptType.isBlank()) {
            spec = spec.and(EtaReceiptSpecifications.withDocumentType(
                    EtaReceiptDocumentType.valueOf(receiptType)));
        }
        if (dateFrom != null || dateTo != null) {
            spec = spec.and(EtaReceiptSpecifications.issuedBetween(
                    dateFrom != null ? java.time.LocalDate.parse(dateFrom)
                            : null,
                    dateTo != null ? java.time.LocalDate.parse(dateTo)
                            : null));
        }

        return repository.findAll(spec, PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "issueDatetime")))
                .map(EtaReceiptFormMapper::toResponse);
    }

    /**
     * Finds a receipt by ID within the current tenant.
     *
     * @param docId the document identifier
     * @return the receipt response
     */
    @Transactional(readOnly = true)
    public EtaReceiptResponse findById(UUID docId) {
        return EtaReceiptFormMapper.toResponse(loadWithinTenant(docId));
    }

    /**
     * Creates a new DRAFT receipt.
     *
     * @param companyId the owning company
     * @param form the write form
     * @return the created receipt response
     */
    public EtaReceiptResponse create(UUID companyId,
            EtaReceiptWriteForm form) {
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();

        validateOriginalDocument(form, companyId, authEnvId);
        validateUnitValues(form);

        EtaReceiptHeader header = EtaReceiptFormMapper.toEntity(form,
                companyId, authEnvId, TenantContext.getUserId());

        reconcileTotals(header);

        try {
            header = repository.saveAndFlush(header);
        } catch (DataIntegrityViolationException e) {
            throwIfUniqueConstraint(e, UQ_ETA_RECEIPT_NUMBER,
                    () -> new DuplicateReceiptNumberException(
                            "Duplicate receipt number: "
                                    + form.receiptNumber(),
                            companyId, form.receiptNumber()));
            throw e;
        }

        auditService.record("CREATE_RECEIPT", "ETA_RECEIPT",
                header.getId().toString(), null,
                Map.of("receiptNumber", form.receiptNumber()));
        return EtaReceiptFormMapper.toResponse(header);
    }

    /**
     * Updates an existing DRAFT receipt with optimistic locking.
     *
     * @param docId the document identifier
     * @param form the write form
     * @param ifMatchVersion the expected version from If-Match header
     * @return the updated receipt response
     */
    public EtaReceiptResponse update(UUID docId, EtaReceiptWriteForm form,
            Integer ifMatchVersion) {
        EtaReceiptHeader header = loadWithinTenant(docId);

        if (header.getState() != DocumentState.DRAFT) {
            throw new DocumentNotDraftException(
                    "Only DRAFT receipts can be edited",
                    header.getState().name());
        }
        if (ifMatchVersion != null
                && !ifMatchVersion.equals(header.getVersion())) {
            throw new OptimisticLockConflictException("Version conflict",
                    ifMatchVersion, header.getVersion(),
                    EtaReceiptFormMapper.toResponse(header));
        }

        validateOriginalDocument(form, header.getCompanyId(),
                header.getAuthorityEnvironmentId());
        validateUnitValues(form);

        header.setReceiptNumber(form.receiptNumber());
        header.setDocumentType(form.documentType());
        header.setDocumentTypeVersion(form.documentTypeVersion() != null
                ? form.documentTypeVersion()
                : header.getDocumentTypeVersion());
        header.setIssueDatetime(form.issueDatetime());
        header.setSellerData(form.sellerData());
        header.setBuyerData(form.buyerData());
        header.setPosSerial(form.posSerial());
        header.setPaymentMethod(form.paymentMethod());
        header.setCurrency(form.currency());
        header.setTotalSalesAmount(form.totalSalesAmount());
        header.setTotalCommercialDiscount(form.totalCommercialDiscount());
        header.setExtraDiscountAmount(form.extraDiscountAmount());
        header.setTotalItemsDiscountAmount(
                form.totalItemsDiscountAmount());
        header.setNetAmount(form.netAmount());
        header.setTotalAmount(form.totalAmount());
        header.setExchangeRate(form.exchangeRate());
        header.setPreviousUuid(form.previousUuid());
        header.setReferenceOldUuid(form.referenceOldUuid());
        header.setSOrderNameCode(form.sOrderNameCode());
        header.setOrderDeliveryMode(form.orderDeliveryMode());
        header.setGrossWeight(form.grossWeight());
        header.setNetWeight(form.netWeight());
        header.setTaxTotals(form.taxTotals());
        header.setExtraReceiptDiscountData(form.extraReceiptDiscountData());
        header.setContractorData(form.contractorData());
        header.setBeneficiaryData(form.beneficiaryData());
        header.setFeesAmount(form.feesAmount());
        header.setAdjustment(form.adjustment());
        header.setErpReferenceId(form.erpReferenceId());
        header.setOriginalInvoiceNumber(form.originalInvoiceNumber());
        header.setOriginalReceiptId(form.originalReceiptId());

        header.getLines().clear();
        repository.flush();
        EtaReceiptHeader persistentHeader = header;
        EtaReceiptFormMapper.toEntity(form, header.getCompanyId(),
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
            throwIfUniqueConstraint(e, UQ_ETA_RECEIPT_NUMBER,
                    () -> new DuplicateReceiptNumberException(
                            "Duplicate receipt number: "
                                    + form.receiptNumber(),
                            editCompanyId, form.receiptNumber()));
            throw e;
        }

        auditService.record("EDIT_RECEIPT", "ETA_RECEIPT",
                header.getId().toString(), null,
                Map.of("version", header.getVersion()));
        return EtaReceiptFormMapper.toResponse(header);
    }

    /**
     * Deletes a DRAFT receipt.
     *
     * @param docId the document identifier
     */
    public void delete(UUID docId) {
        EtaReceiptHeader header = loadWithinTenant(docId);
        if (header.getState() != DocumentState.DRAFT) {
            throw new DocumentNotDraftException(
                    "Only DRAFT receipts can be deleted",
                    header.getState().name());
        }
        repository.delete(header);
        auditService.record("DELETE_RECEIPT", "ETA_RECEIPT",
                docId.toString(), null, null);
    }

    /**
     * Clones a REJECTED receipt as a new DRAFT.
     *
     * @param rejectedDocId the rejected document identifier
     * @param newReceiptNumber the new receipt number
     * @return the cloned draft response
     */
    public EtaReceiptResponse cloneAsDraft(UUID rejectedDocId,
            String newReceiptNumber) {
        EtaReceiptHeader source = loadWithinTenant(rejectedDocId);
        if (source.getState() != DocumentState.REJECTED) {
            throw new InvalidLifecycleTransitionException(
                    "Source must be REJECTED to clone",
                    source.getState().name(), "CLONE_TO_NEW_DRAFT");
        }

        UUID companyId = source.getCompanyId();
        Short authEnvId = source.getAuthorityEnvironmentId();

        EtaReceiptHeader clone = EtaReceiptHeader.builder()
                .companyId(companyId)
                .authorityEnvironmentId(authEnvId)
                .receiptNumber(newReceiptNumber)
                .documentType(source.getDocumentType())
                .documentTypeVersion(source.getDocumentTypeVersion())
                .issueDatetime(source.getIssueDatetime())
                .sellerData(source.getSellerData())
                .buyerData(source.getBuyerData())
                .posSerial(source.getPosSerial())
                .paymentMethod(source.getPaymentMethod())
                .currency(source.getCurrency())
                .totalSalesAmount(source.getTotalSalesAmount())
                .totalCommercialDiscount(source.getTotalCommercialDiscount())
                .extraDiscountAmount(source.getExtraDiscountAmount())
                .totalItemsDiscountAmount(
                        source.getTotalItemsDiscountAmount())
                .netAmount(source.getNetAmount())
                .totalAmount(source.getTotalAmount())
                .exchangeRate(source.getExchangeRate())
                .previousUuid(source.getPreviousUuid())
                .referenceOldUuid(source.getReferenceOldUuid())
                .sOrderNameCode(source.getSOrderNameCode())
                .orderDeliveryMode(source.getOrderDeliveryMode())
                .grossWeight(source.getGrossWeight())
                .netWeight(source.getNetWeight())
                .taxTotals(source.getTaxTotals())
                .extraReceiptDiscountData(source.getExtraReceiptDiscountData())
                .contractorData(source.getContractorData())
                .beneficiaryData(source.getBeneficiaryData())
                .feesAmount(source.getFeesAmount())
                .adjustment(source.getAdjustment())
                .erpReferenceId(source.getErpReferenceId())
                .originalInvoiceNumber(source.getOriginalInvoiceNumber())
                .originalReceiptId(source.getOriginalReceiptId())
                .createdBy(TenantContext.getUserId())
                .build();

        try {
            clone = repository.saveAndFlush(clone);
        } catch (DataIntegrityViolationException e) {
            throwIfUniqueConstraint(e, UQ_ETA_RECEIPT_NUMBER,
                    () -> new DuplicateReceiptNumberException(
                            "Duplicate receipt number: "
                                    + newReceiptNumber,
                            companyId, newReceiptNumber));
            throw e;
        }

        auditService.record("CLONE_TO_NEW_DRAFT", "ETA_RECEIPT",
                clone.getId().toString(),
                Map.of("sourceId", rejectedDocId.toString()),
                Map.of("receiptNumber", newReceiptNumber));
        return EtaReceiptFormMapper.toResponse(clone);
    }

    private void reconcileTotals(EtaReceiptHeader header) {
        BigDecimal linesTotalSum = BigDecimal.ZERO;
        for (var line : header.getLines()) {
            BigDecimal lineDiscount = sumAmount(
                    line.getCommercialDiscountData());
            BigDecimal itemsDiscount = sumAmount(
                    line.getItemDiscountData());
            EtaMoneyMath.reconcileLineTotal(
                    line.getSalesTotal(), lineDiscount, itemsDiscount,
                    line.getValueDifference(),
                    line.getTotalTaxableFees(), line.getTaxAmount(),
                    line.getTotal());
            linesTotalSum = linesTotalSum.add(
                    line.getTotal() != null ? line.getTotal()
                            : BigDecimal.ZERO);
        }
        EtaMoneyMath.reconcileHeaderTotals(
                header.getTotalSalesAmount(),
                header.getTotalCommercialDiscount(),
                header.getExtraDiscountAmount(),
                header.getTotalItemsDiscountAmount(),
                header.getNetAmount(), header.getTotalAmount(),
                linesTotalSum);
    }

    private static BigDecimal sumAmount(
            com.fasterxml.jackson.databind.JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (com.fasterxml.jackson.databind.JsonNode n : arrayNode) {
            com.fasterxml.jackson.databind.JsonNode amt = n.path("amount");
            if (amt.isNumber()) {
                sum = sum.add(amt.decimalValue());
            }
        }
        return sum;
    }

    /**
     * Loads a receipt within the current tenant context.
     *
     * @param docId the document identifier
     * @return the receipt header
     */
    public EtaReceiptHeader loadWithinTenant(UUID docId) {
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();
        Specification<EtaReceiptHeader> spec =
                OperationalRepositorySupport.<EtaReceiptHeader>
                        authorityEnvironmentIdEquals(authEnvId)
                        .and(OperationalRepositorySupport.idEquals(docId));
        UUID ctxCompanyId = TenantContext.getCompanyId();
        if (ctxCompanyId != null) {
            spec = spec.and(OperationalRepositorySupport.companyIdEquals(ctxCompanyId));
        }
        return repository.findOne(spec)
                .orElseThrow(() -> new com.einvoice.core.error
                        .ItemNotFoundException("Receipt not found"));
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

    private void validateOriginalDocument(EtaReceiptWriteForm form,
            UUID companyId, Short authEnvId) {
        boolean requiresOriginal = form.documentType() != null
                && form.documentType().requiresOriginalDocument();

        if (!requiresOriginal) {
            if (form.originalReceiptId() != null) {
                throw new IncompatibleOriginalDocumentException(
                        "Receipt type " + form.documentType()
                                + " must not reference an original receipt",
                        null,
                        form.documentType().name());
            }
            return;
        }

        if (form.originalReceiptId() == null) {
            throw new MissingOriginalDocumentException(
                    "Receipt type " + form.documentType()
                            + " requires an original receipt",
                    null, form.documentType().name());
        }

        EtaReceiptHeader original = repository.findOne(
                OperationalRepositorySupport.<EtaReceiptHeader>
                        authorityEnvironmentIdEquals(authEnvId)
                        .and(OperationalRepositorySupport.idEquals(
                                form.originalReceiptId())))
                .orElseThrow(() -> new MissingOriginalDocumentException(
                        "Original receipt not found: "
                                + form.originalReceiptId(),
                        form.originalReceiptId(),
                        form.documentType().name()));

        EtaReceiptDocumentType expectedSourceType =
                compatibleSourceTypes(form.documentType());
        if (expectedSourceType != null
                && original.getDocumentType() != expectedSourceType) {
            throw new IncompatibleOriginalDocumentException(
                    "Original receipt must be of type "
                            + expectedSourceType.name()
                            + " but was " + original.getDocumentType().name(),
                    expectedSourceType.name(),
                    original.getDocumentType().name());
        }
    }

    private EtaReceiptDocumentType compatibleSourceTypes(
            EtaReceiptDocumentType subtype) {
        return switch (subtype) {
          case cr, crr, rr, rrwr -> EtaReceiptDocumentType.r;
          case rt, rtr -> EtaReceiptDocumentType.r;
          case tr, trr -> EtaReceiptDocumentType.r;
          case bk, bkr -> EtaReceiptDocumentType.r;
          case ed, edr -> EtaReceiptDocumentType.r;
          case pr, prr -> EtaReceiptDocumentType.r;
          case sh, shr -> EtaReceiptDocumentType.r;
          case en, enr -> EtaReceiptDocumentType.r;
          case ut, utr -> EtaReceiptDocumentType.r;
          default -> null;
        };
    }

    private void validateUnitValues(EtaReceiptWriteForm form) {
        if (form.lines() == null) {
            return;
        }
        for (var line : form.lines()) {
            if (line.unitPrice() == null) {
                throw new InvalidUnitValueException(
                        "Line unitPrice is required",
                        "unitPrice", List.of());
            }
        }
    }

    private void throwIfUniqueConstraint(DataIntegrityViolationException e,
            String constraintName,
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
