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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
class ImmutabilityTriggerZatcaIT {

    private static final Logger log =
            LoggerFactory.getLogger(ImmutabilityTriggerZatcaIT.class);

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
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.main.web-application-type", () -> "none");
        registry.add("spring.flyway.placeholders"
                        + ".BOOTSTRAP_SUPERUSER_EMAIL",
                () -> "");
        registry.add("spring.flyway.placeholders"
                        + ".BOOTSTRAP_SUPERUSER_PASSWORD_HASH",
                () -> "");
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
        disableAppendOnlyTriggers();
        try {
            jdbcTemplate.execute(
                    "TRUNCATE TABLE invoice_artifacts, "
                            + "submission_attempts, audit_logs");
        } finally {
            enableAppendOnlyTriggers();
        }
    }

    private void disableAppendOnlyTriggers() {
        jdbcTemplate.execute(
                "ALTER TABLE invoice_artifacts DISABLE TRIGGER ALL");
        jdbcTemplate.execute(
                "ALTER TABLE audit_logs DISABLE TRIGGER ALL");
        jdbcTemplate.execute(
                "ALTER TABLE submission_attempts DISABLE TRIGGER ALL");
    }

    private void enableAppendOnlyTriggers() {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE invoice_artifacts ENABLE TRIGGER ALL");
        } catch (Exception e) {
            log.warn("Failed to re-enable invoice_artifacts triggers",
                    e);
        }
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE audit_logs ENABLE TRIGGER ALL");
        } catch (Exception e) {
            log.warn("Failed to re-enable audit_logs triggers", e);
        }
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE submission_attempts ENABLE TRIGGER ALL");
        } catch (Exception e) {
            log.warn("Failed to re-enable submission_attempts triggers",
                    e);
        }
    }

    @Test
    void standardArtifact_update_rejectedByTrigger() {
        InvoiceArtifact saved = artifactRepository.save(
                buildArtifact(TransactionType.STANDARD));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE invoice_artifacts SET content = 'tampered' "
                        + "WHERE id = ?::uuid",
                saved.getId().toString()))
                .hasMessageContaining("append_only_table");
    }

    @Test
    void standardArtifact_delete_rejectedByTrigger() {
        InvoiceArtifact saved = artifactRepository.save(
                buildArtifact(TransactionType.STANDARD));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM invoice_artifacts WHERE id = ?::uuid",
                saved.getId().toString()))
                .hasMessageContaining("append_only_table");
    }

    @Test
    void simplifiedArtifact_update_rejectedByTrigger() {
        InvoiceArtifact saved = artifactRepository.save(
                buildArtifact(TransactionType.SIMPLIFIED));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE invoice_artifacts SET content = 'tampered' "
                        + "WHERE id = ?::uuid",
                saved.getId().toString()))
                .hasMessageContaining("append_only_table");
    }

    @Test
    void simplifiedArtifact_delete_rejectedByTrigger() {
        InvoiceArtifact saved = artifactRepository.save(
                buildArtifact(TransactionType.SIMPLIFIED));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM invoice_artifacts WHERE id = ?::uuid",
                saved.getId().toString()))
                .hasMessageContaining("append_only_table");
    }

    @Test
    void zatcaStandardAuditLog_update_rejectedByTrigger() {
        AuditLog saved = auditLogRepository.save(
                buildAuditLog("ZATCA_STANDARD"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE audit_logs SET action = 'TAMPERED' "
                        + "WHERE id = ?",
                saved.getId()))
                .hasMessageContaining("append_only_table");
    }

    @Test
    void zatcaStandardAuditLog_delete_rejectedByTrigger() {
        AuditLog saved = auditLogRepository.save(
                buildAuditLog("ZATCA_STANDARD"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM audit_logs WHERE id = ?",
                saved.getId()))
                .hasMessageContaining("append_only_table");
    }

    @Test
    void zatcaSimplifiedAuditLog_update_rejectedByTrigger() {
        AuditLog saved = auditLogRepository.save(
                buildAuditLog("ZATCA_SIMPLIFIED"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE audit_logs SET action = 'TAMPERED' "
                        + "WHERE id = ?",
                saved.getId()))
                .hasMessageContaining("append_only_table");
    }

    @Test
    void zatcaSimplifiedAuditLog_delete_rejectedByTrigger() {
        AuditLog saved = auditLogRepository.save(
                buildAuditLog("ZATCA_SIMPLIFIED"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM audit_logs WHERE id = ?",
                saved.getId()))
                .hasMessageContaining("append_only_table");
    }

    @Test
    void standardSubmissionAttempt_allowlistedUpdate_succeeds() {
        SubmissionAttempt saved = attemptRepository.save(
                buildAttempt(TransactionType.STANDARD));

        assertDoesNotThrow(() -> attemptRepository.finalizeAttempt(
                saved.getId(),
                SubmissionResult.SUCCESS, 200, null, null,
                OffsetDateTime.now()));
    }

    @Test
    void standardSubmissionAttempt_nonAllowlistedUpdate_rejected() {
        SubmissionAttempt saved = attemptRepository.save(
                buildAttempt(TransactionType.STANDARD));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE submission_attempts "
                        + "SET attempt_number = 999 "
                        + "WHERE id = ?::uuid",
                saved.getId().toString()))
                .hasMessageContaining("append_only_table");
    }

    @Test
    void standardSubmissionAttempt_delete_rejectedByTrigger() {
        SubmissionAttempt saved = attemptRepository.save(
                buildAttempt(TransactionType.STANDARD));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM submission_attempts "
                        + "WHERE id = ?::uuid",
                saved.getId().toString()))
                .hasMessageContaining("append_only_table");
    }

    @Test
    void simplifiedSubmissionAttempt_allowlistedUpdate_succeeds() {
        SubmissionAttempt saved = attemptRepository.save(
                buildAttempt(TransactionType.SIMPLIFIED));

        assertDoesNotThrow(() -> attemptRepository.finalizeAttempt(
                saved.getId(),
                SubmissionResult.SUCCESS, 200, null, null,
                OffsetDateTime.now()));
    }

    @Test
    void simplifiedSubmissionAttempt_nonAllowlistedUpdate_rejected() {
        SubmissionAttempt saved = attemptRepository.save(
                buildAttempt(TransactionType.SIMPLIFIED));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE submission_attempts "
                        + "SET attempt_number = 999 "
                        + "WHERE id = ?::uuid",
                saved.getId().toString()))
                .hasMessageContaining("append_only_table");
    }

    @Test
    void simplifiedSubmissionAttempt_delete_rejectedByTrigger() {
        SubmissionAttempt saved = attemptRepository.save(
                buildAttempt(TransactionType.SIMPLIFIED));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM submission_attempts "
                        + "WHERE id = ?::uuid",
                saved.getId().toString()))
                .hasMessageContaining("append_only_table");
    }

    private InvoiceArtifact buildArtifact(TransactionType txType) {
        return InvoiceArtifact.builder()
                .companyId(UUID.randomUUID())
                .authorityEnvironmentId((short) 5)
                .transactionType(txType)
                .documentId(UUID.randomUUID())
                .artifactType(ArtifactType.SIGNED_UBL_XML)
                .content("original-zatca-content")
                .contentHash("zatca-hash-abc123")
                .build();
    }

    private AuditLog buildAuditLog(String entityType) {
        return AuditLog.builder()
                .companyId(UUID.randomUUID())
                .authorityEnvironmentId((short) 5)
                .userId(UUID.randomUUID())
                .action("SUBMIT_STANDARD")
                .entityType(entityType)
                .entityId(UUID.randomUUID().toString())
                .build();
    }

    private SubmissionAttempt buildAttempt(TransactionType txType) {
        return SubmissionAttempt.builder()
                .companyId(UUID.randomUUID())
                .authorityEnvironmentId((short) 5)
                .transactionType(txType)
                .documentId(UUID.randomUUID())
                .attemptNumber(1)
                .build();
    }
}
