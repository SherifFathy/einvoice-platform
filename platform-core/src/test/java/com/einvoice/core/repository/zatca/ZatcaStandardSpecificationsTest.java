package com.einvoice.core.repository.zatca;

import static org.assertj.core.api.Assertions.assertThat;

import com.einvoice.core.domain.shared.DocumentState;
import com.einvoice.core.domain.zatca.ZatcaStandardHeader;
import com.einvoice.core.repository.support.ZatcaStandardSpecifications;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
class ZatcaStandardSpecificationsTest {

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
        registry.add("spring.flyway.placeholders.BOOTSTRAP_SUPERUSER_EMAIL", () -> "");
        registry.add("spring.flyway.placeholders.BOOTSTRAP_SUPERUSER_PASSWORD_HASH", () -> "");
    }

    @Autowired
    private ZatcaStandardHeaderRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private UUID companyId;
    private UUID otherCompanyId;
    private Short authEnvId = 5;
    private Short otherAuthEnvId = 3;

    @BeforeEach
    void setUp() {
        companyId = insertCompany("Test Co");
        otherCompanyId = insertCompany("Other Co");
    }

    @Test
    void filtersByCompanyAndEnvironment() {
        insertHeader(companyId, authEnvId, "STD-001",
                DocumentState.DRAFT);
        insertHeader(otherCompanyId, authEnvId, "STD-002",
                DocumentState.DRAFT);

        Specification<ZatcaStandardHeader> spec =
                ZatcaStandardSpecifications
                        .inActiveTenantAndAssignedCompany(
                                List.of(companyId), authEnvId);
        Page<ZatcaStandardHeader> page = repository.findAll(spec,
                PageRequest.of(0, 50));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getInvoiceNumber())
                .isEqualTo("STD-001");
    }

    @Test
    void filtersByEnvironment() {
        insertHeader(companyId, authEnvId, "STD-SB",
                DocumentState.DRAFT);
        insertHeader(companyId, otherAuthEnvId, "STD-PR",
                DocumentState.DRAFT);

        Specification<ZatcaStandardHeader> spec =
                ZatcaStandardSpecifications
                        .inActiveTenantAndAssignedCompany(
                                List.of(companyId), authEnvId);
        Page<ZatcaStandardHeader> page = repository.findAll(spec,
                PageRequest.of(0, 50));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getInvoiceNumber())
                .isEqualTo("STD-SB");
    }

    @Test
    void filtersByStatus() {
        insertHeader(companyId, authEnvId, "STD-D",
                DocumentState.DRAFT);
        insertHeader(companyId, authEnvId, "STD-A",
                DocumentState.ACCEPTED);

        Specification<ZatcaStandardHeader> spec =
                ZatcaStandardSpecifications
                        .inActiveTenantAndAssignedCompany(
                                List.of(companyId), authEnvId)
                        .and(ZatcaStandardSpecifications.inState(
                                DocumentState.DRAFT));
        Page<ZatcaStandardHeader> page = repository.findAll(spec,
                PageRequest.of(0, 50));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getInvoiceNumber())
                .isEqualTo("STD-D");
    }

    @Test
    void filtersByDateRange() {
        insertHeader(companyId, authEnvId, "STD-OLD",
                DocumentState.DRAFT,
                LocalDate.of(2026, 1, 15));
        insertHeader(companyId, authEnvId, "STD-NEW",
                DocumentState.DRAFT,
                LocalDate.of(2026, 5, 19));

        Specification<ZatcaStandardHeader> spec =
                ZatcaStandardSpecifications
                        .inActiveTenantAndAssignedCompany(
                                List.of(companyId), authEnvId)
                        .and(ZatcaStandardSpecifications.issuedBetween(
                                LocalDate.of(2026, 5, 1),
                                LocalDate.of(2026, 5, 31)));
        Page<ZatcaStandardHeader> page = repository.findAll(spec,
                PageRequest.of(0, 50));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getInvoiceNumber())
                .isEqualTo("STD-NEW");
    }

    @Test
    void returnsEmptyForUnassignedCompany() {
        insertHeader(companyId, authEnvId, "STD-001",
                DocumentState.DRAFT);

        Specification<ZatcaStandardHeader> spec =
                ZatcaStandardSpecifications
                        .inActiveTenantAndAssignedCompany(
                                List.of(otherCompanyId), authEnvId);
        Page<ZatcaStandardHeader> page = repository.findAll(spec,
                PageRequest.of(0, 50));

        assertThat(page.getTotalElements()).isEqualTo(0);
    }

    private UUID insertCompany(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO companies (id, name_en, name_ar, "
                        + "tax_number, is_active) "
                        + "VALUES (?, ?, ?, ?, true)",
                id, name, name, "TX-" + id.toString().substring(0, 8));
        return id;
    }

    private void insertHeader(UUID compId, Short envId,
            String invoiceNumber, DocumentState status) {
        insertHeader(compId, envId, invoiceNumber, status,
                LocalDate.of(2026, 5, 19));
    }

    private void insertHeader(UUID compId, Short envId,
            String invoiceNumber, DocumentState status,
            LocalDate issueDate) {
        jdbcTemplate.update(
                "INSERT INTO zatca_standard_headers "
                        + "(id, company_id, authority_environment_id, "
                        + "invoice_number, invoice_type_code, "
                        + "transaction_type_code, issue_date, "
                        + "issue_time, seller_data, buyer_data, "
                        + "currency, status, version, created_at, "
                        + "updated_at) "
                        + "VALUES (?, ?, ?, ?, '388', '0100000', "
                        + "?, ?, "
                        + "'{\"partyName\":\"Seller\"}'::jsonb, "
                        + "'{\"partyName\":\"Buyer\"}'::jsonb, "
                        + "'SAR', ?, 0, NOW(), NOW())",
                UUID.randomUUID(), compId, envId,
                invoiceNumber, issueDate,
                LocalTime.of(12, 0),
                status.name());
    }
}
