package com.einvoice.api.eta;

import com.einvoice.api.eta.dto.CodeRequest;
import com.einvoice.api.eta.dto.CodeResponse;
import com.einvoice.api.eta.dto.PublishedCodeResponse;
import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.EtaItemCode;
import com.einvoice.core.domain.enums.EtaItemCodeStatus;
import com.einvoice.eta.codes.EtaCodeService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for ETA item code management.
 */
@RestController
@RequestMapping("/api/eta/codes")
public class EtaCodeController {

    private final EtaCodeService etaCodeService;

    /**
     * Creates the ETA code controller.
     *
     * @param etaCodeService the ETA code service
     */
    public EtaCodeController(EtaCodeService etaCodeService) {
        this.etaCodeService = etaCodeService;
    }

    /**
     * Registers a new item code with ETA.
     *
     * @param request the code registration request
     * @return the created code response
     */
    @PostMapping
    @PreAuthorize("hasAuthority('CREATE')")
    public ResponseEntity<CodeResponse> registerCode(
            @Valid @RequestBody CodeRequest request) {
        Long companyId = TenantContext.getCurrentTenantId();
        EtaItemCode code = etaCodeService.registerCode(
                companyId, request.itemCode(), request.codeType(),
                request.description());
        return ResponseEntity
                .created(URI.create("/api/eta/codes/" + code.getId()))
                .body(toResponse(code));
    }

    /**
     * Lists the company's ETA item code registrations.
     *
     * @param status optional status filter
     * @param search optional search term
     * @param pageable pagination parameters
     * @return a page of code responses
     */
    @GetMapping
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<Page<CodeResponse>> listCodes(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        Long companyId = TenantContext.getCurrentTenantId();
        EtaItemCodeStatus statusFilter = parseStatus(status);
        Page<EtaItemCode> codes = etaCodeService.listCodes(
                companyId, statusFilter, search, pageable);
        return ResponseEntity.ok(codes.map(this::toResponse));
    }

    /**
     * Searches ETA's published code directory.
     *
     * @param query the search query
     * @param pageable pagination parameters
     * @return a page of published code responses
     */
    @GetMapping("/search-published")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<Page<PublishedCodeResponse>> searchPublished(
            @RequestParam String query,
            @PageableDefault(size = 20) Pageable pageable) {
        Long companyId = TenantContext.getCurrentTenantId();
        Page<JsonNode> results = etaCodeService.searchPublishedCodes(
                companyId, query, pageable);
        Page<PublishedCodeResponse> mapped = results.map(this::toPublishedResponse);
        return ResponseEntity.ok(mapped);
    }

    /**
     * Updates an existing code registration.
     *
     * @param id the code database ID
     * @param request the update request
     * @return the updated code response
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('UPDATE')")
    public ResponseEntity<CodeResponse> updateCode(@PathVariable Long id,
            @Valid @RequestBody CodeRequest request) {
        Long companyId = TenantContext.getCurrentTenantId();
        EtaItemCode code = etaCodeService.updateCode(
                companyId, id, request.itemCode(), request.codeType(),
                request.description());
        return ResponseEntity.ok(toResponse(code));
    }

    private CodeResponse toResponse(EtaItemCode code) {
        return new CodeResponse(
                code.getId(),
                code.getItemCode(),
                code.getCodeType(),
                code.getDescription(),
                code.getStatus().name(),
                code.getEtaCodeId(),
                code.getCreatedAt(),
                code.getUpdatedAt());
    }

    private PublishedCodeResponse toPublishedResponse(JsonNode node) {
        OffsetDateTime publishedAt = null;
        String dateStr = node.path("publishedAt").asText(null);
        if (dateStr != null && !dateStr.isBlank()) {
            try {
                publishedAt = OffsetDateTime.parse(dateStr,
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            } catch (Exception e) {
                publishedAt = null;
            }
        }
        return new PublishedCodeResponse(
                node.path("itemCode").asText(null),
                node.path("codeType").asText(null),
                node.path("description").asText(null),
                node.path("publishedBy").asText("ETA"),
                publishedAt);
    }

    private EtaItemCodeStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return EtaItemCodeStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid status filter: " + status
                            + ". Valid values: PENDING, APPROVED, REJECTED");
        }
    }
}
