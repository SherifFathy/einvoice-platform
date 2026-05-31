package com.einvoice.api.zatca.service;

import com.einvoice.api.zatca.dto.ZatcaItemResponse;
import com.einvoice.api.zatca.dto.ZatcaItemWriteRequest;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.zatca.ZatcaItem;
import com.einvoice.core.error.DuplicateInternalCodeException;
import com.einvoice.core.error.InvalidVatCategoryException;
import com.einvoice.core.error.InvalidVatRateException;
import com.einvoice.core.error.ItemNotFoundException;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.rbac.UserCompanyTransactionRoleRepository;
import com.einvoice.core.repository.support.OperationalRepositorySupport;
import com.einvoice.core.repository.zatca.ZatcaItemRepository;
import com.einvoice.security.tenant.TenantContext;
import java.math.BigDecimal;
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

/** Javadoc. */
@Service
@Transactional
public class ZatcaItemService {

    private static final List<String> ALLOWED_VAT_CATEGORIES = List.of("S", "Z", "E", "O");
    private static final String UQ_ZATCA_ITEM_CODE = "uq_zatca_item_code";

    private final ZatcaItemRepository repository;
    private final UserCompanyTransactionRoleRepository uctrRepository;
    private final CompanyRepository companyRepository;

    /**
     * Constructs a ZatcaItemService with required dependencies.
     *
     * @param repository the ZATCA item repository
     * @param uctrRepository the user-company-transaction-role repository
     * @param companyRepository the company repository
     */
    public ZatcaItemService(ZatcaItemRepository repository,
            UserCompanyTransactionRoleRepository uctrRepository,
            CompanyRepository companyRepository) {
        this.repository = repository;
        this.uctrRepository = uctrRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * Lists ZATCA items matching optional search criteria with pagination.
     *
     * @param q optional search query filtering by name or code
     * @param includeInactive whether to include inactive items
     * @param page the page number (zero-based)
     * @param size the page size
     * @param sort the sort expression (field,dir)
     * @param filterCompanyId optional company filter for multi-company users
     * @return paginated list of ZATCA item responses
     */
    @Transactional(readOnly = true)
    public Page<ZatcaItemResponse> list(String q, boolean includeInactive,
            int page, int size, String sort, UUID filterCompanyId) {
        Specification<ZatcaItem> spec;
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();

        if (filterCompanyId != null) {
            List<UUID> assigned = getAssignedCompanyIds();
            if (!assigned.contains(filterCompanyId)) {
                spec = OperationalRepositorySupport.<ZatcaItem>companyIdIn(List.of());
            } else {
                spec = OperationalRepositorySupport.inActiveTenant(filterCompanyId, authEnvId);
            }
        } else {
            List<UUID> assigned = getAssignedCompanyIds();
            spec = OperationalRepositorySupport.<ZatcaItem>authorityEnvironmentIdEquals(authEnvId)
                    .and(OperationalRepositorySupport.companyIdIn(assigned));
        }

        if (!includeInactive) {
            spec = spec.and((root, query, cb) -> cb.isTrue(root.get("isActive")));
        }

        if (q != null && !q.isBlank()) {
            String trimmed = q.strip();
            String pattern = "%" + trimmed.toLowerCase(Locale.ROOT) + "%";
            Specification<ZatcaItem> searchSpec = (root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("nameEn")), pattern),
                    cb.like(cb.lower(root.get("nameAr")), pattern),
                    cb.like(cb.lower(root.get("internalCode")), pattern));
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
     * Retrieves a single ZATCA item by ID within the active tenant.
     *
     * @param itemId the item ID
     * @return the item response
     */
    @Transactional(readOnly = true)
    public ZatcaItemResponse get(UUID itemId) {
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();
        List<UUID> assigned = getAssignedCompanyIds();
        Specification<ZatcaItem> spec = OperationalRepositorySupport.<ZatcaItem>authorityEnvironmentIdEquals(authEnvId)
                .and(OperationalRepositorySupport.companyIdIn(assigned))
                .and(OperationalRepositorySupport.idEquals(itemId));
        return repository.findOne(spec)
                .map(this::toResponse)
                .orElseThrow(() -> new ItemNotFoundException("Item not found"));
    }

    /**
     * Creates a new ZATCA item after validating VAT category and rate.
     *
     * @param request the item creation payload
     * @return the created item response
     */
    public ZatcaItemResponse create(ZatcaItemWriteRequest request) {
        validateVatCategory(request.vatCategory());
        validateVatRate(request.vatCategory(), request.vatRate());

        ZatcaItem entity = ZatcaItem.builder()
                .companyId(TenantContext.getCompanyId())
                .authorityEnvironmentId(TenantContext.getAuthorityEnvironmentId())
                .internalCode(request.internalCode())
                .itemCode(request.itemCode())
                .nameAr(request.nameAr())
                .nameEn(request.nameEn())
                .unitType(request.unitType())
                .unitPrice(request.unitPrice())
                .vatCategory(request.vatCategory())
                .vatRate(request.vatRate())
                .isActive(request.isActive())
                .build();

        try {
            return toResponse(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            throwIfUniqueConstraint(e, UQ_ZATCA_ITEM_CODE,
                    () -> new DuplicateInternalCodeException(
                            "Duplicate internal code in this context",
                            entity.getInternalCode(), "internalCode"));
            throw e;
        }
    }

    /**
     * Updates an existing ZATCA item, deactivating if isActive transitions to false.
     *
     * @param itemId the item ID
     * @param request the item update payload
     * @return the updated item response
     */
    public ZatcaItemResponse update(UUID itemId, ZatcaItemWriteRequest request) {
        validateVatCategory(request.vatCategory());
        validateVatRate(request.vatCategory(), request.vatRate());

        Short authEnvId = TenantContext.getAuthorityEnvironmentId();
        List<UUID> assigned = getAssignedCompanyIds();
        Specification<ZatcaItem> spec = OperationalRepositorySupport.<ZatcaItem>authorityEnvironmentIdEquals(authEnvId)
                .and(OperationalRepositorySupport.companyIdIn(assigned))
                .and(OperationalRepositorySupport.idEquals(itemId));
        ZatcaItem entity = repository.findOne(spec)
                .orElseThrow(() -> new ItemNotFoundException("Item not found"));

        entity.setInternalCode(request.internalCode());
        entity.setItemCode(request.itemCode());
        entity.setNameAr(request.nameAr());
        entity.setNameEn(request.nameEn());
        entity.setUnitType(request.unitType());
        entity.setUnitPrice(request.unitPrice());
        entity.setVatCategory(request.vatCategory());
        entity.setVatRate(request.vatRate());

        if (Boolean.TRUE.equals(entity.getIsActive()) && !request.isActive()) {
            deactivate(entity);
        } else {
            entity.setIsActive(request.isActive());
        }

        try {
            return toResponse(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            throwIfUniqueConstraint(e, UQ_ZATCA_ITEM_CODE,
                    () -> new DuplicateInternalCodeException(
                            "Duplicate internal code in this context",
                            entity.getInternalCode(), "internalCode"));
            throw e;
        }
    }

    /**
     * Hard-deletes a ZATCA item by ID.
     *
     * @param itemId the item ID
     */
    public void delete(UUID itemId) {
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();
        List<UUID> assigned = getAssignedCompanyIds();
        Specification<ZatcaItem> spec = OperationalRepositorySupport.<ZatcaItem>authorityEnvironmentIdEquals(authEnvId)
                .and(OperationalRepositorySupport.companyIdIn(assigned))
                .and(OperationalRepositorySupport.idEquals(itemId));
        ZatcaItem entity = repository.findOne(spec)
                .orElseThrow(() -> new ItemNotFoundException("Item not found"));
        repository.delete(entity);
    }

    /**
     * Marks the item as inactive.
     *
     * @param entity the item entity to deactivate
     */
    public void deactivate(ZatcaItem entity) {
        entity.setIsActive(false);
    }

    private void validateVatCategory(String vatCategory) {
        if (vatCategory != null && !ALLOWED_VAT_CATEGORIES.contains(vatCategory)) {
            throw new InvalidVatCategoryException(
                    "Invalid VAT category: " + vatCategory,
                    "vatCategory", vatCategory, ALLOWED_VAT_CATEGORIES);
        }
    }

    private void validateVatRate(String vatCategory, BigDecimal vatRate) {
        if (vatCategory != null && !"S".equals(vatCategory)) {
            if (vatRate != null && vatRate.compareTo(BigDecimal.ZERO) != 0) {
                throw new InvalidVatRateException(
                        "vatRate must be 0 when vatCategory is not S",
                        "vatRate", vatRate.toPlainString());
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

    private ZatcaItemResponse toResponse(ZatcaItem i) {
        return new ZatcaItemResponse(
                i.getId(), i.getCompanyId(), i.getInternalCode(),
                i.getItemCode(),
                i.getNameAr(), i.getNameEn(),
                i.getUnitType(), i.getUnitPrice(),
                i.getVatCategory(), i.getVatRate(),
                i.getIsActive(), i.getCreatedAt(), i.getUpdatedAt());
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
          case "internal_code" -> "internalCode";
          case "created_at" -> "createdAt";
          default -> "nameEn";
        };
    }
}
