package com.einvoice.api.eta.artifact;

import com.einvoice.core.domain.shared.ArtifactType;
import com.einvoice.core.domain.shared.InvoiceArtifact;
import com.einvoice.core.domain.shared.SubmissionAttempt;
import com.einvoice.core.domain.shared.TransactionType;
import com.einvoice.core.error.UnauthorizedContextException;
import com.einvoice.core.repository.shared.InvoiceArtifactRepository;
import com.einvoice.core.repository.shared.SubmissionAttemptRepository;
import com.einvoice.core.security.RequiresPermission;
import com.einvoice.security.operational.RequireOperationalMode;
import com.einvoice.security.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for ETA artifact download and submission-history endpoints. */
@RestController
@RequireOperationalMode
@RequestMapping("/api/companies/{companyId}/eta")
public class EtaArtifactController {

    private final InvoiceArtifactRepository artifactRepository;
    private final SubmissionAttemptRepository attemptRepository;

    public EtaArtifactController(InvoiceArtifactRepository artifactRepository,
            SubmissionAttemptRepository attemptRepository) {
        this.artifactRepository = artifactRepository;
        this.attemptRepository = attemptRepository;
    }

    /**
     * Lists submission attempts for a given invoice.
     *
     * @param companyId the company identifier
     * @param docId the document identifier
     * @return list of submission attempt summaries
     */
    @GetMapping("/invoices/{docId}/submissions")
    @RequiresPermission(transactionType = "INVOICE", action = "VIEW")
    public ResponseEntity<List<Map<String, Object>>> listInvoiceSubmissions(
            @PathVariable UUID companyId,
            @PathVariable UUID docId) {
        verifyContext(companyId);
        List<SubmissionAttempt> attempts =
                attemptRepository.findByDocumentIdAndTenant(
                        docId, companyId, TransactionType.INVOICE);
        List<Map<String, Object>> body = attempts.stream()
                .map(this::toAttemptMap)
                .toList();
        return ResponseEntity.ok(body);
    }

    /**
     * Lists submission attempts for a given receipt.
     *
     * @param companyId the company identifier
     * @param docId the document identifier
     * @return list of submission attempt summaries
     */
    @GetMapping("/receipts/{docId}/submissions")
    @RequiresPermission(transactionType = "RECEIPT", action = "VIEW")
    public ResponseEntity<List<Map<String, Object>>> listReceiptSubmissions(
            @PathVariable UUID companyId,
            @PathVariable UUID docId) {
        verifyContext(companyId);
        List<SubmissionAttempt> attempts =
                attemptRepository.findByDocumentIdAndTenant(
                        docId, companyId, TransactionType.RECEIPT);
        List<Map<String, Object>> body = attempts.stream()
                .map(this::toAttemptMap)
                .toList();
        return ResponseEntity.ok(body);
    }

    /**
     * Downloads an invoice artifact by type.
     *
     * @param companyId the company identifier
     * @param docId the document identifier
     * @param type the artifact type
     * @param attemptNumber optional attempt number filter
     * @return the artifact bytes
     */
    @GetMapping("/invoices/{docId}/artifacts/{type}")
    @RequiresPermission(transactionType = "INVOICE", action = "VIEW")
    public ResponseEntity<byte[]> downloadInvoiceArtifact(
            @PathVariable UUID companyId,
            @PathVariable UUID docId,
            @PathVariable String type,
            @RequestParam(required = false) Integer attemptNumber) {
        verifyContext(companyId);
        return downloadArtifact(docId, type, attemptNumber,
                companyId, TransactionType.INVOICE);
    }

    /**
     * Downloads a receipt artifact by type.
     *
     * @param companyId the company identifier
     * @param docId the document identifier
     * @param type the artifact type
     * @param attemptNumber optional attempt number filter
     * @return the artifact bytes
     */
    @GetMapping("/receipts/{docId}/artifacts/{type}")
    @RequiresPermission(transactionType = "RECEIPT", action = "VIEW")
    public ResponseEntity<byte[]> downloadReceiptArtifact(
            @PathVariable UUID companyId,
            @PathVariable UUID docId,
            @PathVariable String type,
            @RequestParam(required = false) Integer attemptNumber) {
        verifyContext(companyId);
        return downloadArtifact(docId, type, attemptNumber,
                companyId, TransactionType.RECEIPT);
    }

    private ResponseEntity<byte[]> downloadArtifact(UUID docId, String type,
            Integer attemptNumber, UUID companyId,
            TransactionType transactionType) {
        ArtifactType artifactType;
        try {
            artifactType = ArtifactType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        Short authEnvId = TenantContext.getAuthorityEnvironmentId();
        List<InvoiceArtifact> artifacts;
        if (attemptNumber != null) {
            artifacts = artifactRepository
                    .findByDocumentIdAndTypeAndAttemptAndTenant(
                            docId, artifactType, attemptNumber,
                            companyId, authEnvId, transactionType);
        } else {
            artifacts = artifactRepository
                    .findByDocumentIdAndTypeAndTenant(
                            docId, artifactType, companyId,
                            authEnvId, transactionType);
        }

        if (artifacts.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        InvoiceArtifact artifact = artifacts.get(0);

        return ResponseEntity.ok()
                .header("X-Artifact-Hash", artifact.getContentHash())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\""
                                + resolveFilename(artifactType) + "\"")
                .contentType(resolveMediaType(artifactType))
                .body(artifact.getContent().getBytes());
    }

    private String resolveFilename(ArtifactType type) {
        return switch (type) {
          case SIGNED_JSON -> "signed_document.json";
          case SIGNED_XML -> "signed_document.xml";
          case CLEARED_XML -> "cleared_document.xml";
          case QR_CODE -> "qr_code.png";
          case ETA_RESPONSE -> "eta_response.json";
          case ZATCA_RESPONSE -> "zatca_response.json";
        };
    }

    private MediaType resolveMediaType(ArtifactType type) {
        return switch (type) {
          case SIGNED_JSON, ETA_RESPONSE -> MediaType.APPLICATION_JSON;
          case SIGNED_XML, CLEARED_XML ->
                  MediaType.APPLICATION_XML;
          case QR_CODE -> MediaType.IMAGE_PNG;
          case ZATCA_RESPONSE -> MediaType.APPLICATION_JSON;
        };
    }

    private Map<String, Object> toAttemptMap(SubmissionAttempt a) {
        return Map.of(
                "id", (Object) a.getId().toString(),
                "attemptNumber", a.getAttemptNumber(),
                "submittedBy", a.getSubmittedBy() != null
                        ? a.getSubmittedBy().toString() : "",
                "result", a.getResult() != null
                        ? a.getResult().name() : "",
                "statusCode", a.getStatusCode() != null
                        ? a.getStatusCode() : 0,
                "errorSummary", a.getErrorSummary() != null
                        ? a.getErrorSummary() : "",
                "submittedAt", a.getSubmittedAt() != null
                        ? a.getSubmittedAt().toString() : "",
                "completedAt", a.getCompletedAt() != null
                        ? a.getCompletedAt().toString() : ""
        );
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
}
