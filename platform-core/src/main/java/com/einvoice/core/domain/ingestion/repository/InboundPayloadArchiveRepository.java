package com.einvoice.core.domain.ingestion.repository;

import com.einvoice.core.domain.ingestion.entity.InboundPayloadArchive;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Repository for inbound payload archive rows with patch-via-query support. */
@Repository
public interface InboundPayloadArchiveRepository extends JpaRepository<InboundPayloadArchive, UUID> {

    /**
     * Patches outcome, tenancy tuple, and (when the request succeeded) the
     * pointer to the persisted document on the archive row. Existing non-null
     * values are preserved via COALESCE so this method is idempotent.
     *
     * @param id the archive row UUID
     * @param httpStatus the HTTP status code
     * @param companyId the resolved company ID (nullable)
     * @param authorityEnvironmentId the resolved authority environment ID (nullable)
     * @param documentId the persisted header UUID (nullable — null for rejections)
     * @param documentType one of ETA_INVOICE / ETA_RECEIPT / ZATCA_STANDARD / ZATCA_SIMPLIFIED (nullable)
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE InboundPayloadArchive a SET a.outcome = :httpStatus,"
            + " a.companyId = COALESCE(a.companyId, :companyId),"
            + " a.authorityEnvironmentId = COALESCE(a.authorityEnvironmentId, :authorityEnvironmentId),"
            + " a.documentId = COALESCE(a.documentId, :documentId),"
            + " a.documentType = COALESCE(a.documentType, :documentType)"
            + " WHERE a.id = :id")
    void patchOutcome(@Param("id") UUID id,
            @Param("httpStatus") Short httpStatus,
            @Param("companyId") UUID companyId,
            @Param("authorityEnvironmentId") Short authorityEnvironmentId,
            @Param("documentId") UUID documentId,
            @Param("documentType") String documentType);
}
