package com.einvoice.core.repository;

import com.einvoice.core.domain.AuditLog;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/** Append-only repository for AuditLog. Only save and read operations are exposed. */
@Repository
public interface AuditLogRepository extends AppendOnlyRepository<AuditLog, Long> {

    Page<AuditLog> findAll(Pageable pageable);

    Page<AuditLog> findByCompanyId(Long companyId, Pageable pageable);

    Page<AuditLog> findByCompanyIdAndEntityType(
            Long companyId, String entityType, Pageable pageable);

    Page<AuditLog> findByCompanyIdAndEntityTypeAndEntityId(
            Long companyId, String entityType, String entityId, Pageable pageable);

    Page<AuditLog> findByCompanyIdAndTimestampBetween(
            Long companyId, OffsetDateTime from, OffsetDateTime to, Pageable pageable);

    Page<AuditLog> findByCompanyIdAndEntityTypeAndTimestampBetween(
            Long companyId, String entityType, OffsetDateTime from,
            OffsetDateTime to, Pageable pageable);

    Page<AuditLog> findByCompanyIdAndEntityTypeAndEntityIdAndTimestampBetween(
            Long companyId, String entityType, String entityId,
            OffsetDateTime from, OffsetDateTime to, Pageable pageable);
}
