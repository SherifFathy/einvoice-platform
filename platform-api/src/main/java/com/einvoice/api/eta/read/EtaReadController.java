package com.einvoice.api.eta.read;

import com.einvoice.api.eta.invoice.service.EtaInvoiceFormMapper.EtaInvoiceResponse;
import com.einvoice.api.eta.invoice.service.EtaInvoiceService;
import com.einvoice.api.eta.receipt.service.EtaReceiptFormMapper.EtaReceiptResponse;
import com.einvoice.api.eta.receipt.service.EtaReceiptService;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Company-less ETA read endpoints (ADR-001). Lists and fetches ETA invoices and
 * receipts scoped to the active {@code authority_environment_id}, with an
 * optional company filter; no per-company path segment or VIEW gate.
 */
@RestController
@RequestMapping("/api/eta")
public class EtaReadController {

    private final EtaInvoiceService invoiceService;
    private final EtaReceiptService receiptService;

    /**
     * Constructs the controller with the ETA read services.
     *
     * @param invoiceService ETA invoice read service
     * @param receiptService ETA receipt read service
     */
    public EtaReadController(EtaInvoiceService invoiceService,
            EtaReceiptService receiptService) {
        this.invoiceService = invoiceService;
        this.receiptService = receiptService;
    }

    /**
     * Lists ETA invoices in the active environment.
     *
     * @param status optional document-state filter
     * @param filterCompanyId optional single-company narrowing
     * @param branchId optional branch narrowing
     * @param dateFrom optional inclusive issue-date lower bound
     * @param dateTo optional inclusive issue-date upper bound
     * @param page zero-based page index
     * @param size page size
     * @return a paged envelope of invoice responses
     */
    @GetMapping("/invoices")
    public ResponseEntity<Map<String, Object>> listInvoices(
            @RequestParam(required = false) String status,
            @RequestParam(name = "companyId", required = false)
                    UUID filterCompanyId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<EtaInvoiceResponse> result = invoiceService.list(status,
                filterCompanyId, branchId, dateFrom, dateTo, page, size);
        return ResponseEntity.ok(Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements()));
    }

    /**
     * Fetches a single ETA invoice by id.
     *
     * @param docId the invoice identifier
     * @return the invoice response with an ETag header
     */
    @GetMapping("/invoices/{docId}")
    public ResponseEntity<EtaInvoiceResponse> getInvoice(
            @PathVariable UUID docId) {
        EtaInvoiceResponse response = invoiceService.findById(docId);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    /**
     * Lists ETA receipts in the active environment.
     *
     * @param status optional document-state filter
     * @param filterCompanyId optional single-company narrowing
     * @param branchId optional branch narrowing
     * @param receiptType optional receipt-type filter
     * @param dateFrom optional inclusive issue-date lower bound
     * @param dateTo optional inclusive issue-date upper bound
     * @param page zero-based page index
     * @param size page size
     * @return a paged envelope of receipt responses
     */
    @GetMapping("/receipts")
    public ResponseEntity<Map<String, Object>> listReceipts(
            @RequestParam(required = false) String status,
            @RequestParam(name = "companyId", required = false)
                    UUID filterCompanyId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String receiptType,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<EtaReceiptResponse> result = receiptService.list(status,
                filterCompanyId, branchId, receiptType, dateFrom, dateTo,
                page, size);
        return ResponseEntity.ok(Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements()));
    }

    /**
     * Fetches a single ETA receipt by id.
     *
     * @param docId the receipt identifier
     * @return the receipt response with an ETag header
     */
    @GetMapping("/receipts/{docId}")
    public ResponseEntity<EtaReceiptResponse> getReceipt(
            @PathVariable UUID docId) {
        EtaReceiptResponse response = receiptService.findById(docId);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }
}
