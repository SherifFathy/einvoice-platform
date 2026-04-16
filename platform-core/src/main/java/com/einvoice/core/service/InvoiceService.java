package com.einvoice.core.service;

import com.einvoice.core.audit.Audited;
import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Customer;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.InvoiceLine;
import com.einvoice.core.domain.User;
import com.einvoice.core.domain.enums.InvoiceStatus;
import com.einvoice.core.domain.enums.InvoiceType;
import com.einvoice.core.repository.BranchRepository;
import com.einvoice.core.repository.CompanyRepository;
import com.einvoice.core.repository.CustomerRepository;
import com.einvoice.core.repository.InvoiceRepository;
import com.einvoice.core.repository.ItemRepository;
import com.einvoice.core.repository.UserRepository;
import com.einvoice.core.service.InvoiceValidationService.ValidationError;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates draft invoice creation, update, cancellation, and querying. */
@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final CompanyRepository companyRepository;
    private final BranchRepository branchRepository;
    private final CustomerRepository customerRepository;
    private final ItemRepository itemRepository;
    private final UserRepository userRepository;
    private final InvoiceCalculationService calculationService;
    private final InvoiceValidationService validationService;
    private final InvoiceNumberService invoiceNumberService;

    /**
     * Creates the invoice service.
     *
     * @param invoiceRepository the invoice repository
     * @param companyRepository the company repository
     * @param branchRepository the branch repository
     * @param customerRepository the customer repository
     * @param itemRepository the item repository
     * @param userRepository the user repository
     * @param calculationService the calculation service
     * @param validationService the validation service
     * @param invoiceNumberService the invoice number service
     */
    public InvoiceService(InvoiceRepository invoiceRepository,
            CompanyRepository companyRepository,
            BranchRepository branchRepository,
            CustomerRepository customerRepository,
            ItemRepository itemRepository,
            UserRepository userRepository,
            InvoiceCalculationService calculationService,
            InvoiceValidationService validationService,
            InvoiceNumberService invoiceNumberService) {
        this.invoiceRepository = invoiceRepository;
        this.companyRepository = companyRepository;
        this.branchRepository = branchRepository;
        this.customerRepository = customerRepository;
        this.itemRepository = itemRepository;
        this.userRepository = userRepository;
        this.calculationService = calculationService;
        this.validationService = validationService;
        this.invoiceNumberService = invoiceNumberService;
    }

    /**
     * Creates a new draft invoice with auto-calculated totals and generated number.
     *
     * @param invoice the invoice to create
     * @return the persisted invoice
     */
    @Transactional
    @PreAuthorize("hasAuthority('CREATE')")
    @Audited(action = "invoice.create", entityType = "Invoice")
    public Invoice createDraft(Invoice invoice) {
        Long companyId = TenantContext.getCurrentTenantId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new InvoiceNotFoundException(
                        "Company not found: " + companyId));
        invoice.setCompany(company);

        resolveRelations(invoice, companyId);

        List<ValidationError> errors = validationService.validate(invoice);
        if (!errors.isEmpty()) {
            throw new InvoiceValidationException(errors);
        }

        calculationService.recalculate(invoice);
        invoiceNumberService.generate(invoice);

        return invoiceRepository.save(invoice);
    }

    /**
     * Updates an existing draft invoice (recalculates totals).
     *
     * @param invoiceId the invoice identifier
     * @param updates the invoice updates to apply
     * @return the updated invoice
     */
    @Transactional
    @PreAuthorize("hasAuthority('UPDATE')")
    @Audited(action = "invoice.update", entityType = "Invoice",
            entityClass = Invoice.class)
    public Invoice updateDraft(UUID invoiceId, Invoice updates) {
        Long companyId = TenantContext.getCurrentTenantId();
        Invoice existing = invoiceRepository.findByIdAndCompanyId(invoiceId,
                companyId)
                .orElseThrow(() -> new InvoiceNotFoundException(
                        "Invoice not found: " + invoiceId));

        if (existing.getStatus() != InvoiceStatus.DRAFT) {
            throw new InvoiceNotDraftException(
                    "Only DRAFT invoices can be updated");
        }

        applyUpdates(existing, updates, companyId);

        List<ValidationError> errors = validationService.validate(existing);
        if (!errors.isEmpty()) {
            throw new InvoiceValidationException(errors);
        }

        calculationService.recalculate(existing);
        return invoiceRepository.save(existing);
    }

    /**
     * Cancels a draft invoice (sets status to CANCELLED).
     *
     * @param invoiceId the invoice identifier
     */
    @Transactional
    @PreAuthorize("hasAuthority('DELETE')")
    @Audited(action = "invoice.cancel", entityType = "Invoice",
            entityClass = Invoice.class)
    public void cancelDraft(UUID invoiceId) {
        Long companyId = TenantContext.getCurrentTenantId();
        Invoice invoice = invoiceRepository.findByIdAndCompanyId(invoiceId,
                companyId)
                .orElseThrow(() -> new InvoiceNotFoundException(
                        "Invoice not found: " + invoiceId));

        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new InvoiceNotDraftException(
                    "Only DRAFT invoices can be cancelled");
        }

        invoice.setStatus(InvoiceStatus.CANCELLED);
        invoiceRepository.save(invoice);
    }

    /**
     * Lists invoices for the current tenant with optional filters.
     *
     * @param status optional status filter
     * @param type optional invoice type filter
     * @param dateFrom optional start date filter
     * @param dateTo optional end date filter
     * @param search optional search term
     * @param pageable pagination parameters
     * @return page of matching invoices
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('READ')")
    public Page<Invoice> list(InvoiceStatus status, InvoiceType type,
            LocalDate dateFrom, LocalDate dateTo, String search,
            Pageable pageable) {
        Long companyId = TenantContext.getCurrentTenantId();
        return invoiceRepository.findByCompanyIdFiltered(companyId, status,
                type, dateFrom, dateTo,
                (search != null && !search.isBlank()) ? search : null,
                pageable);
    }

    /**
     * Gets full invoice detail including lines and VAT breakdown.
     *
     * @param invoiceId the invoice identifier
     * @return the invoice with lines and VAT breakdown loaded
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('READ')")
    public Invoice getDetail(UUID invoiceId) {
        Long companyId = TenantContext.getCurrentTenantId();
        Invoice invoice = invoiceRepository.findByIdAndCompanyId(invoiceId,
                companyId)
                .orElseThrow(() -> new InvoiceNotFoundException(
                        "Invoice not found: " + invoiceId));
        invoice.getLines().size();
        invoice.getVatBreakdown().size();
        return invoice;
    }

    private void resolveRelations(Invoice invoice, Long companyId) {
        if (invoice.getBranch() != null && invoice.getBranch().getId() != null) {
            Branch branch = branchRepository.findById(
                    invoice.getBranch().getId())
                    .orElseThrow(() -> new InvoiceNotFoundException(
                            "Branch not found: "
                                    + invoice.getBranch().getId()));
            invoice.setBranch(branch);
        }

        if (invoice.getBuyer() != null && invoice.getBuyer().getId() != null) {
            Customer buyer = customerRepository.findByIdAndCompanyId(
                    invoice.getBuyer().getId(), companyId)
                    .orElseThrow(() -> new InvoiceNotFoundException(
                            "Customer not found: "
                                    + invoice.getBuyer().getId()));
            invoice.setBuyer(buyer);
        }

        Long userId = getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvoiceNotFoundException(
                        "User not found: " + userId));
        invoice.setCreatedBy(user);

        if (invoice.getLines() != null) {
            for (InvoiceLine line : invoice.getLines()) {
                line.setInvoice(invoice);
                if (line.getItem() != null && line.getItem().getId() != null) {
                    itemRepository.findByIdAndCompanyId(
                            line.getItem().getId(), companyId)
                            .ifPresent(line::setItem);
                }
            }
        }

        if (invoice.getOriginalInvoice() != null
                && invoice.getOriginalInvoice().getId() != null) {
            Invoice original = invoiceRepository.findByIdAndCompanyId(
                    invoice.getOriginalInvoice().getId(), companyId)
                    .orElseThrow(() -> new InvoiceNotFoundException(
                            "Original invoice not found: "
                                    + invoice.getOriginalInvoice().getId()));
            invoice.setOriginalInvoice(original);
        }
    }

    private void applyUpdates(Invoice existing, Invoice updates,
            Long companyId) {
        if (updates.getType() != null) {
            existing.setType(updates.getType());
        }
        if (updates.getSubtypeFlags() != null) {
            existing.setSubtypeFlags(updates.getSubtypeFlags());
        }
        if (updates.getIssueDate() != null) {
            existing.setIssueDate(updates.getIssueDate());
        }
        existing.setSupplyDate(updates.getSupplyDate());
        existing.setSupplyEndDate(updates.getSupplyEndDate());
        if (updates.getCurrency() != null) {
            existing.setCurrency(updates.getCurrency());
        }
        if (updates.getBuyer() != null && updates.getBuyer().getId() != null) {
            Customer buyer = customerRepository.findByIdAndCompanyId(
                    updates.getBuyer().getId(), companyId)
                    .orElseThrow(() -> new InvoiceNotFoundException(
                            "Customer not found: "
                                    + updates.getBuyer().getId()));
            existing.setBuyer(buyer);
        }
        existing.setBuyerData(updates.getBuyerData());
        existing.setSellerData(updates.getSellerData());
        existing.setPaymentMeansCode(updates.getPaymentMeansCode());
        existing.setPaymentTerms(updates.getPaymentTerms());
        existing.setPrepaidAmount(
                updates.getPrepaidAmount() != null
                        ? updates.getPrepaidAmount() : BigDecimal.ZERO);
        existing.setTotalAllowances(
                updates.getTotalAllowances() != null
                        ? updates.getTotalAllowances() : BigDecimal.ZERO);
        existing.setNotes(updates.getNotes());

        if (updates.getLines() != null) {
            existing.getLines().clear();
            for (InvoiceLine line : updates.getLines()) {
                line.setInvoice(existing);
                if (line.getItem() != null && line.getItem().getId() != null) {
                    itemRepository.findByIdAndCompanyId(
                            line.getItem().getId(), companyId)
                            .ifPresent(line::setItem);
                }
                existing.getLines().add(line);
            }
        }

        if (updates.getOriginalInvoice() != null
                && updates.getOriginalInvoice().getId() != null) {
            Invoice original = invoiceRepository.findByIdAndCompanyId(
                    updates.getOriginalInvoice().getId(), companyId)
                    .orElseThrow(() -> new InvoiceNotFoundException(
                            "Original invoice not found"));
            existing.setOriginalInvoice(original);
        }
    }

    private Long getCurrentUserId() {
        var auth = org.springframework.security.core.context
                .SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new IllegalStateException(
                    "No authenticated user found in security context");
        }
        if (auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        try {
            return Long.parseLong(auth.getName());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Cannot determine user id from authentication principal: "
                            + auth.getName());
        }
    }

    /** Thrown when an invoice is not found. */
    public static class InvoiceNotFoundException extends RuntimeException {

        public InvoiceNotFoundException(String message) {
            super(message);
        }
    }

    /** Thrown when an operation is attempted on a non-DRAFT invoice. */
    public static class InvoiceNotDraftException extends RuntimeException {

        public InvoiceNotDraftException(String message) {
            super(message);
        }
    }

    /** Thrown when invoice validation fails. */
    public static class InvoiceValidationException extends RuntimeException {

        private final List<ValidationError> errors;

        public InvoiceValidationException(List<ValidationError> errors) {
            super("Invoice validation failed");
            this.errors = errors;
        }

        public List<ValidationError> getErrors() {
            return errors;
        }
    }
}
