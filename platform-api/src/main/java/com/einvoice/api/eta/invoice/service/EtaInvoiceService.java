package com.einvoice.api.eta.invoice.service;

import com.einvoice.api.audit.service.AuditService;
import com.einvoice.api.eta.invoice.service.EtaInvoiceFormMapper.EtaInvoiceResponse;
import com.einvoice.api.eta.invoice.service.EtaInvoiceFormMapper.EtaInvoiceWriteForm;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.eta.EtaInvoiceHeader;
import com.einvoice.core.domain.eta.document.EtaInvoiceDocumentType;
import com.einvoice.core.domain.eta.lifecycle.EtaInvoiceState;
import com.einvoice.core.domain.eta.lifecycle.LifecycleAction;
import com.einvoice.core.error.DocumentNotDraftException;
import com.einvoice.core.error.DuplicateInvoiceNumberException;
import com.einvoice.core.error.IncompatibleOriginalDocumentException;
import com.einvoice.core.error.InvalidLifecycleTransitionException;
import com.einvoice.core.error.InvalidUnitValueException;
import com.einvoice.core.error.MissingOriginalDocumentException;
import com.einvoice.core.error.OptimisticLockConflictException;
import com.einvoice.core.error.TotalsInconsistentException;
import com.einvoice.core.lifecycle.EtaInvoiceLifecycle;
import com.einvoice.core.money.EtaMoneyMath;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.eta.EtaInvoiceHeaderRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.core.repository.support.EtaInvoiceSpecifications;
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

/** Service for ETA invoice CRUD and lifecycle operations. */
@Service
@Transactional
public class EtaInvoiceService {

    private static final String UQ_ETA_INVOICE_NUMBER = "uq_eta_invoice_number";
    private static final List<String> REQUIRED_UNIT_VALUE_KEYS =
            List.of("currencySold", "amountEGP", "amountSold",
                    "currencyExchangeRate");

    private final EtaInvoiceHeaderRepository repository;
    private final UserCompanyTransactionRoleRepository uctrRepository;
    private final CompanyRepository companyRepository;
    private final AuditService auditService;

    /**
     * Constructs an EtaInvoiceService.
     *
     * @param repository the invoice header repository
     * @param uctrRepository the user-company-role repository
     * @param companyRepository the company repository
     * @param auditService the audit service
     */
    public EtaInvoiceService(EtaInvoiceHeaderRepository repository,
            UserCompanyTransactionRoleRepository uctrRepository,
            CompanyRepository companyRepository,
            AuditService auditService) {
        this.repository = repository;
        this.uctrRepository = uctrRepository;
        this.companyRepository = companyRepository;
        this.auditService = auditService;
    }

    /**
     * Lists invoices with optional filters.
     *
     * @param status optional state filter
     * @param filterCompanyId optional company filter
     * @param dateFrom optional start date
     * @param dateTo optional end date
     * @param page page number
     * @param size page size
     * @return paged results
     */
    @Transactional(readOnly = true)
    public Page<EtaInvoiceResponse> list(String status, UUID filterCompanyId,
            String dateFrom, String dateTo, int page, int size) {
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();
        List<UUID> assigned = getAssignedCompanyIds();

        Specification<EtaInvoiceHeader> spec =
                EtaInvoiceSpecifications.inActiveTenantAndAssignedCompany(assigned, authEnvId);

        if (filterCompanyId != null) {
            if (assigned.contains(filterCompanyId)) {
                spec = spec.and(EtaInvoiceSpecifications.forCompany(filterCompanyId));
            } else {
                spec = spec.and((root, q, cb) -> cb.disjunction());
            }
        }
        if (status != null && !status.isBlank()) {
            spec = spec.and(EtaInvoiceSpecifications.inState(EtaInvoiceState.valueOf(status)));
        }
        if (dateFrom != null || dateTo != null) {
            spec = spec.and(EtaInvoiceSpecifications.issuedBetween(
                    dateFrom != null ? java.time.LocalDate.parse(dateFrom) : null,
                    dateTo != null ? java.time.LocalDate.parse(dateTo) : null));
        }

        return repository.findAll(spec, PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "issueDatetime")))
                .map(EtaInvoiceFormMapper::toResponse);
    }

    /**
     * Finds an invoice by ID within the current tenant.
     *
     * @param docId the document identifier
     * @return the invoice response
     */
    @Transactional(readOnly = true)
    public EtaInvoiceResponse findById(UUID docId) {
        return EtaInvoiceFormMapper.toResponse(loadWithinTenant(docId));
    }

    /**
     * Creates a new DRAFT invoice.
     *
     * @param form the write form
     * @return the created invoice response
     */
    public EtaInvoiceResponse create(EtaInvoiceWriteForm form) {
        UUID companyId = TenantContext.getCompanyId();
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();

        validateOriginalDocument(form, companyId, authEnvId);
        validateUnitValues(form);

        EtaInvoiceHeader header = EtaInvoiceFormMapper.toEntity(form, companyId,
                authEnvId, TenantContext.getUserId());

        reconcileTotals(header);

        try {
            header = repository.saveAndFlush(header);
        } catch (DataIntegrityViolationException e) {
            throwIfUniqueConstraint(e, UQ_ETA_INVOICE_NUMBER,
                    () -> new DuplicateInvoiceNumberException(
                            "Duplicate invoice number: " + form.invoiceNumber(),
                            companyId, form.invoiceNumber()));
            throw e;
        }

        auditService.record("CREATE_INVOICE", "ETA_INVOICE",
                header.getId().toString(), null, Map.of("invoiceNumber", form.invoiceNumber()));
        return EtaInvoiceFormMapper.toResponse(header);
    }

    /**
     * Updates an existing DRAFT invoice with optimistic locking.
     *
     * @param docId the document identifier
     * @param form the write form
     * @param ifMatchVersion the expected version from If-Match header
     * @return the updated invoice response
     */
    public EtaInvoiceResponse update(UUID docId, EtaInvoiceWriteForm form, Integer ifMatchVersion) {
        EtaInvoiceHeader header = loadWithinTenant(docId);

        if (header.getState() != EtaInvoiceState.DRAFT) {
            throw new DocumentNotDraftException(
                    "Only DRAFT invoices can be edited",
                    header.getState().name());
        }
        if (ifMatchVersion != null && !ifMatchVersion.equals(header.getVersion())) {
            throw new OptimisticLockConflictException("Version conflict",
                    ifMatchVersion, header.getVersion(),
                    EtaInvoiceFormMapper.toResponse(header));
        }

        validateOriginalDocument(form, header.getCompanyId(), header.getAuthorityEnvironmentId());
        validateUnitValues(form);

        header.setInvoiceNumber(form.invoiceNumber());
        header.setDocumentType(form.documentType());
        header.setDocumentTypeVersion(form.documentTypeVersion());
        header.setIssueDatetime(form.issueDatetime());
        header.setServiceDeliveryDate(form.serviceDeliveryDate());
        header.setSellerData(form.sellerData());
        header.setBuyerData(form.buyerData());
        header.setTaxpayerActivityCode(form.taxpayerActivityCode());
        header.setPurchaseOrderReference(form.purchaseOrderReference());
        header.setPurchaseOrderDescription(form.purchaseOrderDescription());
        header.setSalesOrderReference(form.salesOrderReference());
        header.setSalesOrderDescription(form.salesOrderDescription());
        header.setProformaInvoiceNumber(form.proformaInvoiceNumber());
        header.setPaymentData(form.paymentData());
        header.setDeliveryData(form.deliveryData());
        header.setCurrency(form.currency());
        header.setTotalSalesAmount(form.totalSalesAmount());
        header.setTotalDiscountAmount(form.totalDiscountAmount());
        header.setExtraDiscountAmount(form.extraDiscountAmount());
        header.setTotalItemsDiscountAmount(form.totalItemsDiscountAmount());
        header.setNetAmount(form.netAmount());
        header.setTotalAmount(form.totalAmount());
        header.setOriginalDocumentId(form.originalDocumentId());

        header.getLines().clear();
        EtaInvoiceFormMapper.toEntity(form, header.getCompanyId(),
                header.getAuthorityEnvironmentId(), header.getCreatedBy())
                .getLines().forEach(header.getLines()::add);

        reconcileTotals(header);

        UUID editCompanyId = header.getCompanyId();
        try {
            header = repository.saveAndFlush(header);
        } catch (DataIntegrityViolationException e) {
            throwIfUniqueConstraint(e, UQ_ETA_INVOICE_NUMBER,
                    () -> new DuplicateInvoiceNumberException(
                            "Duplicate invoice number: "
                                    + form.invoiceNumber(),
                            editCompanyId, form.invoiceNumber()));
            throw e;
        }

        auditService.record("EDIT_INVOICE", "ETA_INVOICE",
                header.getId().toString(), null, Map.of("version", header.getVersion()));
        return EtaInvoiceFormMapper.toResponse(header);
    }

    /**
     * Deletes a DRAFT invoice.
     *
     * @param docId the document identifier
     */
    public void delete(UUID docId) {
        EtaInvoiceHeader header = loadWithinTenant(docId);
        if (header.getState() != EtaInvoiceState.DRAFT) {
            throw new DocumentNotDraftException(
                    "Only DRAFT invoices can be deleted",
                    header.getState().name());
        }
        repository.delete(header);
        auditService.record("DELETE_INVOICE", "ETA_INVOICE",
                docId.toString(), null, null);
    }

    /**
     * Clones a REJECTED invoice as a new DRAFT.
     *
     * @param rejectedDocId the rejected document identifier
     * @param newInvoiceNumber the new invoice number
     * @return the cloned draft response
     */
    public EtaInvoiceResponse cloneAsDraft(UUID rejectedDocId, String newInvoiceNumber) {
        EtaInvoiceHeader source = loadWithinTenant(rejectedDocId);
        if (source.getState() != EtaInvoiceState.REJECTED) {
            throw new InvalidLifecycleTransitionException(
                    "Source must be REJECTED to clone",
                    source.getState().name(), "CLONE_TO_NEW_DRAFT");
        }

        UUID companyId = source.getCompanyId();
        Short authEnvId = source.getAuthorityEnvironmentId();

        EtaInvoiceHeader clone = EtaInvoiceHeader.builder()
                .companyId(companyId)
                .authorityEnvironmentId(authEnvId)
                .invoiceNumber(newInvoiceNumber)
                .documentType(source.getDocumentType())
                .documentTypeVersion(source.getDocumentTypeVersion())
                .issueDatetime(source.getIssueDatetime())
                .serviceDeliveryDate(source.getServiceDeliveryDate())
                .sellerData(source.getSellerData())
                .buyerData(source.getBuyerData())
                .taxpayerActivityCode(source.getTaxpayerActivityCode())
                .purchaseOrderReference(source.getPurchaseOrderReference())
                .purchaseOrderDescription(source.getPurchaseOrderDescription())
                .salesOrderReference(source.getSalesOrderReference())
                .salesOrderDescription(source.getSalesOrderDescription())
                .proformaInvoiceNumber(source.getProformaInvoiceNumber())
                .paymentData(source.getPaymentData())
                .deliveryData(source.getDeliveryData())
                .currency(source.getCurrency())
                .totalSalesAmount(source.getTotalSalesAmount())
                .totalDiscountAmount(source.getTotalDiscountAmount())
                .extraDiscountAmount(source.getExtraDiscountAmount())
                .totalItemsDiscountAmount(source.getTotalItemsDiscountAmount())
                .netAmount(source.getNetAmount())
                .totalAmount(source.getTotalAmount())
                .originalDocumentId(source.getOriginalDocumentId())
                .createdBy(TenantContext.getUserId())
                .build();

        try {
            clone = repository.saveAndFlush(clone);
        } catch (DataIntegrityViolationException e) {
            throwIfUniqueConstraint(e, UQ_ETA_INVOICE_NUMBER,
                    () -> new DuplicateInvoiceNumberException(
                            "Duplicate invoice number: " + newInvoiceNumber,
                            companyId, newInvoiceNumber));
            throw e;
        }

        auditService.record("CLONE_TO_NEW_DRAFT", "ETA_INVOICE",
                clone.getId().toString(),
                Map.of("sourceId", rejectedDocId.toString()),
                Map.of("invoiceNumber", newInvoiceNumber));
        return EtaInvoiceFormMapper.toResponse(clone);
    }

    private void reconcileTotals(EtaInvoiceHeader header) {
        java.math.BigDecimal linesTotalSum = BigDecimal.ZERO;
        for (var line : header.getLines()) {
            EtaMoneyMath.reconcileLineTotal(
                    line.getSalesTotal(), line.getDiscountAmount(),
                    line.getItemsDiscount(), line.getValueDifference(),
                    line.getTotalTaxableFees(), line.getTaxAmount(),
                    line.getTotal());
            linesTotalSum = linesTotalSum.add(
                    line.getTotal() != null ? line.getTotal()
                            : BigDecimal.ZERO);
        }
        EtaMoneyMath.reconcileHeaderTotals(
                header.getTotalSalesAmount(),
                header.getTotalDiscountAmount(),
                header.getExtraDiscountAmount(),
                header.getTotalItemsDiscountAmount(),
                header.getNetAmount(), header.getTotalAmount(),
                linesTotalSum);
    }

    /**
     * Loads an invoice within the current tenant context.
     *
     * @param docId the document identifier
     * @return the invoice header
     */
    public EtaInvoiceHeader loadWithinTenant(UUID docId) {
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();
        List<UUID> assigned = getAssignedCompanyIds();
        Specification<EtaInvoiceHeader> spec =
                EtaInvoiceSpecifications.inActiveTenantAndAssignedCompany(assigned, authEnvId)
                        .and(OperationalRepositorySupport.idEquals(docId));
        return repository.findOne(spec)
                .orElseThrow(() -> new com.einvoice.core.error.ItemNotFoundException(
                        "Invoice not found"));
    }

    private List<UUID> getAssignedCompanyIds() {
        if (TenantContext.isSuperUser()) {
            return companyRepository.findByIsActiveTrue().stream()
                    .map(Company::getId).toList();
        }
        return uctrRepository.findDistinctAssignedCompanyIds(
                TenantContext.getUserId(), TenantContext.getAuthorityEnvironmentId());
    }

    private void validateOriginalDocument(EtaInvoiceWriteForm form, UUID companyId,
            Short authEnvId) {
        if (form.documentType() == null
                || !form.documentType().requiresOriginalDocument()) {
            return;
        }
        if (form.originalDocumentId() == null) {
            throw new MissingOriginalDocumentException(
                    "Document type " + form.documentType()
                            + " requires an original document",
                    null, form.documentType().name());
        }

        Specification<EtaInvoiceHeader> spec =
                EtaInvoiceSpecifications.inActiveTenantAndAssignedCompany(
                        getAssignedCompanyIds(), authEnvId)
                        .and(OperationalRepositorySupport.idEquals(
                                form.originalDocumentId()));
        EtaInvoiceHeader original = repository.findOne(spec)
                .orElseThrow(() -> new MissingOriginalDocumentException(
                        "Original document not found: "
                                + form.originalDocumentId(),
                        form.originalDocumentId(),
                        form.documentType().name()));

        EtaInvoiceDocumentType expected = expectedOriginalType(form.documentType());
        if (original.getDocumentType() != expected) {
            throw new IncompatibleOriginalDocumentException(
                    "Original document type must be " + expected
                            + " but was " + original.getDocumentType(),
                    expected.name(),
                    original.getDocumentType().name());
        }
    }

    private static EtaInvoiceDocumentType expectedOriginalType(
            EtaInvoiceDocumentType noteType) {
        return switch (noteType) {
          case c, d -> EtaInvoiceDocumentType.i;
          case ec, ed -> EtaInvoiceDocumentType.ei;
          default -> EtaInvoiceDocumentType.i;
        };
    }

    private void validateUnitValues(EtaInvoiceWriteForm form) {
        if (form.lines() == null) {
            return;
        }
        for (var line : form.lines()) {
            if (line.unitValue() == null) {
                throw new InvalidUnitValueException(
                        "Line unitValue is required",
                        "unitValue", List.of());
            }
            List<String> missing = REQUIRED_UNIT_VALUE_KEYS.stream()
                    .filter(key -> !line.unitValue().containsKey(key))
                    .toList();
            if (!missing.isEmpty()) {
                throw new InvalidUnitValueException(
                        "Line unitValue missing required keys: " + missing,
                        "unitValue", missing);
            }
        }
    }

    private void throwIfUniqueConstraint(DataIntegrityViolationException e,
            String constraintName, Supplier<? extends RuntimeException> exSupplier) {
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
