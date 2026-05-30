package com.einvoice.core.domain.ingestion.repository;

import com.einvoice.core.domain.ingestion.entity.InboundPayloadArchive;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for inbound payload archive rows with patch-via-query support. */
@Repository
public interface InboundPayloadArchiveRepository extends JpaRepository<InboundPayloadArchive, UUID> {

    /**
     * Patches outcome, companyId, and authorityEnvironmentId on the archive row.
     * Uses JPQL @Modifying to keep the entity immutable beyond outcome.
     *
     * @param id the archive row UUID
     * @param httpStatus the HTTP status code
     * @param companyId the resolved company ID (nullable)
     * @param authorityEnvironmentId the resolved authority environment ID (nullable)
     */
    @Modifying
    @Query("UPDATE InboundPayloadArchive a SET a.outcome = :httpStatus,"
            + " a.companyId = COALESCE(a.companyId, :companyId),"
            + " a.authorityEnvironmentId = COALESCE(a.authorityEnvironmentId, :authorityEnvironmentId)"
            + " WHERE a.id = :id")
    void patchOutcome(@Param("id") UUID id,
            @Param("httpStatus") Short httpStatus,
            @Param("companyId") UUID companyId,
            @Param("authorityEnvironmentId") Short authorityEnvironmentId);
}
