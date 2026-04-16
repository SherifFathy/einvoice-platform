package com.einvoice.core.service;

import com.einvoice.core.audit.Audited;
import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Item;
import com.einvoice.core.domain.enums.AuthorityScope;
import com.einvoice.core.repository.CompanyRepository;
import com.einvoice.core.repository.ItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for item CRUD with tenant scoping, duplicate-code checks, and soft-delete. */
@Service
public class ItemService {

    private final ItemRepository itemRepository;

    private final CompanyRepository companyRepository;

    /**
     * Creates the item service.
     *
     * @param itemRepository the item repository
     * @param companyRepository the company repository
     */
    public ItemService(ItemRepository itemRepository,
            CompanyRepository companyRepository) {
        this.itemRepository = itemRepository;
        this.companyRepository = companyRepository;
    }

    /**
     * Creates a new item under the current tenant.
     *
     * @param item the item to persist
     * @return the saved item
     */
    @Transactional
    @PreAuthorize("hasAuthority('CREATE')")
    @Audited(action = "item.create", entityType = "Item",
            entityClass = Item.class)
    public Item create(Item item) {
        Long companyId = TenantContext.getCurrentTenantId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyService.CompanyNotFoundException(
                        "Company not found: " + companyId));
        item.setCompany(company);
        validateDuplicateCode(companyId, item.getCode(), null);
        return itemRepository.save(item);
    }

    /**
     * Applies partial updates to an existing item.
     *
     * @param id the item id
     * @param updates the fields to apply (nulls are ignored)
     * @return the updated item
     */
    @Transactional
    @PreAuthorize("hasAuthority('UPDATE')")
    @Audited(action = "item.update", entityType = "Item", entityClass = Item.class)
    public Item update(Long id, Item updates) {
        Long companyId = TenantContext.getCurrentTenantId();
        Item existing = itemRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ItemNotFoundException(
                        "Item not found: " + id));
        String resolvedCode = updates.getCode() != null ? updates.getCode()
                : existing.getCode();
        validateDuplicateCode(companyId, resolvedCode, id);
        applyUpdates(existing, updates);
        return itemRepository.save(existing);
    }

    /**
     * Soft-deletes an item (sets {@code isActive} to {@code false}).
     *
     * @param id the item id
     */
    @Transactional
    @PreAuthorize("hasAuthority('DELETE')")
    @Audited(action = "item.delete", entityType = "Item", entityClass = Item.class)
    public void softDelete(Long id) {
        Long companyId = TenantContext.getCurrentTenantId();
        Item item = itemRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ItemNotFoundException(
                        "Item not found: " + id));
        item.setIsActive(false);
        itemRepository.save(item);
    }

    /**
     * Lists active items for the current tenant, optionally filtered by
     * search term or authority scope.
     *
     * @param search optional search term matching code or name
     * @param authorityScope optional authority-scope filter
     * @param pageable pagination parameters
     * @return the page of matching items
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('READ')")
    public Page<Item> list(String search, AuthorityScope authorityScope,
            Pageable pageable) {
        Long companyId = TenantContext.getCurrentTenantId();
        if (search != null && !search.isBlank()) {
            return itemRepository.searchByCompanyId(companyId, search,
                    pageable);
        }
        if (authorityScope != null) {
            return itemRepository
                    .findByCompanyIdAndIsActiveTrueAndAuthorityScope(
                            companyId, authorityScope, pageable);
        }
        return itemRepository.findByCompanyIdAndIsActiveTrue(companyId,
                pageable);
    }

    /**
     * Loads an item by id for the current tenant.
     *
     * @param id the item id
     * @return the item
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('READ')")
    public Item getById(Long id) {
        Long companyId = TenantContext.getCurrentTenantId();
        return itemRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ItemNotFoundException(
                        "Item not found: " + id));
    }

    private void validateDuplicateCode(Long companyId, String code,
            Long excludeId) {
        if (code == null || code.isBlank()) {
            return;
        }
        itemRepository.findByCompanyIdAndCodeAndIsActiveTrue(companyId, code)
                .ifPresent(existing -> {
                    if (excludeId == null
                            || !existing.getId().equals(excludeId)) {
                        throw new DuplicateItemCodeException(
                                "Item code " + code
                                        + " already exists for this company");
                    }
                });
    }

    private void applyUpdates(Item existing, Item updates) {
        if (updates.getCode() != null) {
            existing.setCode(updates.getCode());
        }
        if (updates.getNameAr() != null) {
            existing.setNameAr(updates.getNameAr());
        }
        if (updates.getNameEn() != null) {
            existing.setNameEn(updates.getNameEn());
        }
        if (updates.getUnitOfMeasure() != null) {
            existing.setUnitOfMeasure(updates.getUnitOfMeasure());
        }
        if (updates.getUnitPrice() != null) {
            existing.setUnitPrice(updates.getUnitPrice());
        }
        if (updates.getVatCategory() != null) {
            existing.setVatCategory(updates.getVatCategory());
        }
        if (updates.getVatRate() != null) {
            existing.setVatRate(updates.getVatRate());
        }
        if (updates.getDescription() != null) {
            existing.setDescription(updates.getDescription());
        }
        if (updates.getAuthorityScope() != null) {
            existing.setAuthorityScope(updates.getAuthorityScope());
        }
    }

    /** Thrown when an item lookup by id returns no result. */
    public static class ItemNotFoundException extends RuntimeException {

        /**
         * Creates a new ItemNotFoundException.
         *
         * @param message the detail message
         */
        public ItemNotFoundException(String message) {
            super(message);
        }
    }

    /** Thrown when an item code collides with an existing active item. */
    public static class DuplicateItemCodeException extends RuntimeException {

        /**
         * Creates a new DuplicateItemCodeException.
         *
         * @param message the detail message
         */
        public DuplicateItemCodeException(String message) {
            super(message);
        }
    }
}
