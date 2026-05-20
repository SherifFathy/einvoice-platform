package com.einvoice.api.zatca.standard;

import com.einvoice.api.zatca.standard.service.ZatcaStandardFormMapper.ZatcaStandardResponse;
import com.einvoice.api.zatca.standard.service.ZatcaStandardFormMapper.ZatcaStandardWriteForm;
import com.einvoice.api.zatca.standard.service.ZatcaStandardService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** REST controller for ZATCA Standard (B2B) invoice CRUD. */
@RestController
@RequireOperationalMode
@RequestMapping("/api/companies/{companyId}/zatca/standard")
public class ZatcaStandardController {

    private final ZatcaStandardService service;

    /** Inject service. */
    public ZatcaStandardController(ZatcaStandardService service) {
        this.service = service;
    }

    /** List standard documents with optional filters. */
    @GetMapping
    @RequiresPermission(transactionType = "STANDARD", action = "VIEW")
    public ResponseEntity<Map<String, Object>> list(
            @PathVariable UUID companyId,
            @RequestParam(required = false) String status,
            @RequestParam(name = "company", required = false)
                    UUID filterCompanyId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        verifyContext(companyId);
        Page<ZatcaStandardResponse> result = service.list(status,
                filterCompanyId, dateFrom, dateTo, page, size);
        return ResponseEntity.ok(Map.of(
                "items", result.getContent(),
                "page", result.getNumber(),
                "size", result.getSize(),
                "totalElements", result.getTotalElements()));
    }

    /** Get a single standard document by ID. */
    @GetMapping("/{docId}")
    @RequiresPermission(transactionType = "STANDARD", action = "VIEW")
    public ResponseEntity<ZatcaStandardResponse> get(
            @PathVariable UUID companyId,
            @PathVariable UUID docId) {
        verifyContext(companyId);
        ZatcaStandardResponse response = service.findById(docId);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    /** Create a new standard document. */
    @PostMapping
    @RequiresPermission(transactionType = "STANDARD", action = "CREATE")
    public ResponseEntity<ZatcaStandardResponse> create(
            @PathVariable UUID companyId,
            @Valid @RequestBody ZatcaStandardWriteForm request) {
        verifyContext(companyId);
        ZatcaStandardResponse response = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    /** Update an existing draft standard document. */
    @PutMapping("/{docId}")
    @RequiresPermission(transactionType = "STANDARD", action = "EDIT")
    public ResponseEntity<ZatcaStandardResponse> update(
            @PathVariable UUID companyId,
            @PathVariable UUID docId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody ZatcaStandardWriteForm request) {
        verifyContext(companyId);
        Long version = parseIfMatch(ifMatch);
        ZatcaStandardResponse response = service.update(docId, request,
                version);
        return ResponseEntity.ok()
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    /** Delete a draft standard document. */
    @DeleteMapping("/{docId}")
    @RequiresPermission(transactionType = "STANDARD", action = "DELETE")
    public ResponseEntity<Void> delete(
            @PathVariable UUID companyId,
            @PathVariable UUID docId) {
        verifyContext(companyId);
        service.delete(docId);
        return ResponseEntity.noContent().build();
    }

    /** Clone an existing document as a new draft. */
    @PostMapping("/{docId}/clone-as-draft")
    @RequiresPermission(transactionType = "STANDARD", action = "CREATE")
    public ResponseEntity<ZatcaStandardResponse> cloneAsDraft(
            @PathVariable UUID companyId,
            @PathVariable UUID docId,
            @RequestBody Map<String, String> body) {
        verifyContext(companyId);
        ZatcaStandardResponse response = service.cloneAsDraft(docId,
                body.get("newInvoiceNumber"));
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag("\"" + response.version() + "\"")
                .body(response);
    }

    private void verifyContext(UUID pathCompanyId) {
        UUID jwtCompanyId = TenantContext.getCompanyId();
        if (jwtCompanyId == null
                || !jwtCompanyId.equals(pathCompanyId)) {
            throw new UnauthorizedContextException(
                    "Path companyId does not match authenticated"
                            + " company context");
        }
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
