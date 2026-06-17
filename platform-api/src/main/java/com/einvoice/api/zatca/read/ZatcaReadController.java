package com.einvoice.api.zatca.read;

import com.einvoice.api.zatca.simplified.service.ZatcaSimplifiedFormMapper.ZatcaSimplifiedResponse;
import com.einvoice.api.zatca.simplified.service.ZatcaSimplifiedService;
import com.einvoice.api.zatca.standard.service.ZatcaStandardFormMapper.ZatcaStandardResponse;
import com.einvoice.api.zatca.standard.service.ZatcaStandardService;
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
 * Company-less ZATCA read endpoints (ADR-001). Lists and fetches ZATCA standard
 * and simplified invoices scoped to the active {@code authority_environment_id},
 * with an optional company filter; no per-company path segment or VIEW gate.
 */
@RestController
@RequestMapping("/api/zatca")
public class ZatcaReadController {

    private final ZatcaStandardService standardService;
    private final ZatcaSimplifiedService simplifiedService;

    /**
     * Constructs the controller with the ZATCA read services.
     *
     * @param standardService ZATCA standard read service
     * @param simplifiedService ZATCA simplified read service
     */
    public ZatcaReadController(ZatcaStandardService standardService,
            ZatcaSimplifiedService simplifiedService) {
        this.standardService = standardService;
        this.simplifiedService = simplifiedService;
    }

    /**
     * Lists ZATCA standard invoices in the active environment.
     *
     * @param status optional document-state filter
     * @param filterCompanyId optional single-company narrowing
     * @param branchId optional branch narrowing
     * @param dateFrom optional inclusive issue-date lower bound
     * @param dateTo optional inclusive issue-date upper bound
     * @param page zero-based page index
     * @param size page size
     * @return a paged envelope of standard-invoice responses
     */
    @GetMapping("/standard")
    public ResponseEntity<Map<String, Object>> listStandard(
            @RequestParam(required = false) String status,
            @RequestParam(name = "company", required = false)
                    UUID filterCompanyId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<ZatcaStandardResponse> result = standardService.list(status,
                filterCompanyId, branchId, dateFrom, dateTo, page, size);
        return ResponseEntity.ok(Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements()));
    }

    /**
     * Fetches a single ZATCA standard invoice by id.
     *
     * @param docId the standard-invoice identifier
     * @return the standard-invoice response with an ETag header
     */
    @GetMapping("/standard/{docId}")
    public ResponseEntity<ZatcaStandardResponse> getStandard(
            @PathVariable UUID docId) {
        ZatcaStandardResponse response = standardService.findById(docId);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    /**
     * Lists ZATCA simplified invoices in the active environment.
     *
     * @param status optional document-state filter
     * @param filterCompanyId optional single-company narrowing
     * @param branchId optional branch narrowing
     * @param dateFrom optional inclusive issue-date lower bound
     * @param dateTo optional inclusive issue-date upper bound
     * @param page zero-based page index
     * @param size page size
     * @return a paged envelope of simplified-invoice responses
     */
    @GetMapping("/simplified")
    public ResponseEntity<Map<String, Object>> listSimplified(
            @RequestParam(required = false) String status,
            @RequestParam(name = "company", required = false)
                    UUID filterCompanyId,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<ZatcaSimplifiedResponse> result = simplifiedService.list(status,
                filterCompanyId, branchId, dateFrom, dateTo, page, size);
        return ResponseEntity.ok(Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements()));
    }

    /**
     * Fetches a single ZATCA simplified invoice by id.
     *
     * @param docId the simplified-invoice identifier
     * @return the simplified-invoice response with an ETag header
     */
    @GetMapping("/simplified/{docId}")
    public ResponseEntity<ZatcaSimplifiedResponse> getSimplified(
            @PathVariable UUID docId) {
        ZatcaSimplifiedResponse response = simplifiedService.findById(docId);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }
}
