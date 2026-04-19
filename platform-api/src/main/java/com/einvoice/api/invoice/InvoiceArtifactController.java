package com.einvoice.api.invoice;

import com.einvoice.api.invoice.dto.ArtifactListResponse;
import com.einvoice.api.invoice.dto.ArtifactResponse;
import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.InvoiceArtifact;
import com.einvoice.core.domain.enums.ArtifactType;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.repository.AuthorityConfigRepository;
import com.einvoice.core.repository.InvoiceRepository;
import com.einvoice.core.service.InvoiceArtifactService;
import com.einvoice.eta.client.EtaDocumentClient;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for managing invoice artifacts. */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceArtifactController {

    private static final String CONTENT_TYPE_XML = "application/xml";
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String CONTENT_TYPE_TEXT = "text/plain";
    private static final String CONTENT_TYPE_PDF = "application/pdf";

    private final InvoiceRepository invoiceRepository;
    private final InvoiceArtifactService artifactService;
    private final EtaDocumentClient etaDocumentClient;
    private final AuthorityConfigRepository authorityConfigRepository;

    /**
     * Creates the controller with its required dependencies.
     *
     * @param invoiceRepository the invoice repository
     * @param artifactService the artifact service
     * @param etaDocumentClient the ETA document client
     * @param authorityConfigRepository the authority configuration repository
     */
    public InvoiceArtifactController(InvoiceRepository invoiceRepository,
            InvoiceArtifactService artifactService,
            EtaDocumentClient etaDocumentClient,
            AuthorityConfigRepository authorityConfigRepository) {
        this.invoiceRepository = invoiceRepository;
        this.artifactService = artifactService;
        this.etaDocumentClient = etaDocumentClient;
        this.authorityConfigRepository = authorityConfigRepository;
    }

    /**
     * Lists all artifacts for an invoice.
     *
     * @param id the invoice UUID
     * @return list of artifact metadata
     */
    @GetMapping("/{id}/artifacts")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<ArtifactListResponse> listArtifacts(@PathVariable UUID id) {
        Long companyId = TenantContext.getCurrentTenantId();
        invoiceRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + id));

        List<InvoiceArtifact> artifacts = artifactService.getArtifacts(id);
        List<ArtifactResponse> responses = artifacts.stream()
                .map(a -> new ArtifactResponse(
                        a.getId(),
                        a.getArtifactType().name(),
                        "sha256:" + a.getContentHash(),
                        a.getCreatedAt(),
                        "/api/invoices/" + id + "/artifacts/" + a.getArtifactType().name()))
                .toList();

        return ResponseEntity.ok(new ArtifactListResponse(id.toString(), responses));
    }

    /**
     * Downloads a specific artifact by type.
     *
     * @param id the invoice UUID
     * @param type the artifact type name
     * @return the artifact content as bytes
     */
    @GetMapping("/{id}/artifacts/{type}")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<byte[]> downloadArtifact(@PathVariable UUID id,
            @PathVariable String type) {
        Long companyId = TenantContext.getCurrentTenantId();
        Invoice invoice = invoiceRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + id));

        ArtifactType artifactType = ArtifactType.valueOf(type);
        InvoiceArtifact artifact = artifactService.getLatestArtifact(id, artifactType)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Artifact not found: " + type));

        String contentType = resolveContentType(artifactType);
        String filename = buildFilename(invoice, artifactType);

        byte[] contentBytes;
        if (artifactType == ArtifactType.ETA_PDF) {
            contentBytes = Base64.getDecoder().decode(artifact.getContent());
        } else {
            contentBytes = artifact.getContent().getBytes(StandardCharsets.UTF_8);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .header("X-Content-Hash", "sha256:" + artifact.getContentHash())
                .body(contentBytes);
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private String resolveContentType(ArtifactType type) {
        return switch (type) {
          case SIGNED_XML, CLEARED_XML -> CONTENT_TYPE_XML;
          case SIGNED_JSON, ETA_RESPONSE, ZATCA_RESPONSE, ETA_CADES_SIG -> CONTENT_TYPE_JSON;
          case QR_CODE -> CONTENT_TYPE_TEXT;
          case ETA_PDF -> CONTENT_TYPE_PDF;
        };
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    private String buildFilename(Invoice invoice, ArtifactType type) {
        String number = invoice.getInvoiceNumber() != null
                ? invoice.getInvoiceNumber() : invoice.getId().toString();
        String ext = switch (type) {
          case SIGNED_XML, CLEARED_XML -> "xml";
          case SIGNED_JSON, ETA_RESPONSE, ZATCA_RESPONSE, ETA_CADES_SIG -> "json";
          case QR_CODE -> "txt";
          case ETA_PDF -> "pdf";
        };
        return "invoice-" + number + "-" + type.name().toLowerCase() + "." + ext;
    }

    /**
     * Downloads the ETA-rendered PDF for an invoice, caching it if not already stored.
     *
     * @param id the invoice UUID
     * @return the PDF bytes
     */
    @GetMapping("/{id}/eta-pdf")
    @PreAuthorize("hasAuthority('READ')")
    public ResponseEntity<byte[]> downloadEtaPdf(@PathVariable UUID id) {
        Long companyId = TenantContext.getCurrentTenantId();
        Invoice invoice = invoiceRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new IllegalArgumentException("Invoice not found: " + id));

        if (invoice.getAuthority() != Authority.ETA) {
            return ResponseEntity.status(409)
                    .header("X-Error", "PDF download is only available for ETA invoices")
                    .body(null);
        }

        Optional<InvoiceArtifact> existingPdf = artifactService.getLatestArtifact(
                id, ArtifactType.ETA_PDF);
        if (existingPdf.isPresent()) {
            byte[] pdfContent = Base64.getDecoder()
                    .decode(existingPdf.get().getContent());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"invoice-"
                                    + (invoice.getInvoiceNumber() != null
                                            ? invoice.getInvoiceNumber() : id)
                                    + "-eta-pdf.pdf\"")
                    .header("X-Content-Hash", "sha256:" + existingPdf.get().getContentHash())
                    .body(pdfContent);
        }

        AuthorityConfig config = authorityConfigRepository
                .findByBranchIdAndAuthorityAndEnvironment(
                        invoice.getBranch().getId(),
                        Authority.ETA,
                        invoice.getEnvironment())
                .orElseThrow(() -> new IllegalArgumentException("ETA config not found"));

        String documentId = invoice.getExternalInvoiceReference() != null
                ? invoice.getExternalInvoiceReference()
                : invoice.getInvoiceNumber();

        Optional<byte[]> pdfBytes = etaDocumentClient.downloadPdf(documentId, config);
        if (pdfBytes.isEmpty()) {
            return ResponseEntity.status(404)
                    .header("X-Error", "PDF not available for this invoice")
                    .body(null);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"invoice-"
                                + (invoice.getInvoiceNumber() != null
                                        ? invoice.getInvoiceNumber() : id)
                                + "-eta-pdf.pdf\"")
                .body(pdfBytes.get());
    }
}
