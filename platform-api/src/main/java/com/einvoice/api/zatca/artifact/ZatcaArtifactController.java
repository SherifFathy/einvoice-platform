package com.einvoice.api.zatca.artifact;

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
import java.util.LinkedHashMap;
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

/** REST controller for downloading ZATCA submission artifacts. */
@RestController
@RequireOperationalMode
@RequestMapping("/api/companies/{companyId}/zatca")
public class ZatcaArtifactController {

    private final InvoiceArtifactRepository artifactRepository;
    private final SubmissionAttemptRepository attemptRepository;

    public ZatcaArtifactController(
            InvoiceArtifactRepository artifactRepository,
            SubmissionAttemptRepository attemptRepository) {
        this.artifactRepository = artifactRepository;
        this.attemptRepository = attemptRepository;
    }

    /**
     * List submission attempts for a standard document.
     *
     * @param companyId tenant company id
     * @param docId document id
     * @return ordered list of submission attempt maps
     */
    @GetMapping("/standard/{docId}/submissions")
    @RequiresPermission(transactionType = "STANDARD", action = "VIEW")
    public ResponseEntity<List<Map<String, Object>>>
            listStandardSubmissions(
                    @PathVariable UUID companyId,
                    @PathVariable UUID docId) {
        verifyContext(companyId);
        List<SubmissionAttempt> attempts =
                attemptRepository.findByDocumentIdAndTenant(
                        docId, companyId, TransactionType.STANDARD);
        return ResponseEntity.ok(
                attempts.stream().map(this::toAttemptMap).toList());
    }

    /**
     * List submission attempts for a simplified document.
     *
     * @param companyId tenant company id
     * @param docId document id
     * @return ordered list of submission attempt maps
     */
    @GetMapping("/simplified/{docId}/submissions")
    @RequiresPermission(transactionType = "SIMPLIFIED", action = "VIEW")
    public ResponseEntity<List<Map<String, Object>>>
            listSimplifiedSubmissions(
                    @PathVariable UUID companyId,
                    @PathVariable UUID docId) {
        verifyContext(companyId);
        List<SubmissionAttempt> attempts =
                attemptRepository.findByDocumentIdAndTenant(
                        docId, companyId,
                        TransactionType.SIMPLIFIED);
        return ResponseEntity.ok(
                attempts.stream().map(this::toAttemptMap).toList());
    }

    /**
     * Download an artifact for a standard document.
     *
     * @param companyId tenant company id
     * @param docId document id
     * @param type artifact type (see {@link ArtifactType})
     * @param attemptNumber optional submission attempt number; latest when null
     * @return artifact bytes with content-type derived from artifact type
     */
    @GetMapping("/standard/{docId}/artifacts/{type}")
    @RequiresPermission(transactionType = "STANDARD", action = "VIEW")
    public ResponseEntity<byte[]> downloadStandardArtifact(
            @PathVariable UUID companyId,
            @PathVariable UUID docId,
            @PathVariable String type,
            @RequestParam(required = false)
                    Integer attemptNumber) {
        verifyContext(companyId);
        return downloadArtifact(docId, type, attemptNumber,
                companyId, TransactionType.STANDARD);
    }

    /**
     * Download an artifact for a simplified document.
     *
     * @param companyId tenant company id
     * @param docId document id
     * @param type artifact type (see {@link ArtifactType})
     * @param attemptNumber optional submission attempt number; latest when null
     * @return artifact bytes with content-type derived from artifact type
     */
    @GetMapping("/simplified/{docId}/artifacts/{type}")
    @RequiresPermission(transactionType = "SIMPLIFIED", action = "VIEW")
    public ResponseEntity<byte[]> downloadSimplifiedArtifact(
            @PathVariable UUID companyId,
            @PathVariable UUID docId,
            @PathVariable String type,
            @RequestParam(required = false)
                    Integer attemptNumber) {
        verifyContext(companyId);
        return downloadArtifact(docId, type, attemptNumber,
                companyId, TransactionType.SIMPLIFIED);
    }

    private ResponseEntity<byte[]> downloadArtifact(UUID docId,
            String type, Integer attemptNumber, UUID companyId,
            TransactionType transactionType) {
        ArtifactType artifactType;
        try {
            artifactType = ArtifactType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        Short authEnvId =
                TenantContext.getAuthorityEnvironmentId();
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
                .header("X-Artifact-Hash",
                        artifact.getContentHash())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\""
                                + resolveFilename(artifactType)
                                + "\"")
                .contentType(resolveMediaType(artifactType))
                .body(artifact.getContent().getBytes());
    }

    private String resolveFilename(ArtifactType type) {
        return switch (type) {
          case UBL_XML -> "ubl_document.xml";
          case SIGNED_UBL_XML -> "signed_ubl_document.xml";
          case QR_PNG -> "qr_code.png";
          case CLEARED_XML -> "cleared_document.xml";
          case ZATCA_REQUEST -> "zatca_request.json";
          case ZATCA_RESPONSE -> "zatca_response.json";
          default -> "artifact";
        };
    }

    private MediaType resolveMediaType(ArtifactType type) {
        return switch (type) {
          case UBL_XML, SIGNED_UBL_XML, CLEARED_XML ->
                    MediaType.APPLICATION_XML;
          case QR_PNG -> MediaType.IMAGE_PNG;
          default -> MediaType.APPLICATION_JSON;
        };
    }

    private Map<String, Object> toAttemptMap(SubmissionAttempt a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId().toString());
        m.put("attemptNumber", a.getAttemptNumber());
        m.put("chainCounterSnapshot", a.getChainCounterSnapshot());
        m.put("result", a.getResult() != null
                ? a.getResult().name() : null);
        m.put("errorSummary", a.getErrorSummary());
        m.put("submittedBy", a.getSubmittedBy() != null
                ? a.getSubmittedBy().toString() : null);
        m.put("startedAt", a.getSubmittedAt() != null
                ? a.getSubmittedAt().toString() : null);
        m.put("finalisedAt", a.getCompletedAt() != null
                ? a.getCompletedAt().toString() : null);
        return m;
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
