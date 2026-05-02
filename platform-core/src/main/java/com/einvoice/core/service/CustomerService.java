package com.einvoice.core.service;

import com.einvoice.core.audit.Audited;
import com.einvoice.core.context.LovContextResolver;
import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Customer;
import com.einvoice.core.domain.enums.CustomerType;
import com.einvoice.core.domain.enums.Permission;
import com.einvoice.core.repository.CompanyRepository;
import com.einvoice.core.repository.CustomerRepository;
import com.einvoice.core.security.RequiresPermission;
import com.einvoice.core.service.importing.BulkUploadResult;
import com.einvoice.core.service.importing.ParsedRow;
import com.einvoice.core.service.importing.RowError;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing customers within a tenant context. */
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    private final CompanyRepository companyRepository;

    private final JdbcTemplate jdbcTemplate;

    private final LovContextResolver lovContextResolver;

    /**
     * Creates the customer service.
     *
     * @param customerRepository the customer repository
     * @param companyRepository the company repository
     * @param jdbcTemplate the JDBC template for cross-domain queries
     * @param lovContextResolver resolves effective LOV context (super-user bypass)
     */
    public CustomerService(CustomerRepository customerRepository,
            CompanyRepository companyRepository, JdbcTemplate jdbcTemplate,
            LovContextResolver lovContextResolver) {
        this.customerRepository = customerRepository;
        this.companyRepository = companyRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.lovContextResolver = lovContextResolver;
    }

    /**
     * Creates a new customer within the current tenant context.
     *
     * @param customer the customer to create (company set automatically)
     * @return the saved customer
     */
    @Transactional
    @RequiresPermission(Permission.CREATE_CUSTOMER)
    @Audited(action = "customer.create", entityType = "Customer")
    public Customer create(Customer customer) {
        Long companyId = TenantContext.getCurrentTenantId();
        Long lovContextId = TenantContext.getLovContextId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyService.CompanyNotFoundException(
                        "Company not found: " + companyId));
        customer.setCompany(company);
        if (lovContextId == null) {
            throw new IllegalStateException("No active LOV context for create operation");
        }
        customer.setLovContextId(lovContextId);
        validateB2bVat(customer.getCustomerType(), customer.getVatNumber());
        validateDuplicateVat(companyId, lovContextId, customer.getVatNumber(), null);
        return customerRepository.save(customer);
    }

    /**
     * Updates an existing customer, replacing all supplied fields.
     *
     * @param id the customer identifier within the current tenant
     * @param updates the customer data to apply (all non-null fields replace existing)
     * @return the updated customer
     */
    @Transactional
    @RequiresPermission(Permission.EDIT_CUSTOMER)
    @Audited(action = "customer.update", entityType = "Customer",
            entityClass = Customer.class)
    public Customer update(Long id, Customer updates) {
        Long companyId = TenantContext.getCurrentTenantId();
        Long lovContextId = lovContextResolver.resolveEffectiveLovContextId();
        Customer existing = customerRepository.findByIdAndCompanyId(id, companyId, lovContextId)
                .orElseThrow(() -> new CustomerNotFoundException(
                        "Customer not found: " + id));
        CustomerType resolvedType =
                updates.getCustomerType() != null ? updates.getCustomerType()
                        : existing.getCustomerType();
        String resolvedVat =
                updates.getVatNumber() != null ? updates.getVatNumber()
                        : existing.getVatNumber();
        validateB2bVat(resolvedType, resolvedVat);
        validateDuplicateVat(companyId, lovContextId, resolvedVat, id);
        applyUpdates(existing, updates);
        return customerRepository.save(existing);
    }

    /**
     * Soft-deletes a customer. Blocked if referenced by any invoice.
     *
     * @param id the customer identifier within the current tenant
     */
    @Transactional
    @RequiresPermission(Permission.DELETE_CUSTOMER)
    @Audited(action = "customer.delete", entityType = "Customer",
            entityClass = Customer.class)
    public void softDelete(Long id) {
        Long companyId = TenantContext.getCurrentTenantId();
        Long lovContextId = lovContextResolver.resolveEffectiveLovContextId();
        Customer customer = customerRepository.findByIdAndCompanyId(id, companyId, lovContextId)
                .orElseThrow(() -> new CustomerNotFoundException(
                        "Customer not found: " + id));
        List<String> linkedInvoices = findLinkedInvoices(id, companyId);
        if (!linkedInvoices.isEmpty()) {
            throw new CustomerReferencedByInvoiceException(
                    "Cannot delete customer referenced by invoices",
                    linkedInvoices);
        }
        customer.setIsActive(false);
        customerRepository.save(customer);
    }

    /**
     * Lists active customers for the current tenant with optional filters.
     *
     * @param search free-text search on name/VAT (nullable)
     * @param type filter by customer type (nullable)
     * @param vatNumber filter by VAT number (nullable)
     * @param pageable pagination information
     * @return a page of customers matching the filters
     */
    @Transactional(readOnly = true)
    @RequiresPermission(Permission.VIEW_CUSTOMER_LIST)
    public Page<Customer> list(String search, CustomerType type,
            String vatNumber, Pageable pageable) {
        Long companyId = TenantContext.getCurrentTenantId();
        Long lovContextId = lovContextResolver.resolveEffectiveLovContextId();
        if (search != null && !search.isBlank()) {
            return customerRepository.searchByCompanyId(
                    companyId, lovContextId, search, pageable);
        }
        if (type != null) {
            return customerRepository
                    .findByCompanyIdAndIsActiveTrueAndCustomerType(
                            companyId, lovContextId, type, pageable);
        }
        if (vatNumber != null && !vatNumber.isBlank()) {
            return customerRepository.searchByCompanyId(
                    companyId, lovContextId, vatNumber, pageable);
        }
        return customerRepository.findByCompanyIdAndIsActiveTrue(
                companyId, lovContextId, pageable);
    }

    /**
     * Retrieves a customer by ID within the current tenant.
     *
     * @param id the customer identifier
     * @return the customer
     */
    @Transactional(readOnly = true)
    @RequiresPermission(Permission.VIEW_CUSTOMER_LIST)
    public Customer getById(Long id) {
        Long companyId = TenantContext.getCurrentTenantId();
        Long lovContextId = lovContextResolver.resolveEffectiveLovContextId();
        return customerRepository.findByIdAndCompanyId(id, companyId, lovContextId)
                .orElseThrow(() -> new CustomerNotFoundException(
                        "Customer not found: " + id));
    }

    private void validateB2bVat(CustomerType customerType, String vatNumber) {
        if (customerType == CustomerType.B2B
                && (vatNumber == null || vatNumber.isBlank())) {
            throw new B2bVatRequiredException(
                    "VAT number is required for B2B customers");
        }
    }

    private void validateDuplicateVat(Long companyId, Long lovContextId, String vatNumber,
            Long excludeId) {
        if (vatNumber == null || vatNumber.isBlank()) {
            return;
        }
        customerRepository.findByCompanyIdAndVatNumber(companyId, lovContextId, vatNumber)
                .ifPresent(existing -> {
                    if (excludeId == null
                            || !existing.getId().equals(excludeId)) {
                        throw new DuplicateCustomerVatException(
                                "Customer with VAT number " + vatNumber
                                        + " already exists");
                    }
                });
    }

    /**
     * Bulk-creates or updates customers with upsert semantics.
     * B2B matches on VAT number; B2C matches on contact email.
     *
     * @param rows parsed rows each carrying a row number and customer entity
     * @param companyId the owning company
     * @param lovContextId the LOV context for tenant isolation
     * @return summary of processed / failed counts and per-row errors
     */
    @Transactional
    @RequiresPermission(Permission.CREATE_CUSTOMER)
    @Audited(action = "customer.bulk_import", entityType = "Customer")
    public BulkUploadResult createBatch(List<ParsedRow<Customer>> rows,
            Long companyId, Long lovContextId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyService.CompanyNotFoundException(
                        "Company not found: " + companyId));
        List<RowError> errors = new ArrayList<>();
        int processed = 0;

        for (ParsedRow<Customer> parsed : rows) {
            Customer customer = parsed.entity();
            int rowNum = parsed.rowNum();
            try {
                customer.setCompany(company);
                customer.setLovContextId(lovContextId);
                Optional<Customer> existing = findExistingForUpsert(
                        companyId, lovContextId, customer);
                if (existing.isPresent()) {
                    applyBulkUpdates(existing.get(), customer);
                    customerRepository.save(existing.get());
                } else {
                    customerRepository.save(customer);
                }
                processed++;
            } catch (Exception e) {
                errors.add(new RowError(rowNum, "persistence",
                        "Failed to save customer '" + customer.getNameEn()
                                + "': " + e.getMessage()));
            }
        }

        return new BulkUploadResult(processed, errors.size(), errors);
    }

    private Optional<Customer> findExistingForUpsert(Long companyId,
            Long lovContextId, Customer customer) {
        if (customer.getCustomerType() == CustomerType.B2B
                && customer.getVatNumber() != null
                && !customer.getVatNumber().isBlank()) {
            return customerRepository.findByCompanyIdAndVatNumber(
                    companyId, lovContextId, customer.getVatNumber());
        }
        if (customer.getCustomerType() == CustomerType.B2C
                && customer.getContactEmail() != null
                && !customer.getContactEmail().isBlank()) {
            return customerRepository.findByCompanyIdAndContactEmail(
                    companyId, lovContextId, customer.getContactEmail());
        }
        return Optional.empty();
    }

    private void applyBulkUpdates(Customer existing, Customer updates) {
        if (updates.getNameAr() != null) {
            existing.setNameAr(updates.getNameAr());
        }
        existing.setNameEn(updates.getNameEn());
        if (updates.getVatNumber() != null) {
            existing.setVatNumber(updates.getVatNumber());
        }
        existing.setCustomerType(updates.getCustomerType());
        if (updates.getIdType() != null) {
            existing.setIdType(updates.getIdType());
        }
        if (updates.getIdValue() != null) {
            existing.setIdValue(updates.getIdValue());
        }
        if (updates.getContactEmail() != null) {
            existing.setContactEmail(updates.getContactEmail());
        }
        if (updates.getContactPhone() != null) {
            existing.setContactPhone(updates.getContactPhone());
        }
        if (updates.getCountryCode() != null) {
            existing.setCountryCode(updates.getCountryCode());
        }
    }

    private void applyUpdates(Customer existing, Customer updates) {
        if (updates.getNameAr() != null) {
            existing.setNameAr(updates.getNameAr());
        }
        if (updates.getNameEn() != null) {
            existing.setNameEn(updates.getNameEn());
        }
        if (updates.getVatNumber() != null) {
            existing.setVatNumber(updates.getVatNumber());
        }
        if (updates.getCustomerType() != null) {
            existing.setCustomerType(updates.getCustomerType());
        }
        if (updates.getIdType() != null) {
            existing.setIdType(updates.getIdType());
        }
        if (updates.getIdValue() != null) {
            existing.setIdValue(updates.getIdValue());
        }
        if (updates.getStreet() != null) {
            existing.setStreet(updates.getStreet());
        }
        if (updates.getBuildingNumber() != null) {
            existing.setBuildingNumber(updates.getBuildingNumber());
        }
        if (updates.getCity() != null) {
            existing.setCity(updates.getCity());
        }
        if (updates.getDistrict() != null) {
            existing.setDistrict(updates.getDistrict());
        }
        if (updates.getPostalCode() != null) {
            existing.setPostalCode(updates.getPostalCode());
        }
        if (updates.getCountryCode() != null) {
            existing.setCountryCode(updates.getCountryCode());
        }
        if (updates.getContactEmail() != null) {
            existing.setContactEmail(updates.getContactEmail());
        }
        if (updates.getContactPhone() != null) {
            existing.setContactPhone(updates.getContactPhone());
        }
    }

    private List<String> findLinkedInvoices(Long customerId, Long companyId) {
        return jdbcTemplate.queryForList(
                "SELECT invoice_number FROM invoices "
                        + "WHERE buyer_id = ? AND company_id = ? LIMIT 10",
                String.class, customerId, companyId);
    }

    /** Exception thrown when a customer is not found. */
    public static class CustomerNotFoundException extends RuntimeException {

        /**
         * Creates a new CustomerNotFoundException.
         *
         * @param message the detail message
         */
        public CustomerNotFoundException(String message) {
            super(message);
        }
    }

    /** Exception thrown when a duplicate VAT number is detected. */
    public static class DuplicateCustomerVatException extends RuntimeException {

        /**
         * Creates a new DuplicateCustomerVatException.
         *
         * @param message the detail message
         */
        public DuplicateCustomerVatException(String message) {
            super(message);
        }
    }

    /** Exception thrown when a B2B customer is missing a VAT number. */
    public static class B2bVatRequiredException extends RuntimeException {

        /**
         * Creates a new B2bVatRequiredException.
         *
         * @param message the detail message
         */
        public B2bVatRequiredException(String message) {
            super(message);
        }
    }

    /** Exception thrown when deleting a customer referenced by invoices. */
    public static class CustomerReferencedByInvoiceException
            extends RuntimeException {

        private final List<String> linkedInvoices;

        /**
         * Creates a new CustomerReferencedByInvoiceException.
         *
         * @param message the detail message
         * @param linkedInvoices the list of invoice numbers referencing this customer
         */
        public CustomerReferencedByInvoiceException(String message,
                List<String> linkedInvoices) {
            super(message);
            this.linkedInvoices = linkedInvoices;
        }

        /**
         * Returns the list of linked invoice numbers.
         *
         * @return the list of invoice numbers
         */
        public List<String> getLinkedInvoices() {
            return linkedInvoices;
        }
    }
}
