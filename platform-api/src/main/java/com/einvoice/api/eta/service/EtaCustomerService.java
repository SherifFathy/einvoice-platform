package com.einvoice.api.eta.service;

import com.einvoice.api.eta.dto.EtaCustomerResponse;
import com.einvoice.api.eta.dto.EtaCustomerWriteRequest;
import com.einvoice.api.shared.validation.EtaAddressDataValidator;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.eta.EtaCustomer;
import com.einvoice.core.error.CustomerNotFoundException;
import com.einvoice.core.error.DuplicateTaxNumberException;
import com.einvoice.core.error.InvalidCustomerTypeException;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.eta.EtaCustomerRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.core.repository.support.OperationalRepositorySupport;
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
 * Service layer for ETA customer CRUD operations with tenant isolation.
 */
@Service
@Transactional
public class EtaCustomerService {

    private static final List<String> ALLOWED_CUSTOMER_TYPES = List.of("B", "P", "F");
    private static final String UQ_ETA_CUSTOMER_TAX = "uq_eta_customer_tax";

    private final EtaCustomerRepository repository;
    private final UserCompanyTransactionRoleRepository uctrRepository;
    private final CompanyRepository companyRepository;

    /**
     * Constructs an EtaCustomerService with required dependencies.
     *
     * @param repository the ETA customer repository
     * @param uctrRepository the user-company-transaction-role repository
     * @param companyRepository the company repository
     */
    public EtaCustomerService(EtaCustomerRepository repository,
            UserCompanyTransactionRoleRepository uctrRepository,
            CompanyRepository companyRepository) {
        this.repository = repository;
        this.uctrRepository = uctrRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * Lists ETA customers matching optional search criteria with pagination.
     *
     * @param q optional search query filtering by name or tax number
     * @param includeInactive whether to include inactive customers
     * @param page the page number (zero-based)
     * @param size the page size
     * @param sort the sort expression (field,dir)
     * @param filterCompanyId optional company filter for multi-company users
     * @return paginated list of ETA customer responses
     */
    @Transactional(readOnly = true)
    public Page<EtaCustomerResponse> list(String q, boolean includeInactive,
            int page, int size, String sort, UUID filterCompanyId) {
        Specification<EtaCustomer> spec;
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();

        if (filterCompanyId != null) {
            List<UUID> assigned = getAssignedCompanyIds();
            if (!assigned.contains(filterCompanyId)) {
                spec = OperationalRepositorySupport.<EtaCustomer>companyIdIn(List.of());
            } else {
                spec = OperationalRepositorySupport.inActiveTenant(filterCompanyId, authEnvId);
            }
        } else {
            List<UUID> assigned = getAssignedCompanyIds();
            spec = OperationalRepositorySupport.<EtaCustomer>authorityEnvironmentIdEquals(authEnvId)
                    .and(OperationalRepositorySupport.companyIdIn(assigned));
        }

        if (!includeInactive) {
            spec = spec.and((root, query, cb) -> cb.isTrue(root.get("isActive")));
        }

        if (q != null && !q.isBlank()) {
            String trimmed = q.strip();
            String pattern = "%" + trimmed.toLowerCase(Locale.ROOT) + "%";
            Specification<EtaCustomer> searchSpec = (root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("nameEn")), pattern),
                    cb.like(cb.lower(root.get("nameAr")), pattern),
                    cb.like(cb.lower(root.get("taxNumber")), pattern));
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
     * Retrieves a single ETA customer by ID within the active tenant.
     *
     * @param customerId the customer ID
     * @return the customer response
     */
    @Transactional(readOnly = true)
    public EtaCustomerResponse get(UUID customerId) {
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();
        List<UUID> assigned = getAssignedCompanyIds();
        Specification<EtaCustomer> spec =
                OperationalRepositorySupport.<EtaCustomer>authorityEnvironmentIdEquals(authEnvId)
                .and(OperationalRepositorySupport.companyIdIn(assigned))
                .and(OperationalRepositorySupport.idEquals(customerId));
        return repository.findOne(spec)
                .map(this::toResponse)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));
    }

    /**
     * Creates a new ETA customer after validating type and address.
     *
     * @param request the customer creation payload
     * @return the created customer response
     */
    public EtaCustomerResponse create(EtaCustomerWriteRequest request) {
        validateCustomerType(request.customerType());
        EtaAddressDataValidator.validate(request.addressData());

        EtaCustomer entity = EtaCustomer.builder()
                .companyId(TenantContext.getCompanyId())
                .authorityEnvironmentId(TenantContext.getAuthorityEnvironmentId())
                .customerType(request.customerType())
                .nameAr(request.nameAr())
                .nameEn(request.nameEn())
                .taxNumber(request.taxNumber())
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
            throwIfUniqueConstraint(e, UQ_ETA_CUSTOMER_TAX,
                    () -> new DuplicateTaxNumberException(
                            "Duplicate tax number in this context",
                            entity.getTaxNumber(), "taxNumber"));
            throw e;
        }
    }

    /**
     * Updates an existing ETA customer, deactivating if isActive transitions to false.
     *
     * @param customerId the customer ID
     * @param request the customer update payload
     * @return the updated customer response
     */
    public EtaCustomerResponse update(UUID customerId, EtaCustomerWriteRequest request) {
        validateCustomerType(request.customerType());
        EtaAddressDataValidator.validate(request.addressData());

        Short authEnvId = TenantContext.getAuthorityEnvironmentId();
        List<UUID> assigned = getAssignedCompanyIds();
        Specification<EtaCustomer> spec =
                OperationalRepositorySupport.<EtaCustomer>authorityEnvironmentIdEquals(authEnvId)
                .and(OperationalRepositorySupport.companyIdIn(assigned))
                .and(OperationalRepositorySupport.idEquals(customerId));
        EtaCustomer entity = repository.findOne(spec)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));

        entity.setCustomerType(request.customerType());
        entity.setNameAr(request.nameAr());
        entity.setNameEn(request.nameEn());
        entity.setTaxNumber(request.taxNumber());
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
            throwIfUniqueConstraint(e, UQ_ETA_CUSTOMER_TAX,
                    () -> new DuplicateTaxNumberException(
                            "Duplicate tax number in this context",
                            entity.getTaxNumber(), "taxNumber"));
            throw e;
        }
    }

    /**
     * Hard-deletes an ETA customer by ID.
     *
     * @param customerId the customer ID
     */
    public void delete(UUID customerId) {
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();
        List<UUID> assigned = getAssignedCompanyIds();
        Specification<EtaCustomer> spec =
                OperationalRepositorySupport.<EtaCustomer>authorityEnvironmentIdEquals(authEnvId)
                .and(OperationalRepositorySupport.companyIdIn(assigned))
                .and(OperationalRepositorySupport.idEquals(customerId));
        EtaCustomer entity = repository.findOne(spec)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found"));
        repository.delete(entity);
    }

    /**
     * Marks the customer as inactive.
     *
     * @param entity the customer entity to deactivate
     */
    public void deactivate(EtaCustomer entity) {
        entity.setIsActive(false);
    }

    private void validateCustomerType(String customerType) {
        if (customerType != null && !ALLOWED_CUSTOMER_TYPES.contains(customerType)) {
            throw new InvalidCustomerTypeException(
                    "Invalid customer type: " + customerType,
                    "customerType", customerType, ALLOWED_CUSTOMER_TYPES);
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

    private EtaCustomerResponse toResponse(EtaCustomer c) {
        return new EtaCustomerResponse(
                c.getId(), c.getCompanyId(), c.getCustomerType(),
                c.getNameAr(), c.getNameEn(), c.getTaxNumber(),
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
          case "tax_number" -> "taxNumber";
          case "created_at" -> "createdAt";
          default -> "nameEn";
        };
    }
}
