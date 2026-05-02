package com.einvoice.core.service;

import com.einvoice.core.audit.Audited;
import com.einvoice.core.context.LovContextResolver;
import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Item;
import com.einvoice.core.domain.enums.AuthorityScope;
import com.einvoice.core.domain.enums.Permission;
import com.einvoice.core.repository.CompanyRepository;
import com.einvoice.core.repository.ItemRepository;
import com.einvoice.core.security.RequiresPermission;
import com.einvoice.core.service.importing.BulkUploadResult;
import com.einvoice.core.service.importing.ParsedRow;
import com.einvoice.core.service.importing.RowError;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for item CRUD with tenant scoping, duplicate-code checks, and soft-delete. */
@Service
public class ItemService {

    private final ItemRepository itemRepository;

    private final CompanyRepository companyRepository;

    private final LovContextResolver lovContextResolver;

    /**
     * Creates the item service.
     *
     * @param itemRepository the item repository
     * @param companyRepository the company repository
     * @param lovContextResolver resolves effective LOV context (super-user bypass)
     */
    public ItemService(ItemRepository itemRepository,
            CompanyRepository companyRepository,
            LovContextResolver lovContextResolver) {
        this.itemRepository = itemRepository;
        this.companyRepository = companyRepository;
        this.lovContextResolver = lovContextResolver;
    }

    /**
     * Creates a new item under the current tenant.
     *
     * @param item the item to persist
     * @return the saved item
     */
    @Transactional
    @RequiresPermission(Permission.CREATE_ITEM)
    @Audited(action = "item.create", entityType = "Item",
            entityClass = Item.class)
    public Item create(Item item) {
        Long companyId = TenantContext.getCurrentTenantId();
        Long lovContextId = TenantContext.getLovContextId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyService.CompanyNotFoundException(
                        "Company not found: " + companyId));
        item.setCompany(company);
        if (lovContextId == null) {
            throw new IllegalStateException("No active LOV context for create operation");
        }
        item.setLovContextId(lovContextId);
        validateDuplicateCode(companyId, lovContextId, item.getCode(), null);
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
    @RequiresPermission(Permission.EDIT_ITEM)
    @Audited(action = "item.update", entityType = "Item", entityClass = Item.class)
    public Item update(Long id, Item updates) {
        Long companyId = TenantContext.getCurrentTenantId();
        Long lovContextId = lovContextResolver.resolveEffectiveLovContextId();
        Item existing = itemRepository.findByIdAndCompanyId(id, companyId, lovContextId)
                .orElseThrow(() -> new ItemNotFoundException(
                        "Item not found: " + id));
        String resolvedCode = updates.getCode() != null ? updates.getCode()
                : existing.getCode();
        validateDuplicateCode(companyId, lovContextId, resolvedCode, id);
        applyUpdates(existing, updates);
        return itemRepository.save(existing);
    }

    /**
     * Soft-deletes an item (sets {@code isActive} to {@code false}).
     *
     * @param id the item id
     */
    @Transactional
    @RequiresPermission(Permission.DELETE_ITEM)
    @Audited(action = "item.delete", entityType = "Item", entityClass = Item.class)
    public void softDelete(Long id) {
        Long companyId = TenantContext.getCurrentTenantId();
        Long lovContextId = lovContextResolver.resolveEffectiveLovContextId();
        Item item = itemRepository.findByIdAndCompanyId(id, companyId, lovContextId)
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
    @RequiresPermission(Permission.VIEW_ITEM_LIST)
    public Page<Item> list(String search, AuthorityScope authorityScope,
            Pageable pageable) {
        Long companyId = TenantContext.getCurrentTenantId();
        Long lovContextId = lovContextResolver.resolveEffectiveLovContextId();
        if (search != null && !search.isBlank()) {
            return itemRepository.searchByCompanyId(companyId, lovContextId, search,
                    pageable);
        }
        if (authorityScope != null) {
            return itemRepository
                    .findByCompanyIdAndIsActiveTrueAndAuthorityScope(
                            companyId, lovContextId, authorityScope, pageable);
        }
        return itemRepository.findByCompanyIdAndIsActiveTrue(companyId, lovContextId,
                pageable);
    }

    /**
     * Loads an item by id for the current tenant.
     *
     * @param id the item id
     * @return the item
     */
    @Transactional(readOnly = true)
    @RequiresPermission(Permission.VIEW_ITEM_LIST)
    public Item getById(Long id) {
        Long companyId = TenantContext.getCurrentTenantId();
        Long lovContextId = lovContextResolver.resolveEffectiveLovContextId();
        return itemRepository.findByIdAndCompanyId(id, companyId, lovContextId)
                .orElseThrow(() -> new ItemNotFoundException(
                        "Item not found: " + id));
    }

    private void validateDuplicateCode(Long companyId, Long lovContextId, String code,
            Long excludeId) {
        if (code == null || code.isBlank()) {
            return;
        }
        itemRepository.findByCompanyIdAndCodeAndIsActiveTrue(companyId, lovContextId, code)
                .ifPresent(existing -> {
                    if (excludeId == null
                            || !existing.getId().equals(excludeId)) {
                        throw new DuplicateItemCodeException(
                                "Item code " + code
                                        + " already exists for this company");
                    }
                });
    }

    /**
     * Bulk-creates or updates items with upsert semantics.
     * Matches on (companyId, lovContextId, code); updates existing, creates new.
     *
     * @param rows parsed rows each carrying a row number and item entity
     * @param companyId the owning company
     * @param lovContextId the LOV context for tenant isolation
     * @return summary of processed / failed counts and per-row errors
     */
    @Transactional
    @RequiresPermission(Permission.CREATE_ITEM)
    @Audited(action = "item.bulk_import", entityType = "Item")
    public BulkUploadResult createBatch(List<ParsedRow<Item>> rows,
            Long companyId, Long lovContextId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new CompanyService.CompanyNotFoundException(
                        "Company not found: " + companyId));
        List<RowError> errors = new ArrayList<>();
        int processed = 0;

        for (ParsedRow<Item> parsed : rows) {
            Item item = parsed.entity();
            int rowNum = parsed.rowNum();
            try {
                item.setCompany(company);
                item.setLovContextId(lovContextId);
                itemRepository.findByCompanyIdAndCodeAndIsActiveTrue(
                        companyId, lovContextId, item.getCode())
                        .ifPresentOrElse(existing -> {
                            applyUpdates(existing, item);
                            itemRepository.save(existing);
                        }, () -> itemRepository.save(item));
                processed++;
            } catch (Exception e) {
                errors.add(new RowError(rowNum, "persistence",
                        "Failed to save item '" + item.getNameEn() + "': "
                                + e.getMessage()));
            }
        }

        return new BulkUploadResult(processed, errors.size(), errors);
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
