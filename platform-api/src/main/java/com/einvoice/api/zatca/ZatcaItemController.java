package com.einvoice.api.zatca;

import com.einvoice.api.zatca.dto.ZatcaItemResponse;
import com.einvoice.api.zatca.dto.ZatcaItemWriteRequest;
import com.einvoice.api.zatca.service.ZatcaItemService;
import com.einvoice.core.error.UnauthorizedContextException;
import com.einvoice.core.security.RequiresPermission;
import com.einvoice.security.operational.RequireOperationalMode;
import com.einvoice.security.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Javadoc. */
@RestController
@RequireOperationalMode
@RequestMapping("/api/companies/{companyId}/zatca/items")
public class ZatcaItemController {

    private final ZatcaItemService service;

    public ZatcaItemController(ZatcaItemService service) {
        this.service = service;
    }

    /**
     * Lists ZATCA items with optional search, filtering and pagination.
     *
     * @param companyId the company ID from the path
     * @param page the page number (zero-based)
     * @param size the page size
     * @param sort the sort expression (field,dir)
     * @param q optional search query
     * @param includeInactive whether to include inactive items
     * @param filterCompanyId optional company filter for multi-company users
     * @return paginated list of ZATCA items
     */
    @GetMapping
    @RequiresPermission(transactionType = "ITEMS", action = "VIEW")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name_en,asc") String sort,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(name = "companyId", required = false) UUID filterCompanyId) {
        verifyContext(companyId);
        Page<ZatcaItemResponse> result = service.list(q, includeInactive, page, size, sort,
                filterCompanyId);
        return ResponseEntity.ok(Map.of(
                "items", result.getContent(),
                "page", Map.of("page", result.getNumber(), "size", result.getSize(),
                        "total", result.getTotalElements())));
    }

    /**
     * Retrieves a single ZATCA item by ID.
     *
     * @param companyId the company ID from the path
     * @param itemId the item ID
     * @return the ZATCA item response
     */
    @GetMapping("/{itemId}")
    @RequiresPermission(transactionType = "ITEMS", action = "VIEW")
    public ResponseEntity<ZatcaItemResponse> get(
            @PathVariable UUID companyId,
            @PathVariable UUID itemId) {
        verifyContext(companyId);
        return ResponseEntity.ok(service.get(itemId));
    }

    /**
     * Creates a new ZATCA item.
     *
     * @param companyId the company ID from the path
     * @param request the item creation payload
     * @return the created ZATCA item response
     */
    @PostMapping
    @RequiresPermission(transactionType = "ITEMS", action = "CREATE")
    public ResponseEntity<ZatcaItemResponse> create(
            @PathVariable UUID companyId,
            @Valid @RequestBody ZatcaItemWriteRequest request) {
        verifyContext(companyId);
        ZatcaItemResponse response = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Updates an existing ZATCA item.
     *
     * @param companyId the company ID from the path
     * @param itemId the item ID
     * @param request the item update payload
     * @return the updated ZATCA item response
     */
    @PutMapping("/{itemId}")
    @RequiresPermission(transactionType = "ITEMS", action = "EDIT")
    public ResponseEntity<ZatcaItemResponse> update(
            @PathVariable UUID companyId,
            @PathVariable UUID itemId,
            @Valid @RequestBody ZatcaItemWriteRequest request) {
        verifyContext(companyId);
        ZatcaItemResponse response = service.update(itemId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Deletes a ZATCA item by ID.
     *
     * @param companyId the company ID from the path
     * @param itemId the item ID
     * @return no content response
     */
    @DeleteMapping("/{itemId}")
    @RequiresPermission(transactionType = "ITEMS", action = "DELETE")
    public ResponseEntity<Void> delete(
            @PathVariable UUID companyId,
            @PathVariable UUID itemId) {
        verifyContext(companyId);
        service.delete(itemId);
        return ResponseEntity.noContent().build();
    }

    private void verifyContext(UUID pathCompanyId) {
        UUID jwtCompanyId = TenantContext.getCompanyId();
        if (jwtCompanyId == null || !jwtCompanyId.equals(pathCompanyId)) {
            throw new UnauthorizedContextException(
                    "Path companyId does not match authenticated company context");
        }
    }
}
