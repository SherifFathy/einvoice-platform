package com.einvoice.api.integration;

import com.einvoice.api.error.ErrorResponse;
import com.einvoice.api.integration.dto.shared.DocumentIngestionResponse;
import com.einvoice.api.integration.dto.zatca.ZatcaSimplifiedInvoiceIngestionRequest;
import com.einvoice.api.integration.dto.zatca.ZatcaStandardInvoiceIngestionRequest;
import com.einvoice.api.integration.service.ZatcaIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * ERP ingestion gateway for externally-cleared ZATCA documents (Wave 8).
 * Accepts SDK-shaped ZATCA standard and simplified payloads for the SANDBOX
 * environment and persists them via {@link ZatcaIngestionService}.
 */
@RestController
@RequestMapping("/api/integration/v1/zatca")
@Tag(name = "integration-gateway")
public class ZatcaIngestionController {

    private final ZatcaIngestionService zatcaIngestionService;

    /**
     * Constructs the controller with the ZATCA ingestion service.
     *
     * @param zatcaIngestionService the ZATCA ingestion service
     */
    public ZatcaIngestionController(ZatcaIngestionService zatcaIngestionService) {
        this.zatcaIngestionService = zatcaIngestionService;
    }

    /**
     * Ingests an externally-cleared ZATCA standard (B2B) invoice.
     *
     * @param req the SDK-shaped standard-invoice ingestion request
     * @return the persisted-document ingestion response
     */
    @PostMapping("/standard")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "ingestZatcaStandard",
            summary = "Ingest a ZATCA Standard (B2B) invoice that has been cleared by ZATCA.",
            description = "Accepted `environment` for this endpoint: **`SANDBOX`** only."
                    + " `PREPROD` is a valid DTO enum value but not registered for ZATCA"
                    + " — passing it returns HTTP 404 `AUTHORITY_ENVIRONMENT_NOT_FOUND`"
                    + " (Constitution III registry: ZATCA has PRODUCTION, SIMULATION,"
                    + " SANDBOX; SIMULATION is intentionally not exposed by the gateway;"
                    + " PRODUCTION is intentionally absent). `transactionTypeCode` MUST"
                    + " match `1[01]{3}0000` (bit-1 = 1 enforces Standard). `buyer` is"
                    + " required."
    )
    @ApiResponse(
            responseCode = "201",
            description = "Standard invoice persisted.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DocumentIngestionResponse.class)
            )
    )
    @ApiResponse(
            responseCode = "503",
            description = "Archive write failed",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ErrorResponse.class)
            )
    )
    public DocumentIngestionResponse ingestStandard(
            @RequestBody @Valid ZatcaStandardInvoiceIngestionRequest req) {
        MDC.put("documentNumber", req.invoiceNumber());
        if (req.erpReferenceId() != null) {
            MDC.put("erpReferenceId", req.erpReferenceId());
        }
        return zatcaIngestionService.ingestStandard(req);
    }

    /**
     * Ingests an externally-reported ZATCA simplified (B2C) invoice.
     *
     * @param req the SDK-shaped simplified-invoice ingestion request
     * @return the persisted-document ingestion response
     */
    @PostMapping("/simplified")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "ingestZatcaSimplified",
            summary = "Ingest a ZATCA Simplified (B2C) invoice that has been reported to ZATCA."
                    + " Anonymous buyer permitted.",
            description = "Accepted `environment` for this endpoint: **`SANDBOX`** only"
                    + " (same reason as `/zatca/standard`). `transactionTypeCode` MUST"
                    + " match `0[01]{3}0000` (bit-1 = 0 enforces Simplified). `buyer` is"
                    + " OPTIONAL — anonymous B2C retail is the dominant case. The status"
                    + " field maps to `reporting_status` (not `clearance_status`)."
    )
    @ApiResponse(
            responseCode = "201",
            description = "Simplified invoice persisted.",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DocumentIngestionResponse.class)
            )
    )
    @ApiResponse(
            responseCode = "503",
            description = "Archive write failed",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ErrorResponse.class)
            )
    )
    public DocumentIngestionResponse ingestSimplified(
            @RequestBody @Valid ZatcaSimplifiedInvoiceIngestionRequest req) {
        MDC.put("documentNumber", req.invoiceNumber());
        if (req.erpReferenceId() != null) {
            MDC.put("erpReferenceId", req.erpReferenceId());
        }
        return zatcaIngestionService.ingestSimplified(req);
    }
}
