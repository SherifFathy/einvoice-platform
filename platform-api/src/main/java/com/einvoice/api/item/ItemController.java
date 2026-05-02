package com.einvoice.api.item;

import com.einvoice.api.item.dto.ItemRequest;
import com.einvoice.api.item.dto.ItemResponse;
import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.Item;
import com.einvoice.core.domain.enums.AuthorityScope;
import com.einvoice.core.domain.enums.VatCategory;
import com.einvoice.core.service.ItemExcelTemplateService;
import com.einvoice.core.service.ItemImportService;
import com.einvoice.core.service.ItemService;
import com.einvoice.core.service.importing.BulkUploadResult;
import com.einvoice.core.service.importing.ExcelParseResult;
import com.einvoice.core.service.importing.RowError;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** REST controller for item CRUD, Excel import, and template download. */
@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemService itemService;

    private final ItemImportService itemImportService;

    private final ItemExcelTemplateService itemExcelTemplateService;

    /**
     * Creates the item controller.
     *
     * @param itemService the item service
     * @param itemImportService the item Excel import service
     * @param itemExcelTemplateService the item Excel template service
     */
    public ItemController(ItemService itemService,
            ItemImportService itemImportService,
            ItemExcelTemplateService itemExcelTemplateService) {
        this.itemService = itemService;
        this.itemImportService = itemImportService;
        this.itemExcelTemplateService = itemExcelTemplateService;
    }

    /**
     * Lists items for the current tenant.
     *
     * @param pageable pagination parameters
     * @param search optional search term matching code or name
     * @param authorityScope optional authority-scope filter
     * @return a page of item responses
     */
    @GetMapping
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<Page<ItemResponse>> listItems(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String authorityScope) {
        AuthorityScope scope = parseAuthorityScope(authorityScope);
        Page<Item> items = itemService.list(search, scope, pageable);
        return ResponseEntity.ok(items.map(this::toResponse));
    }

    /**
     * Creates a new item.
     *
     * @param request the item fields
     * @return the created item
     */
    @PostMapping
    @PreAuthorize("hasAuthority('CREATE')")
    public ResponseEntity<ItemResponse> createItem(
            @Valid @RequestBody ItemRequest request) {
        Item item = toEntity(request);
        Item saved = itemService.create(item);
        return ResponseEntity
                .created(URI.create("/api/items/" + saved.getId()))
                .body(toResponse(saved));
    }

    /**
     * Fetches a single item by id.
     *
     * @param id the item id
     * @return the item response
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<ItemResponse> getItem(@PathVariable Long id) {
        Item item = itemService.getById(id);
        return ResponseEntity.ok(toResponse(item));
    }

    /**
     * Updates an existing item.
     *
     * @param id the item id
     * @param request the updated item fields
     * @return the updated item
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('UPDATE')")
    public ResponseEntity<ItemResponse> updateItem(@PathVariable Long id,
            @Valid @RequestBody ItemRequest request) {
        Item updates = toEntity(request);
        Item updated = itemService.update(id, updates);
        return ResponseEntity.ok(toResponse(updated));
    }

    /**
     * Soft-deletes an item.
     *
     * @param id the item id
     * @return an empty 204 response
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DELETE')")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        itemService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Downloads the Excel import template.
     *
     * @return the .xlsx template as a binary response
     */
    @GetMapping("/template")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] template = itemExcelTemplateService.generateTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=items-template.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument"
                                + ".spreadsheetml.sheet"))
                .body(template);
    }

    /**
     * Bulk-uploads items from an Excel template.
     * Validates headers, parses rows, then creates/updates via
     * {@link ItemService#createBatch}.
     *
     * @param file the multipart Excel file
     * @return the bulk upload result
     */
    @PostMapping("/bulk-upload")
    @PreAuthorize("hasAuthority('CREATE')")
    public ResponseEntity<BulkUploadResult> bulkUploadItems(
            @RequestParam("file") MultipartFile file) {
        ExcelParseResult<Item> parsed = itemImportService.parseExcel(file);
        Long companyId = TenantContext.getCurrentTenantId();
        Long lovContextId = TenantContext.getLovContextId();

        BulkUploadResult batchResult = itemService.createBatch(
                parsed.validRows(), companyId, lovContextId);

        List<RowError> allErrors = new ArrayList<>(parsed.errors());
        allErrors.addAll(batchResult.errors());
        int totalProcessed = batchResult.processed();
        int totalFailed = parsed.errors().size() + batchResult.failed();

        return ResponseEntity.ok(
                new BulkUploadResult(totalProcessed, totalFailed, allErrors));
    }

    private AuthorityScope parseAuthorityScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return null;
        }
        try {
            return AuthorityScope.valueOf(scope.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid authority scope: " + scope
                            + ". Must be ZATCA, ETA, or BOTH");
        }
    }

    private Item toEntity(ItemRequest req) {
        return Item.builder()
                .code(req.code())
                .nameEn(req.nameEn())
                .nameAr(req.nameAr())
                .unitOfMeasure(req.unitOfMeasure())
                .unitPrice(req.unitPrice())
                .vatCategory(req.vatCategory() != null
                        ? VatCategory.valueOf(req.vatCategory()) : null)
                .vatRate(req.vatRate())
                .description(req.description())
                .authorityScope(req.authorityScope() != null
                        ? AuthorityScope.valueOf(req.authorityScope())
                        : AuthorityScope.BOTH)
                .build();
    }

    private ItemResponse toResponse(Item item) {
        return new ItemResponse(item.getId(), item.getCode(),
                item.getNameAr(), item.getNameEn(),
                item.getUnitOfMeasure(), item.getUnitPrice(),
                item.getVatCategory().name(), item.getVatRate(),
                item.getDescription(),
                item.getAuthorityScope().name(), item.getIsActive(),
                item.getCreatedAt());
    }
}
