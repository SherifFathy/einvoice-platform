package com.einvoice.core.service;

import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.InvoiceArtifact;
import com.einvoice.core.domain.enums.ArtifactType;
import com.einvoice.core.repository.InvoiceArtifactRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Service for storing and retrieving immutable invoice artifacts. */
@Service
public class InvoiceArtifactService {

    private final InvoiceArtifactRepository artifactRepository;

    public InvoiceArtifactService(InvoiceArtifactRepository artifactRepository) {
        this.artifactRepository = artifactRepository;
    }

    /**
     * Stores an artifact with SHA-256 content hash.
     *
     * @param invoice the parent invoice
     * @param type the artifact type
     * @param content the artifact content
     * @return the persisted artifact
     */
    @Transactional
    public InvoiceArtifact storeArtifact(Invoice invoice, ArtifactType type, String content) {
        String hash = computeSha256(content);
        InvoiceArtifact artifact = InvoiceArtifact.builder()
                .invoice(invoice)
                .artifactType(type)
                .content(content)
                .contentHash(hash)
                .build();
        return artifactRepository.save(artifact);
    }

    public List<InvoiceArtifact> getArtifacts(UUID invoiceId) {
        return artifactRepository.findByInvoiceIdOrderByCreatedAtAsc(invoiceId);
    }

    public List<InvoiceArtifact> getArtifactsByType(UUID invoiceId, List<ArtifactType> types) {
        return artifactRepository.findByInvoiceIdAndArtifactTypeIn(invoiceId, types);
    }

    public java.util.Optional<InvoiceArtifact> getLatestArtifact(UUID invoiceId, ArtifactType type) {
        return artifactRepository.findByInvoiceIdAndArtifactTypeOrderByCreatedAtDesc(invoiceId, type)
                .stream().findFirst();
    }

    String computeSha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
