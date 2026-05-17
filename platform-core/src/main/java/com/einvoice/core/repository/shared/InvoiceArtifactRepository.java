package com.einvoice.core.repository.shared;

import com.einvoice.core.domain.shared.ArtifactType;
import com.einvoice.core.domain.shared.InvoiceArtifact;
import com.einvoice.core.domain.shared.TransactionType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Repository for invoice/receipt artifacts with tenant-scoped queries. */
public interface InvoiceArtifactRepository extends WriteOnlyRepository<InvoiceArtifact, UUID> {

    @Query("SELECT a FROM InvoiceArtifact a "
            + "WHERE a.documentId = :documentId "
            + "AND a.artifactType = :artifactType "
            + "AND a.companyId = :companyId "
            + "AND a.transactionType = :transactionType "
            + "ORDER BY a.createdAt DESC")
    List<InvoiceArtifact> findByDocumentIdAndTypeAndTenant(
            @Param("documentId") UUID documentId,
            @Param("artifactType") ArtifactType artifactType,
            @Param("companyId") UUID companyId,
            @Param("transactionType") TransactionType transactionType);

    @Query("SELECT a FROM InvoiceArtifact a "
            + "WHERE a.documentId = :documentId "
            + "AND a.artifactType = :artifactType "
            + "AND a.attemptNumber = :attemptNumber "
            + "AND a.companyId = :companyId "
            + "AND a.transactionType = :transactionType "
            + "ORDER BY a.createdAt DESC")
    List<InvoiceArtifact> findByDocumentIdAndTypeAndAttemptAndTenant(
            @Param("documentId") UUID documentId,
            @Param("artifactType") ArtifactType artifactType,
            @Param("attemptNumber") Integer attemptNumber,
            @Param("companyId") UUID companyId,
            @Param("transactionType") TransactionType transactionType);

    @Query("SELECT a FROM InvoiceArtifact a "
            + "WHERE a.documentId = :documentId "
            + "AND a.companyId = :companyId "
            + "AND a.transactionType = :transactionType "
            + "ORDER BY a.createdAt DESC")
    List<InvoiceArtifact> findByDocumentIdAndTenant(
            @Param("documentId") UUID documentId,
            @Param("companyId") UUID companyId,
            @Param("transactionType") TransactionType transactionType);
}
