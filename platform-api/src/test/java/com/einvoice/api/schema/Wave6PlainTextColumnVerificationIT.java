package com.einvoice.api.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * T007 verification: asserts that every secret/key/certificate column on
 * eta_configs and zatca_configs is stored as TEXT (not VARCHAR(N), not BYTEA,
 * not an encrypted-column wrapper). This locks the Phase-1 plain-text storage
 * shape per Constitution XVIII.3 so the Phase-2 AES-256-GCM upgrade can be a
 * storage-layer change with no schema migration.
 */
@SpringBootTest
@Testcontainers
class Wave6PlainTextColumnVerificationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
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
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void etaConfigs_secretColumns_areTextDataType() {
        Set<String> secretColumns = Set.of(
                "client_id", "client_secret_1", "client_secret_2",
                "token_name", "token_pass", "submission_url", "token_url",
                "pos_serial", "pos_os_version", "pos_model");

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT column_name, data_type FROM information_schema.columns "
                        + "WHERE table_name = 'eta_configs'");

        for (Map<String, Object> row : rows) {
            String colName = (String) row.get("column_name");
            if (secretColumns.contains(colName)) {
                String dataType = (String) row.get("data_type");
                assertThat(dataType)
                        .as("eta_configs.%s must be TEXT (got %s)", colName, dataType)
                        .isEqualTo("text");
            }
        }
    }

    @Test
    void zatcaConfigs_secretColumns_areTextDataType() {
        Set<String> secretColumns = Set.of(
                "private_key", "device_uuid", "csr",
                "compliance_certificate", "compliance_api_secret",
                "production_certificate", "production_api_secret");

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT column_name, data_type FROM information_schema.columns "
                        + "WHERE table_name = 'zatca_configs'");

        for (Map<String, Object> row : rows) {
            String colName = (String) row.get("column_name");
            if (secretColumns.contains(colName)) {
                String dataType = (String) row.get("data_type");
                assertThat(dataType)
                        .as("zatca_configs.%s must be TEXT (got %s)", colName, dataType)
                        .isEqualTo("text");
            }
        }
    }

    @Test
    void sevenUniqueConstraints_exist() {
        Set<String> expected = Set.of(
                "uq_eta_config", "uq_eta_customer_tax", "uq_eta_item_code",
                "uq_zatca_config", "uq_zatca_chain", "uq_zatca_customer_vat",
                "uq_zatca_item_code");

        List<String> actual = jdbc.queryForList(
                "SELECT conname FROM pg_constraint "
                        + "WHERE conname LIKE 'uq_%' "
                        + "AND conrelid::regclass::text IN ("
                        + "  'eta_configs','eta_customers','eta_items',"
                        + "  'zatca_configs','zatca_chain_state',"
                        + "  'zatca_customers','zatca_items')",
                String.class);

        assertThat(actual).containsAll(expected);
    }

    @Test
    void sevenCompoundIndexes_exist() {
        Set<String> expected = Set.of(
                "idx_eta_customers_ctx", "idx_eta_items_ctx",
                "idx_zatca_customers_ctx", "idx_zatca_items_ctx",
                "idx_eta_configs_ctx", "idx_zatca_configs_ctx",
                "idx_zatca_chain_ctx");

        List<String> actual = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes "
                        + "WHERE indexname LIKE 'idx_%_ctx' "
                        + "AND tablename IN ("
                        + "  'eta_configs','eta_customers','eta_items',"
                        + "  'zatca_configs','zatca_chain_state',"
                        + "  'zatca_customers','zatca_items')",
                String.class);

        assertThat(actual).containsAll(expected);
    }
}
