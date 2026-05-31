package com.einvoice.api.zatca;

import com.einvoice.api.zatca.dto.ZatcaCustomerResponse;
import com.einvoice.api.zatca.dto.ZatcaCustomerWriteRequest;
import com.einvoice.api.zatca.service.ZatcaCustomerService;
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

/**
 * REST controller for ZATCA customer management endpoints.
 */
@RestController
@RequireOperationalMode
@RequestMapping("/api/companies/{companyId}/zatca/customers")
public class ZatcaCustomerController {

    private final ZatcaCustomerService service;

    public ZatcaCustomerController(ZatcaCustomerService service) {
        this.service = service;
    }

    /**
     * Lists ZATCA customers with optional search, filtering and pagination.
     *
     * @param companyId the company ID from the path
     * @param page the page number (zero-based)
     * @param size the page size
     * @param sort the sort expression (field,dir)
     * @param q optional search query
     * @param includeInactive whether to include inactive customers
     * @param filterCompanyId optional company filter for multi-company users
     * @return paginated list of ZATCA customers
     */
    @GetMapping
    @RequiresPermission(transactionType = "CUSTOMERS", action = "VIEW")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable UUID companyId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name_en,asc") String sort,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(name = "companyId", required = false) UUID filterCompanyId) {
        verifyContext(companyId);
        Page<ZatcaCustomerResponse> result = service.list(q, includeInactive, page, size, sort,
                filterCompanyId);
        return ResponseEntity.ok(Map.of(
                "items", result.getContent(),
                "page", Map.of("page", result.getNumber(), "size", result.getSize(),
                        "total", result.getTotalElements())));
    }

    /**
     * Retrieves a single ZATCA customer by ID.
     *
     * @param companyId the company ID from the path
     * @param customerId the customer ID
     * @return the customer response or 404
     */
    @GetMapping("/{customerId}")
    @RequiresPermission(transactionType = "CUSTOMERS", action = "VIEW")
    public ResponseEntity<ZatcaCustomerResponse> get(
            @PathVariable UUID companyId,
            @PathVariable UUID customerId) {
        verifyContext(companyId);
        ZatcaCustomerResponse response = service.get(customerId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Creates a new ZATCA customer.
     *
     * @param companyId the company ID from the path
     * @param request the customer creation payload
     * @return the created customer
     */
    @PostMapping
    @RequiresPermission(transactionType = "CUSTOMERS", action = "CREATE")
    public ResponseEntity<ZatcaCustomerResponse> create(
            @PathVariable UUID companyId,
            @Valid @RequestBody ZatcaCustomerWriteRequest request) {
        verifyContext(companyId);
        ZatcaCustomerResponse response = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Updates an existing ZATCA customer.
     *
     * @param companyId the company ID from the path
     * @param customerId the customer ID
     * @param request the customer update payload
     * @return the updated customer
     */
    @PutMapping("/{customerId}")
    @RequiresPermission(transactionType = "CUSTOMERS", action = "EDIT")
    public ResponseEntity<ZatcaCustomerResponse> update(
            @PathVariable UUID companyId,
            @PathVariable UUID customerId,
            @Valid @RequestBody ZatcaCustomerWriteRequest request) {
        verifyContext(companyId);
        ZatcaCustomerResponse response = service.update(customerId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Hard-deletes a ZATCA customer.
     *
     * @param companyId the company ID from the path
     * @param customerId the customer ID
     * @return no content on success
     */
    @DeleteMapping("/{customerId}")
    @RequiresPermission(transactionType = "CUSTOMERS", action = "DELETE")
    public ResponseEntity<Void> delete(
            @PathVariable UUID companyId,
            @PathVariable UUID customerId) {
        verifyContext(companyId);
        service.delete(customerId);
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
