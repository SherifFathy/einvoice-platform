package com.einvoice.api.eta.service;

import com.einvoice.api.eta.dto.EtaItemResponse;
import com.einvoice.api.eta.dto.EtaItemWriteRequest;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.eta.EtaItem;
import com.einvoice.core.error.DuplicateInternalCodeException;
import com.einvoice.core.error.InvalidItemTypeException;
import com.einvoice.core.error.ItemNotFoundException;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.eta.EtaItemRepository;
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

/** Javadoc. */
@Service
@Transactional
public class EtaItemService {

    private static final List<String> ALLOWED_ITEM_TYPES = List.of("GS1", "EGS");
    private static final String UQ_ETA_ITEM_CODE = "uq_eta_item_code";

    private final EtaItemRepository repository;
    private final UserCompanyTransactionRoleRepository uctrRepository;
    private final CompanyRepository companyRepository;

    /**
     * Constructs an EtaItemService with required dependencies.
     *
     * @param repository the ETA item repository
     * @param uctrRepository the user-company-transaction-role repository
     * @param companyRepository the company repository
     */
    public EtaItemService(EtaItemRepository repository,
            UserCompanyTransactionRoleRepository uctrRepository,
            CompanyRepository companyRepository) {
        this.repository = repository;
        this.uctrRepository = uctrRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * Lists ETA items matching optional search criteria with pagination.
     *
     * @param q optional search query filtering by name or code
     * @param includeInactive whether to include inactive items
     * @param page the page number (zero-based)
     * @param size the page size
     * @param sort the sort expression (field,dir)
     * @param filterCompanyId optional company filter for multi-company users
     * @return paginated list of ETA item responses
     */
    @Transactional(readOnly = true)
    public Page<EtaItemResponse> list(String q, boolean includeInactive,
            int page, int size, String sort, UUID filterCompanyId) {
        Specification<EtaItem> spec;
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();

        if (filterCompanyId != null) {
            List<UUID> assigned = getAssignedCompanyIds();
            if (!assigned.contains(filterCompanyId)) {
                spec = OperationalRepositorySupport.<EtaItem>companyIdIn(List.of());
            } else {
                spec = OperationalRepositorySupport.inActiveTenant(filterCompanyId, authEnvId);
            }
        } else {
            List<UUID> assigned = getAssignedCompanyIds();
            spec = OperationalRepositorySupport.<EtaItem>authorityEnvironmentIdEquals(authEnvId)
                    .and(OperationalRepositorySupport.companyIdIn(assigned));
        }

        if (!includeInactive) {
            spec = spec.and((root, query, cb) -> cb.isTrue(root.get("isActive")));
        }

        if (q != null && !q.isBlank()) {
            String trimmed = q.strip();
            String pattern = "%" + trimmed.toLowerCase(Locale.ROOT) + "%";
            Specification<EtaItem> searchSpec = (root, query, cb) -> cb.or(
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
     * Retrieves a single ETA item by ID within the active tenant.
     *
     * @param itemId the item ID
     * @return the item response
     */
    @Transactional(readOnly = true)
    public EtaItemResponse get(UUID itemId) {
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();
        List<UUID> assigned = getAssignedCompanyIds();
        Specification<EtaItem> spec = OperationalRepositorySupport.<EtaItem>authorityEnvironmentIdEquals(authEnvId)
                .and(OperationalRepositorySupport.companyIdIn(assigned))
                .and(OperationalRepositorySupport.idEquals(itemId));
        return repository.findOne(spec)
                .map(this::toResponse)
                .orElseThrow(() -> new ItemNotFoundException("Item not found"));
    }

    /**
     * Creates a new ETA item after validating item type.
     *
     * @param request the item creation payload
     * @return the created item response
     */
    public EtaItemResponse create(EtaItemWriteRequest request) {
        validateItemType(request.itemType());

        EtaItem entity = EtaItem.builder()
                .companyId(TenantContext.getCompanyId())
                .authorityEnvironmentId(TenantContext.getAuthorityEnvironmentId())
                .internalCode(request.internalCode())
                .itemType(request.itemType())
                .itemCode(request.itemCode())
                .nameAr(request.nameAr())
                .nameEn(request.nameEn())
                .unitType(request.unitType())
                .unitPrice(request.unitPrice())
                .taxType(request.taxType())
                .taxSubtype(request.taxSubtype())
                .taxRate(request.taxRate())
                .isActive(request.isActive())
                .build();

        try {
            return toResponse(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            throwIfUniqueConstraint(e, UQ_ETA_ITEM_CODE,
                    () -> new DuplicateInternalCodeException(
                            "Duplicate internal code in this context",
                            entity.getInternalCode(), "internalCode"));
            throw e;
        }
    }

    /**
     * Updates an existing ETA item, deactivating if isActive transitions to false.
     *
     * @param itemId the item ID
     * @param request the item update payload
     * @return the updated item response
     */
    public EtaItemResponse update(UUID itemId, EtaItemWriteRequest request) {
        validateItemType(request.itemType());

        Short authEnvId = TenantContext.getAuthorityEnvironmentId();
        List<UUID> assigned = getAssignedCompanyIds();
        Specification<EtaItem> spec = OperationalRepositorySupport.<EtaItem>authorityEnvironmentIdEquals(authEnvId)
                .and(OperationalRepositorySupport.companyIdIn(assigned))
                .and(OperationalRepositorySupport.idEquals(itemId));
        EtaItem entity = repository.findOne(spec)
                .orElseThrow(() -> new ItemNotFoundException("Item not found"));

        entity.setInternalCode(request.internalCode());
        entity.setItemType(request.itemType());
        entity.setItemCode(request.itemCode());
        entity.setNameAr(request.nameAr());
        entity.setNameEn(request.nameEn());
        entity.setUnitType(request.unitType());
        entity.setUnitPrice(request.unitPrice());
        entity.setTaxType(request.taxType());
        entity.setTaxSubtype(request.taxSubtype());
        entity.setTaxRate(request.taxRate());

        if (Boolean.TRUE.equals(entity.getIsActive()) && !request.isActive()) {
            deactivate(entity);
        } else {
            entity.setIsActive(request.isActive());
        }

        try {
            return toResponse(repository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException e) {
            throwIfUniqueConstraint(e, UQ_ETA_ITEM_CODE,
                    () -> new DuplicateInternalCodeException(
                            "Duplicate internal code in this context",
                            entity.getInternalCode(), "internalCode"));
            throw e;
        }
    }

    /**
     * Hard-deletes an ETA item by ID.
     *
     * @param itemId the item ID
     */
    public void delete(UUID itemId) {
        Short authEnvId = TenantContext.getAuthorityEnvironmentId();
        List<UUID> assigned = getAssignedCompanyIds();
        Specification<EtaItem> spec = OperationalRepositorySupport.<EtaItem>authorityEnvironmentIdEquals(authEnvId)
                .and(OperationalRepositorySupport.companyIdIn(assigned))
                .and(OperationalRepositorySupport.idEquals(itemId));
        EtaItem entity = repository.findOne(spec)
                .orElseThrow(() -> new ItemNotFoundException("Item not found"));
        repository.delete(entity);
    }

    /**
     * Marks the item as inactive.
     *
     * @param entity the item entity to deactivate
     */
    public void deactivate(EtaItem entity) {
        entity.setIsActive(false);
    }

    private void validateItemType(String itemType) {
        if (itemType != null && !ALLOWED_ITEM_TYPES.contains(itemType)) {
            throw new InvalidItemTypeException(
                    "Invalid item type: " + itemType,
                    "itemType", itemType, ALLOWED_ITEM_TYPES);
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

    private EtaItemResponse toResponse(EtaItem i) {
        return new EtaItemResponse(
                i.getId(), i.getCompanyId(), i.getInternalCode(),
                i.getItemType(), i.getItemCode(),
                i.getNameAr(), i.getNameEn(),
                i.getUnitType(), i.getUnitPrice(),
                i.getTaxType(), i.getTaxSubtype(), i.getTaxRate(),
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
