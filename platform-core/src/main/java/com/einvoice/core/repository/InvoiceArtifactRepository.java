package com.einvoice.core.repository;

import com.einvoice.core.domain.InvoiceArtifact;
import com.einvoice.core.domain.enums.ArtifactType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Insert-only repository for {@link InvoiceArtifact} data access. */
public interface InvoiceArtifactRepository extends Repository<InvoiceArtifact, Long> {

    InvoiceArtifact save(InvoiceArtifact entity);

    List<InvoiceArtifact> findByInvoiceIdOrderByCreatedAtAsc(UUID invoiceId);

    List<InvoiceArtifact> findByInvoiceIdAndArtifactTypeOrderByCreatedAtDesc(
            UUID invoiceId, ArtifactType artifactType);

    List<InvoiceArtifact> findByInvoiceIdAndArtifactTypeIn(UUID invoiceId,
            List<ArtifactType> artifactTypes);
}
