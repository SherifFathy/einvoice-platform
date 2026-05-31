package com.einvoice.api.integration;

import com.einvoice.api.error.ErrorResponse;
import com.einvoice.api.integration.dto.eta.EtaInvoiceIngestionRequest;
import com.einvoice.api.integration.dto.eta.EtaReceiptIngestionRequest;
import com.einvoice.api.integration.dto.shared.DocumentIngestionResponse;
import com.einvoice.api.integration.service.EtaIngestionService;
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

@RestController
@RequestMapping("/api/integration/v1/eta")
@Tag(name = "integration-gateway")
public class EtaIngestionController {

    private final EtaIngestionService etaIngestionService;

    public EtaIngestionController(EtaIngestionService etaIngestionService) {
        this.etaIngestionService = etaIngestionService;
    }

    @PostMapping("/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "ingestEtaReceipt",
            summary = "Ingest an ETA receipt (SDK v1.2 shape) that has been submitted externally.",
            description = "Accepted `environment` for this endpoint: **`PREPROD`** only."
                    + " `SANDBOX` is a valid DTO enum value but is not registered for ETA"
                    + " — passing it returns HTTP 404 `AUTHORITY_ENVIRONMENT_NOT_FOUND` by"
                    + " design (Constitution III registry: ETA has PRODUCTION and PREPROD"
                    + " only; PRODUCTION is intentionally absent from the gateway)."
    )
    @ApiResponse(
            responseCode = "201",
            description = "Receipt persisted.",
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
    public DocumentIngestionResponse ingestReceipt(
            @RequestBody @Valid EtaReceiptIngestionRequest req) {
        MDC.put("documentNumber", req.header().receiptNumber());
        if (req.erpReferenceId() != null) {
            MDC.put("erpReferenceId", req.erpReferenceId());
        }
        return etaIngestionService.ingestReceipt(req);
    }

    @PostMapping("/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "ingestEtaInvoice",
            summary = "Ingest an ETA invoice (SDK v1.0 shape) that has been submitted externally.",
            description = "Accepted `environment` for this endpoint: **`PREPROD`** only."
                    + " `SANDBOX` returns 404 for the same reason as `/eta/receipts`."
                    + " Credit / debit notes (`documentType='C'` or `'D'`) MUST include"
                    + " `originalInvoiceNumber`; the gateway performs hybrid resolution"
                    + " per FR-017 (raw string always persisted; FK populated only when"
                    + " the referenced invoice exists locally for the same company"
                    + " + environment)."
    )
    @ApiResponse(
            responseCode = "201",
            description = "Invoice persisted.",
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
    public DocumentIngestionResponse ingestInvoice(
            @RequestBody @Valid EtaInvoiceIngestionRequest req) {
        MDC.put("documentNumber", req.invoiceNumber());
        if (req.erpReferenceId() != null) {
            MDC.put("erpReferenceId", req.erpReferenceId());
        }
        return etaIngestionService.ingestInvoice(req);
    }
}
