package com.einvoice.api.eta.invoice;

import com.einvoice.api.eta.invoice.service.EtaInvoiceFormMapper.EtaInvoiceResponse;
import com.einvoice.api.eta.invoice.service.EtaInvoiceFormMapper.EtaInvoiceWriteForm;
import com.einvoice.api.eta.invoice.service.EtaInvoiceService;
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

/** REST controller for ETA invoice CRUD and lifecycle endpoints. */
@RestController
@RequireOperationalMode
@RequestMapping("/api/companies/{companyId}/eta/invoices")
public class EtaInvoiceController {

    private final EtaInvoiceService service;

    /**
     * Constructs an EtaInvoiceController.
     *
     * @param service the invoice service
     */
    public EtaInvoiceController(EtaInvoiceService service) {
        this.service = service;
    }

    /**
     * Lists invoices with optional filters.
     *
     * @param companyId the company identifier
     * @param status optional state filter
     * @param filterCompanyId optional company filter
     * @param dateFrom optional start date
     * @param dateTo optional end date
     * @param page page number
     * @param size page size
     * @return paged invoice list
     */
    @GetMapping
    @RequiresPermission(transactionType = "INVOICE", action = "VIEW")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable UUID companyId,
            @RequestParam(required = false) String status,
            @RequestParam(name = "companyId", required = false)
                    UUID filterCompanyId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<EtaInvoiceResponse> result = service.list(status,
                filterCompanyId, dateFrom, dateTo, page, size);
        return ResponseEntity.ok(Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements()));
    }

    /**
     * Gets a single invoice by ID.
     *
     * @param companyId the company identifier
     * @param docId the document identifier
     * @return the invoice with ETag
     */
    @GetMapping("/{docId}")
    @RequiresPermission(transactionType = "INVOICE", action = "VIEW")
    public ResponseEntity<EtaInvoiceResponse> get(
            @PathVariable UUID companyId,
            @PathVariable UUID docId) {
        EtaInvoiceResponse response = service.findById(docId);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    /**
     * Creates a new DRAFT invoice.
     *
     * @param companyId the company identifier
     * @param request the invoice write form
     * @return the created invoice with ETag
     */
    @PostMapping
    @RequiresPermission(transactionType = "INVOICE", action = "CREATE")
    public ResponseEntity<EtaInvoiceResponse> create(
            @PathVariable UUID companyId,
            @Valid @RequestBody EtaInvoiceWriteForm request) {
        EtaInvoiceResponse response = service.create(companyId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    /**
     * Updates a DRAFT invoice with optimistic locking.
     *
     * @param companyId the company identifier
     * @param docId the document identifier
     * @param ifMatch the If-Match header value
     * @param request the invoice write form
     * @return the updated invoice with ETag
     */
    @PutMapping("/{docId}")
    @RequiresPermission(transactionType = "INVOICE", action = "EDIT")
    public ResponseEntity<EtaInvoiceResponse> update(
            @PathVariable UUID companyId,
            @PathVariable UUID docId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody EtaInvoiceWriteForm request) {
        Integer version = parseIfMatch(ifMatch);
        EtaInvoiceResponse response = service.update(docId, request,
                version);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    /**
     * Deletes a DRAFT invoice.
     *
     * @param companyId the company identifier
     * @param docId the document identifier
     * @return no content
     */
    @DeleteMapping("/{docId}")
    @RequiresPermission(transactionType = "INVOICE", action = "DELETE")
    public ResponseEntity<Void> delete(
            @PathVariable UUID companyId,
            @PathVariable UUID docId) {
        service.delete(docId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Clones a REJECTED invoice as a new DRAFT.
     *
     * @param companyId the company identifier
     * @param docId the rejected document identifier
     * @param body the request body containing the new invoice number
     * @return the cloned draft with ETag
     */
    @PostMapping("/{docId}/clone-as-draft")
    @RequiresPermission(transactionType = "INVOICE", action = "CREATE")
    public ResponseEntity<EtaInvoiceResponse> cloneAsDraft(
            @PathVariable UUID companyId,
            @PathVariable UUID docId,
            @RequestBody Map<String, String> body) {
        EtaInvoiceResponse response = service.cloneAsDraft(docId,
                body.get("invoiceNumber"));
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
