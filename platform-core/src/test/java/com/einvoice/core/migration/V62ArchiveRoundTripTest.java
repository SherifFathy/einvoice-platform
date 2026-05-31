package com.einvoice.core.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.einvoice.core.CorePersistenceTestConfig;
import com.einvoice.core.domain.ingestion.entity.InboundPayloadArchive;
import com.einvoice.core.domain.ingestion.repository.InboundPayloadArchiveRepository;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = CorePersistenceTestConfig.class)
@Testcontainers
class V62ArchiveRoundTripTest {

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
        registry.add("spring.flyway.placeholders.BOOTSTRAP_SUPERUSER_EMAIL", () -> "");
        registry.add("spring.flyway.placeholders.BOOTSTRAP_SUPERUSER_PASSWORD_HASH", () -> "");
    }

    @Autowired
    private InboundPayloadArchiveRepository repository;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
    }

    @Test
    void validJsonBody_roundTripsCleanly() {
        String validJson = "{\"companyRegistrationNumber\":\"100200300\",\"environment\":\"PREPROD\"}";
        InboundPayloadArchive entity = InboundPayloadArchive.builder()
                .endpoint("/api/integration/v1/eta/receipts")
                .body(validJson)
                .build();

        InboundPayloadArchive saved = repository.save(entity);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getBody()).isEqualTo(validJson);
        assertThat(saved.getEndpoint()).isEqualTo("/api/integration/v1/eta/receipts");
        assertThat(saved.getReceivedAt()).isNotNull();
    }

    @Test
    void malformedJsonWrappedAsEnvelope_roundTripsCleanly() {
        String malformedRaw = "this is not json {{{";
        String envelope = "{\"_raw\":\""
                + Base64.getEncoder().encodeToString(malformedRaw.getBytes(StandardCharsets.UTF_8))
                + "\",\"_parseError\":\"...\"}";

        InboundPayloadArchive entity = InboundPayloadArchive.builder()
                .endpoint("/api/integration/v1/eta/receipts")
                .body(envelope)
                .build();

        InboundPayloadArchive saved = repository.save(entity);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getBody()).contains("_raw");
        assertThat(saved.getBody()).contains("_parseError");
    }

    @Test
    void patchOutcome_updatesAllThreeColumns() {
        InboundPayloadArchive entity = InboundPayloadArchive.builder()
                .endpoint("/api/integration/v1/eta/receipts")
                .body("{\"test\":true}")
                .build();
        repository.save(entity);

        UUID companyId = UUID.randomUUID();
        Short authEnvId = 2;

        repository.patchOutcome(entity.getId(), (short) 201, companyId, authEnvId, null, null);

        InboundPayloadArchive patched = repository.findById(entity.getId()).orElseThrow();
        assertThat(patched.getOutcome()).isEqualTo((short) 201);
        assertThat(patched.getCompanyId()).isEqualTo(companyId);
        assertThat(patched.getAuthorityEnvironmentId()).isEqualTo(authEnvId);
    }

    @Test
    void patchOutcome_doesNotOverwriteNonNullCompanyId() {
        UUID originalCompanyId = UUID.randomUUID();
        InboundPayloadArchive entity = InboundPayloadArchive.builder()
                .endpoint("/api/integration/v1/eta/receipts")
                .body("{\"test\":true}")
                .companyId(originalCompanyId)
                .build();
        repository.save(entity);

        repository.patchOutcome(entity.getId(), (short) 200, UUID.randomUUID(), (short) 5, null, null);

        InboundPayloadArchive patched = repository.findById(entity.getId()).orElseThrow();
        assertThat(patched.getCompanyId()).isEqualTo(originalCompanyId);
    }
}
