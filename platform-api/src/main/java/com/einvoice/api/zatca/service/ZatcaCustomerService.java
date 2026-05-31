package com.einvoice.api.zatca.service;

import com.einvoice.api.shared.validation.ZatcaAddressDataValidator;
import com.einvoice.api.zatca.dto.ZatcaCustomerResponse;
import com.einvoice.api.zatca.dto.ZatcaCustomerWriteRequest;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.zatca.ZatcaCustomer;
import com.einvoice.core.error.CustomerNotFoundException;
import com.einvoice.core.error.DuplicateVatNumberException;
import com.einvoice.core.error.InvalidCustomerTypeException;
import com.einvoice.core.error.InvalidVatNumberException;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.core.repository.support.OperationalRepositorySupport;
import com.einvoice.core.repository.zatca.ZatcaCustomerRepository;
import com.einvoice.security.tenant.TenantContext;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for ZATCA customer CRUD operations with tenant isolation.
 */
@Service
@Transactional
public class ZatcaCustomerService {

    private static final List<String> ALLOWED_CUSTOMER_TYPES = List.of("B", "P");
    private static final String VAT_REGEX = "^3[0-9]{13}3$";
    private static final String UQ_ZATCA_CUSTOMER_VAT = "uq_zatca_customer_vat";

    private final ZatcaCustomerRepository repository;
    private final UserCompanyTransactionRoleRepository uctrRepository;
    private final CompanyRepository companyRepository;

    /**
     * Constructs a ZatcaCustomerService with required dependencies.
     *
     * @param repository the ZATCA customer repository
     * @param uctrRepository the user-company-transaction-role repository
     * @param companyRepository the company repository
     */
    public ZatcaCustomerService(ZatcaCustomerRepository repository,
            UserCompanyTransactionRoleRepository uctrRepository,
            CompanyRepository companyRepository) {
        this.repository = repository;
        this.uctrRepository = uctrRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * Lists ZATCA customers matching optional search criteria with pagination.
     *
     * @param q optional search query filtering by name or VAT number
     * @param includeInactive whether to include inactive customers
     * @param page the page number (zero-based)
     * @param size the page size
     * @param sort the sort expression (field,dir)
     * @param filterCompanyId optional company filter for multi-company users
     * @return paginated list of ZATCA customer responses
     */
    @Transactional(readOnly = true)
    public Page<ZatcaCustomerResponse> list(String q, boolean includeInactive,
            int page, int size, String sort, UUID filterCompanyId) {
        Specification<ZatcaCustomer> spec;
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();

        if (filterCompanyId != null) {
            List<UUID> assigned = getAssignedCompanyIds();
            if (!assigned.contains(filterCompanyId)) {
                spec = OperationalRepositorySupport.<ZatcaCustomer>companyIdIn(List.of());
            } else {
                spec = OperationalRepositorySupport.inActiveTenant(filterCompanyId, authEnvId);
            }
        } else {
            List<UUID> assigned = getAssignedCompanyIds();
            spec = OperationalRepositorySupport.<ZatcaCustomer>authorityEnvironmentIdEquals(authEnvId)
                    .and(OperationalRepositorySupport.companyIdIn(assigned));
        }

        if (!includeInactive) {
            spec = spec.and((root, query, cb) -> cb.isTrue(root.get("isActive")));
        }

        if (q != null && !q.isBlank()) {
            String trimmed = q.strip();
            String pattern = "%" + trimmed.toLowerCase(Locale.ROOT) + "%";
            Specification<ZatcaCustomer> searchSpec = (root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("nameEn")), pattern),
                    cb.like(cb.lower(root.get("nameAr")), pattern),
                    cb.like(cb.lower(root.get("vatNumber")), pattern));
            spec = spec.and(searchSpec);
        }

        Sort parsedSort = parseSort(sort);
        return repository.findAll(spec, PageRequest.of(page, size, parsedSort))
                .map(this::toResponse);
    }

    private List<UUID> getAssignedCompanyIds() {
        if (TenantContext.isSuperUser()) {
            return companyRepository.findByIsActiveTrue().stream()
                    .map(Company::getId)
                    .toList();
        }
        return uctrRepository.findDistinctAssignedCompanyIds(
                TenantContext.getUserId(), TenantContext.getAuthorityEnvironmentId());
    }

    /**
     * Retrieves a single ZATCA customer by ID within the active tenant.
     *
     * @param customerId the customer ID
     * @return the customer response
     */
    @Transactional(readOnly = true)
    public ZatcaCustomerResponse get(UUID customerId) {
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();
        List<UUID> assigned = getAssignedCompanyIds();
        Specification<ZatcaCustomer> spec =
                OperationalRepositorySupport.<ZatcaCustomer>authorityEnvironmentIdEquals(authEnvId)
                .and(OperationalRepositorySupport.companyIdIn(assigned))
                .and(OperationalRepositorySupport.idEquals(customerId));
        return repository.findOne(spec)
                .map(this::toResponse)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));
    }

    /**
     * Creates a new ZATCA customer after validating type, address and VAT number.
     *
     * @param request the customer creation payload
     * @return the created customer response
     */
    public ZatcaCustomerResponse create(ZatcaCustomerWriteRequest request) {
        validateCustomerType(request.customerType());
        ZatcaAddressDataValidator.validate(request.addressData());
        validateVatNumber(request.customerType(), request.vatNumber());

        ZatcaCustomer entity = ZatcaCustomer.builder()
                .companyId(TenantContext.getCompanyId())
                .authorityEnvironmentId(TenantContext.getAuthorityEnvironmentId())
                .customerType(request.customerType())
                .nameAr(request.nameAr())
                .nameEn(request.nameEn())
                .vatNumber(request.vatNumber())
                .idType(request.idType())
                .idValue(request.idValue())
                .addressData(request.addressData())
                .contactEmail(request.contactEmail())
                .contactPhone(request.contactPhone())
                .isActive(request.isActive())
                .build();

        try {
            return toResponse(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            throwIfUniqueConstraint(e, UQ_ZATCA_CUSTOMER_VAT,
                    () -> new DuplicateVatNumberException(
                            "Duplicate VAT number in this context",
                            entity.getVatNumber(), "vatNumber"));
            throw e;
        }
    }

    /**
     * Updates an existing ZATCA customer, deactivating if isActive transitions to false.
     *
     * @param customerId the customer ID
     * @param request the customer update payload
     * @return the updated customer response
     */
    public ZatcaCustomerResponse update(UUID customerId, ZatcaCustomerWriteRequest request) {
        validateCustomerType(request.customerType());
        ZatcaAddressDataValidator.validate(request.addressData());
        validateVatNumber(request.customerType(), request.vatNumber());

        Short authEnvId = TenantContext.getAuthorityEnvironmentId();
        List<UUID> assigned = getAssignedCompanyIds();
        Specification<ZatcaCustomer> spec =
                OperationalRepositorySupport.<ZatcaCustomer>authorityEnvironmentIdEquals(authEnvId)
                .and(OperationalRepositorySupport.companyIdIn(assigned))
                .and(OperationalRepositorySupport.idEquals(customerId));
        ZatcaCustomer entity = repository.findOne(spec)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));

        entity.setCustomerType(request.customerType());
        entity.setNameAr(request.nameAr());
        entity.setNameEn(request.nameEn());
        entity.setVatNumber(request.vatNumber());
        entity.setIdType(request.idType());
        entity.setIdValue(request.idValue());
        entity.setAddressData(request.addressData());
        entity.setContactEmail(request.contactEmail());
        entity.setContactPhone(request.contactPhone());

        if (Boolean.TRUE.equals(entity.getIsActive()) && !request.isActive()) {
            deactivate(entity);
        } else {
            entity.setIsActive(request.isActive());
        }

        try {
            return toResponse(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            throwIfUniqueConstraint(e, UQ_ZATCA_CUSTOMER_VAT,
                    () -> new DuplicateVatNumberException(
                            "Duplicate VAT number in this context",
                            entity.getVatNumber(), "vatNumber"));
            throw e;
        }
    }

    /**
     * Hard-deletes a ZATCA customer by ID.
     *
     * @param customerId the customer ID
     */
    public void delete(UUID customerId) {
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();
        List<UUID> assigned = getAssignedCompanyIds();
        Specification<ZatcaCustomer> spec =
                OperationalRepositorySupport.<ZatcaCustomer>authorityEnvironmentIdEquals(authEnvId)
                .and(OperationalRepositorySupport.companyIdIn(assigned))
                .and(OperationalRepositorySupport.idEquals(customerId));
        ZatcaCustomer entity = repository.findOne(spec)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));
        repository.delete(entity);
    }

    /**
     * Marks the customer as inactive.
     *
     * @param entity the customer entity to deactivate
     */
    public void deactivate(ZatcaCustomer entity) {
        entity.setIsActive(false);
    }

    private void validateCustomerType(String customerType) {
        if (customerType != null && !ALLOWED_CUSTOMER_TYPES.contains(customerType)) {
            throw new InvalidCustomerTypeException(
                    "Invalid customer type: " + customerType,
                    "customerType", customerType, ALLOWED_CUSTOMER_TYPES);
        }
    }

    private void validateVatNumber(String customerType, String vatNumber) {
        if ("B".equals(customerType) && (vatNumber == null || !vatNumber.matches(VAT_REGEX))) {
            throw new InvalidVatNumberException(
                    "Business customers must provide a valid 15-digit VAT number starting and ending with 3",
                    "vatNumber", vatNumber);
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

    private ZatcaCustomerResponse toResponse(ZatcaCustomer c) {
        return new ZatcaCustomerResponse(
                c.getId(), c.getCompanyId(), c.getCustomerType(),
                c.getNameAr(), c.getNameEn(), c.getVatNumber(),
                c.getIdType(), c.getIdValue(), c.getAddressData(),
                c.getContactEmail(), c.getContactPhone(),
                c.getIsActive(), c.getCreatedAt(), c.getUpdatedAt());
    }

    private Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Direction.ASC, "nameEn");
        }
        String[] parts = sort.split(",", 2);
        String field = mapSortField(parts[0].trim());
        Sort.Direction dir = parts.length > 1
                && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(dir, field);
    }

    private String mapSortField(String field) {
        return switch (field) {
          case "name_en" -> "nameEn";
          case "name_ar" -> "nameAr";
          case "vat_number" -> "vatNumber";
          case "created_at" -> "createdAt";
          default -> "nameEn";
        };
    }
}
