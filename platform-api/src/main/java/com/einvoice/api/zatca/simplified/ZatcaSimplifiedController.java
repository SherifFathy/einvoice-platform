package com.einvoice.api.zatca.simplified;

import com.einvoice.api.zatca.simplified.service.ZatcaSimplifiedFormMapper.ZatcaSimplifiedResponse;
import com.einvoice.api.zatca.simplified.service.ZatcaSimplifiedFormMapper.ZatcaSimplifiedWriteForm;
import com.einvoice.api.zatca.simplified.service.ZatcaSimplifiedService;
import com.einvoice.core.security.RequiresPermission;
import com.einvoice.security.operational.RequireOperationalMode;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** REST controller for ZATCA Simplified (B2C) invoice CRUD. */
@RestController
@RequireOperationalMode
@RequestMapping("/api/companies/{companyId}/zatca/simplified")
public class ZatcaSimplifiedController {

    private final ZatcaSimplifiedService service;

    /**
     * Inject service.
     *
     * @param service the simplified-document service
     */
    public ZatcaSimplifiedController(ZatcaSimplifiedService service) {
        this.service = service;
    }

    /**
     * List simplified documents with optional filters.
     *
     * @param companyId tenant company id
     * @param status optional status filter
     * @param filterCompanyId optional explicit company filter
     * @param dateFrom inclusive ISO date lower bound
     * @param dateTo inclusive ISO date upper bound
     * @param page zero-based page index
     * @param size page size
     * @return paginated map with items, page, size, totalElements
     */
    @GetMapping
    @RequiresPermission(transactionType = "SIMPLIFIED", action = "VIEW")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable UUID companyId,
            @RequestParam(required = false) String status,
            @RequestParam(name = "company", required = false)
                    UUID filterCompanyId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<ZatcaSimplifiedResponse> result = service.list(status,
                filterCompanyId, dateFrom, dateTo, page, size);
        return ResponseEntity.ok(Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements()));
    }

    /**
     * Get a single simplified document by ID.
     *
     * @param companyId tenant company id
     * @param docId document id
     * @return the document with ETag header for optimistic locking
     */
    @GetMapping("/{docId}")
    @RequiresPermission(transactionType = "SIMPLIFIED", action = "VIEW")
    public ResponseEntity<ZatcaSimplifiedResponse> get(
            @PathVariable UUID companyId,
            @PathVariable UUID docId) {
        ZatcaSimplifiedResponse response = service.findById(docId);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    /**
     * Create a new simplified document.
     *
     * @param companyId tenant company id
     * @param request validated write form
     * @return 201 Created with ETag header
     */
    @PostMapping
    @RequiresPermission(transactionType = "SIMPLIFIED", action = "CREATE")
    public ResponseEntity<ZatcaSimplifiedResponse> create(
            @PathVariable UUID companyId,
            @Valid @RequestBody ZatcaSimplifiedWriteForm request) {
        ZatcaSimplifiedResponse response = service.create(companyId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    /**
     * Update an existing draft simplified document.
     *
     * @param companyId tenant company id
     * @param docId document id
     * @param ifMatch If-Match header carrying optimistic-lock version
     * @param request validated write form
     * @return updated document with refreshed ETag
     */
    @PutMapping("/{docId}")
    @RequiresPermission(transactionType = "SIMPLIFIED", action = "EDIT")
    public ResponseEntity<ZatcaSimplifiedResponse> update(
            @PathVariable UUID companyId,
            @PathVariable UUID docId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ZatcaSimplifiedWriteForm request) {
        Long version = parseIfMatch(ifMatch);
        ZatcaSimplifiedResponse response = service.update(docId, request,
                version);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    /**
     * Delete a draft simplified document.
     *
     * @param companyId tenant company id
     * @param docId document id
     * @return 204 No Content
     */
    @DeleteMapping("/{docId}")
    @RequiresPermission(transactionType = "SIMPLIFIED", action = "DELETE")
    public ResponseEntity<Void> delete(
            @PathVariable UUID companyId,
            @PathVariable UUID docId) {
        service.delete(docId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Clone an existing document as a new draft.
     *
     * @param companyId tenant company id
     * @param docId source document id
     * @param body request body carrying the new invoice number
     * @return 201 Created with the new draft and ETag header
     */
    @PostMapping("/{docId}/clone-as-draft")
    @RequiresPermission(transactionType = "SIMPLIFIED", action = "CREATE")
    public ResponseEntity<ZatcaSimplifiedResponse> cloneAsDraft(
            @PathVariable UUID companyId,
            @PathVariable UUID docId,
            @RequestBody Map<String, String> body) {
        ZatcaSimplifiedResponse response = service.cloneAsDraft(docId,
                body.get("newInvoiceNumber"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    private Long parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return null;
        }
        String cleaned = ifMatch.replace("\"", "").trim();
        try {
            return Long.parseLong(cleaned);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid If-Match header value: " + ifMatch);
        }
    }
}
