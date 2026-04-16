package com.einvoice.core.service;

import com.einvoice.core.audit.Audited;
import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Customer;
import com.einvoice.core.domain.enums.CustomerType;
import com.einvoice.core.repository.CompanyRepository;
import com.einvoice.core.repository.CustomerRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for managing customers within a tenant context. */
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    private final CompanyRepository companyRepository;

    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates the customer service.
     *
     * @param customerRepository the customer repository
     * @param companyRepository the company repository
     * @param jdbcTemplate the JDBC template for cross-domain queries
     */
    public CustomerService(CustomerRepository customerRepository,
            CompanyRepository companyRepository, JdbcTemplate jdbcTemplate) {
        this.customerRepository = customerRepository;
        this.companyRepository = companyRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Creates a new customer within the current tenant context.
     *
     * @param customer the customer to create (company set automatically)
     * @return the saved customer
     */
    @Transactional
    @PreAuthorize("hasAuthority('CREATE')")
    @Audited(action = "customer.create", entityType = "Customer")
    public Customer create(Customer customer) {
        Long companyId = TenantContext.getCurrentTenantId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyService.CompanyNotFoundException(
                        "Company not found: " + companyId));
        customer.setCompany(company);
        validateB2bVat(customer.getCustomerType(), customer.getVatNumber());
        validateDuplicateVat(companyId, customer.getVatNumber(), null);
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
    @PreAuthorize("hasAuthority('UPDATE')")
    @Audited(action = "customer.update", entityType = "Customer",
            entityClass = Customer.class)
    public Customer update(Long id, Customer updates) {
        Long companyId = TenantContext.getCurrentTenantId();
        Customer existing = customerRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new CustomerNotFoundException(
                        "Customer not found: " + id));
        CustomerType resolvedType =
                updates.getCustomerType() != null ? updates.getCustomerType()
                        : existing.getCustomerType();
        String resolvedVat =
                updates.getVatNumber() != null ? updates.getVatNumber()
                        : existing.getVatNumber();
        validateB2bVat(resolvedType, resolvedVat);
        validateDuplicateVat(companyId, resolvedVat, id);
        applyUpdates(existing, updates);
        return customerRepository.save(existing);
    }

    /**
     * Soft-deletes a customer. Blocked if referenced by any invoice.
     *
     * @param id the customer identifier within the current tenant
     */
    @Transactional
    @PreAuthorize("hasAuthority('DELETE')")
    @Audited(action = "customer.delete", entityType = "Customer",
            entityClass = Customer.class)
    public void softDelete(Long id) {
        Long companyId = TenantContext.getCurrentTenantId();
        Customer customer = customerRepository.findByIdAndCompanyId(id, companyId)
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
    @PreAuthorize("hasAuthority('READ')")
    public Page<Customer> list(String search, CustomerType type,
            String vatNumber, Pageable pageable) {
        Long companyId = TenantContext.getCurrentTenantId();
        if (search != null && !search.isBlank()) {
            return customerRepository.searchByCompanyId(
                    companyId, search, pageable);
        }
        if (type != null) {
            return customerRepository
                    .findByCompanyIdAndIsActiveTrueAndCustomerType(
                            companyId, type, pageable);
        }
        if (vatNumber != null && !vatNumber.isBlank()) {
            return customerRepository.searchByCompanyId(
                    companyId, vatNumber, pageable);
        }
        return customerRepository.findByCompanyIdAndIsActiveTrue(
                companyId, pageable);
    }

    /**
     * Retrieves a customer by ID within the current tenant.
     *
     * @param id the customer identifier
     * @return the customer
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('READ')")
    public Customer getById(Long id) {
        Long companyId = TenantContext.getCurrentTenantId();
        return customerRepository.findByIdAndCompanyId(id, companyId)
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

    private void validateDuplicateVat(Long companyId, String vatNumber,
            Long excludeId) {
        if (vatNumber == null || vatNumber.isBlank()) {
            return;
        }
        customerRepository.findByCompanyIdAndVatNumber(companyId, vatNumber)
                .ifPresent(existing -> {
                    if (excludeId == null
                            || !existing.getId().equals(excludeId)) {
                        throw new DuplicateCustomerVatException(
                                "Customer with VAT number " + vatNumber
                                        + " already exists");
                    }
                });
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
