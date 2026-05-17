package com.einvoice.core.repository.shared;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.einvoice.core.CorePersistenceTestConfig;
import com.einvoice.core.domain.shared.ArtifactType;
import com.einvoice.core.domain.shared.AuditLog;
import com.einvoice.core.domain.shared.InvoiceArtifact;
import com.einvoice.core.domain.shared.SubmissionAttempt;
import com.einvoice.core.domain.shared.SubmissionResult;
import com.einvoice.core.domain.shared.TransactionType;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = CorePersistenceTestConfig.class)
@Testcontainers
class AppendOnlyDbTriggerTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("einvoice_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.clean-disabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.main.web-application-type", () -> "none");
    }

    @Autowired
    private InvoiceArtifactRepository artifactRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SubmissionAttemptRepository attemptRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute(
                "ALTER TABLE invoice_artifacts DISABLE TRIGGER ALL");
        jdbcTemplate.execute(
                "ALTER TABLE audit_logs DISABLE TRIGGER ALL");
        jdbcTemplate.execute(
                "ALTER TABLE submission_attempts DISABLE TRIGGER ALL");
        jdbcTemplate.execute(
                "TRUNCATE TABLE invoice_artifacts, "
                        + "submission_attempts, audit_logs");
        jdbcTemplate.execute(
                "ALTER TABLE invoice_artifacts ENABLE TRIGGER ALL");
        jdbcTemplate.execute(
                "ALTER TABLE audit_logs ENABLE TRIGGER ALL");
        jdbcTemplate.execute(
                "ALTER TABLE submission_attempts ENABLE TRIGGER ALL");
    }

    @Test
    void invoiceArtifacts_update_rejectedByTrigger() {
        InvoiceArtifact saved = artifactRepository.save(buildArtifact());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE invoice_artifacts SET content = 'tampered' "
                        + "WHERE id = ?::uuid",
                saved.getId().toString()))
                .hasMessageContaining("append_only_table");
    }

    @Test
    void invoiceArtifacts_delete_rejectedByTrigger() {
        InvoiceArtifact saved = artifactRepository.save(buildArtifact());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM invoice_artifacts WHERE id = ?::uuid",
                saved.getId().toString()))
                .hasMessageContaining("append_only_table");
    }

    @Test
    void auditLogs_update_rejectedByTrigger() {
        AuditLog saved = auditLogRepository.save(buildAuditLog());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE audit_logs SET action = 'TAMPERED' "
                        + "WHERE id = ?",
                saved.getId()))
                .hasMessageContaining("append_only_table");
    }

    @Test
    void auditLogs_delete_rejectedByTrigger() {
        AuditLog saved = auditLogRepository.save(buildAuditLog());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM audit_logs WHERE id = ?",
                saved.getId()))
                .hasMessageContaining("append_only_table");
    }

    @Test
    void submissionAttempts_allowlistedUpdate_succeeds() {
        SubmissionAttempt saved = attemptRepository.save(buildAttempt());

        assertDoesNotThrow(() -> attemptRepository.finalizeAttempt(
                saved.getId(),
                SubmissionResult.SUCCESS, 200, null, null,
                OffsetDateTime.now()));
    }

    @Test
    void submissionAttempts_nonAllowlistedUpdate_rejectedByTrigger() {
        SubmissionAttempt saved = attemptRepository.save(buildAttempt());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE submission_attempts "
                        + "SET attempt_number = 999 "
                        + "WHERE id = ?::uuid",
                saved.getId().toString()))
                .hasMessageContaining("append_only_table");
    }

    @Test
    void submissionAttempts_mixedAllowlistedAndImmutableUpdate_rejectedByTrigger() {
        SubmissionAttempt saved = attemptRepository.save(buildAttempt());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE submission_attempts "
                        + "SET result = 'SUCCESS', attempt_number = 999, "
                        + "document_id = gen_random_uuid() "
                        + "WHERE id = ?::uuid",
                saved.getId().toString()))
                .hasMessageContaining("append_only_table");
    }

    @Test
    void submissionAttempts_delete_rejectedByTrigger() {
        SubmissionAttempt saved = attemptRepository.save(buildAttempt());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM submission_attempts WHERE id = ?::uuid",
                saved.getId().toString()))
                .hasMessageContaining("append_only_table");
    }

    private InvoiceArtifact buildArtifact() {
        return InvoiceArtifact.builder()
                .companyId(UUID.randomUUID())
                .authorityEnvironmentId((short) 2)
                .transactionType(TransactionType.INVOICE)
                .documentId(UUID.randomUUID())
                .artifactType(ArtifactType.SIGNED_JSON)
                .content("original-content")
                .contentHash("abc123")
                .build();
    }

    private AuditLog buildAuditLog() {
        return AuditLog.builder()
                .companyId(UUID.randomUUID())
                .authorityEnvironmentId((short) 2)
                .userId(UUID.randomUUID())
                .action("CREATE_INVOICE")
                .entityType("ETA_INVOICE")
                .entityId(UUID.randomUUID().toString())
                .build();
    }

    private SubmissionAttempt buildAttempt() {
        return SubmissionAttempt.builder()
                .companyId(UUID.randomUUID())
                .authorityEnvironmentId((short) 2)
                .transactionType(TransactionType.INVOICE)
                .documentId(UUID.randomUUID())
                .attemptNumber(1)
                .build();
    }
}
