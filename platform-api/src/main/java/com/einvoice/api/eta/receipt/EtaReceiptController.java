package com.einvoice.api.eta.receipt;

import com.einvoice.api.eta.receipt.service.EtaReceiptFormMapper.EtaReceiptResponse;
import com.einvoice.api.eta.receipt.service.EtaReceiptFormMapper.EtaReceiptWriteForm;
import com.einvoice.api.eta.receipt.service.EtaReceiptService;
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

/** REST controller for ETA receipt CRUD endpoints. */
@RestController
@RequireOperationalMode
@RequestMapping("/api/companies/{companyId}/eta/receipts")
public class EtaReceiptController {

    private final EtaReceiptService service;

    /**
     * Constructs an EtaReceiptController.
     *
     * @param service the receipt service
     */
    public EtaReceiptController(EtaReceiptService service) {
        this.service = service;
    }

    /**
     * Lists receipts with optional filters.
     *
     * @param companyId the company identifier
     * @param status optional state filter
     * @param filterCompanyId optional company filter
     * @param receiptType optional receipt type filter
     * @param dateFrom optional start date
     * @param dateTo optional end date
     * @param page page number
     * @param size page size
     * @return paged receipt list
     */
    @GetMapping
    @RequiresPermission(transactionType = "RECEIPT", action = "VIEW")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable UUID companyId,
            @RequestParam(required = false) String status,
            @RequestParam(name = "companyId", required = false)
                    UUID filterCompanyId,
            @RequestParam(required = false) String receiptType,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<EtaReceiptResponse> result = service.list(status,
                filterCompanyId, receiptType, dateFrom, dateTo,
                page, size);
        return ResponseEntity.ok(Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements()));
    }

    /**
     * Gets a single receipt by ID.
     *
     * @param companyId the company identifier
     * @param docId the document identifier
     * @return the receipt with ETag
     */
    @GetMapping("/{docId}")
    @RequiresPermission(transactionType = "RECEIPT", action = "VIEW")
    public ResponseEntity<EtaReceiptResponse> get(
            @PathVariable UUID companyId,
            @PathVariable UUID docId) {
        EtaReceiptResponse response = service.findById(docId);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    /**
     * Creates a new DRAFT receipt.
     *
     * @param companyId the company identifier
     * @param request the receipt write form
     * @return the created receipt with ETag
     */
    @PostMapping
    @RequiresPermission(transactionType = "RECEIPT", action = "CREATE")
    public ResponseEntity<EtaReceiptResponse> create(
            @PathVariable UUID companyId,
            @Valid @RequestBody EtaReceiptWriteForm request) {
        EtaReceiptResponse response = service.create(companyId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    /**
     * Updates a DRAFT receipt with optimistic locking.
     *
     * @param companyId the company identifier
     * @param docId the document identifier
     * @param ifMatch the If-Match header value
     * @param request the receipt write form
     * @return the updated receipt with ETag
     */
    @PutMapping("/{docId}")
    @RequiresPermission(transactionType = "RECEIPT", action = "EDIT")
    public ResponseEntity<EtaReceiptResponse> update(
            @PathVariable UUID companyId,
            @PathVariable UUID docId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody EtaReceiptWriteForm request) {
        Integer version = parseIfMatch(ifMatch);
        EtaReceiptResponse response = service.update(docId, request,
                version);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    /**
     * Deletes a DRAFT receipt.
     *
     * @param companyId the company identifier
     * @param docId the document identifier
     * @return no content
     */
    @DeleteMapping("/{docId}")
    @RequiresPermission(transactionType = "RECEIPT", action = "DELETE")
    public ResponseEntity<Void> delete(
            @PathVariable UUID companyId,
            @PathVariable UUID docId) {
        service.delete(docId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Clones a REJECTED receipt as a new DRAFT.
     *
     * @param companyId the company identifier
     * @param docId the rejected document identifier
     * @param body the request body containing the new receipt number
     * @return the cloned draft with ETag
     */
    @PostMapping("/{docId}/clone-as-draft")
    @RequiresPermission(transactionType = "RECEIPT", action = "CREATE")
    public ResponseEntity<EtaReceiptResponse> cloneAsDraft(
            @PathVariable UUID companyId,
            @PathVariable UUID docId,
            @RequestBody Map<String, String> body) {
        EtaReceiptResponse response = service.cloneAsDraft(docId,
                body.get("receiptNumber"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    private Integer parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return null;
        }
        String cleaned = ifMatch.replace("\"", "").trim();
        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid If-Match header value: " + ifMatch);
        }
    }
}
