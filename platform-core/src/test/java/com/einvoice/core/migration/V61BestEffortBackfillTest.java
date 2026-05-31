package com.einvoice.core.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class V61BestEffortBackfillTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("einvoice_test")
                    .withUsername("test")
                    .withPassword("test");

    @Test
    void v61_backfill_signatureAndConfig() throws Exception {
        DataSource ds = pgDataSource();

        Flyway flyway60 = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .target("60")
                .placeholders(java.util.Map.of(
                        "BOOTSTRAP_SUPERUSER_EMAIL", "",
                        "BOOTSTRAP_SUPERUSER_PASSWORD_HASH", ""))
                .load();
        flyway60.migrate();

        UUID rowAId = UUID.randomUUID();
        UUID companyA = UUID.randomUUID();
        UUID rowBId = UUID.randomUUID();
        UUID companyB = UUID.randomUUID();
        UUID rowCId = UUID.randomUUID();
        UUID companyC = UUID.randomUUID();
        UUID historicConfigId = UUID.randomUUID();

        try (Connection c = ds.getConnection()) {
            c.createStatement().execute(
                    "SET session_replication_role = replica");

            insertStubCompany(c, companyA, "Company A");
            insertStubCompany(c, companyB, "Company B");
            insertStubCompany(c, companyC, "Company C");

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO zatca_configs ("
                            + "id, company_id, authority_environment_id, "
                            + "private_key, device_uuid, csr, "
                            + "compliance_certificate, compliance_api_secret, "
                            + "is_active, created_at, updated_at) "
                            + "VALUES (?::uuid, ?::uuid, ?, "
                            + "?, ?, ?, "
                            + "?, ?, "
                            + "FALSE, NOW(), NOW())")) {
                ps.setString(1, historicConfigId.toString());
                ps.setString(2, companyC.toString());
                ps.setShort(3, (short) 1);
                ps.setString(4, "dummy-private-key");
                ps.setString(5, "dummy-device-uuid");
                ps.setString(6, "dummy-csr");
                ps.setString(7, "dummy-compliance-cert");
                ps.setString(8, "dummy-compliance-secret");
                ps.executeUpdate();
            }

            insertStandardHeader(c, rowAId, companyA, (short) 1,
                    "INV-V61-A",
                    "{\"signatureValue\": \"MEUCIQ...\","
                            + " \"signedAt\": \"2025-12-01T10:00:00Z\"}");
            insertStandardHeader(c, rowBId, companyB, (short) 1,
                    "INV-V61-B", null);
            insertStandardHeader(c, rowCId, companyC, (short) 1,
                    "INV-V61-C", null);

            c.createStatement().execute(
                    "SET session_replication_role = DEFAULT");
        }

        Flyway flyway61 = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .target("61")
                .placeholders(java.util.Map.of(
                        "BOOTSTRAP_SUPERUSER_EMAIL", "",
                        "BOOTSTRAP_SUPERUSER_PASSWORD_HASH", ""))
                .load();
        flyway61.migrate();

        try (Connection c = ds.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT cryptographic_stamp_value, signed_at "
                            + "FROM zatca_standard_headers "
                            + "WHERE id = ?::uuid")) {
                ps.setString(1, rowAId.toString());
                ResultSet rs = ps.executeQuery();
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("cryptographic_stamp_value"))
                        .isEqualTo("MEUCIQ...");
                assertThat(rs.getTimestamp("signed_at")).isNotNull();
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT zatca_config_id "
                            + "FROM zatca_standard_headers "
                            + "WHERE id = ?::uuid")) {
                ps.setString(1, rowBId.toString());
                ResultSet rs = ps.executeQuery();
                assertThat(rs.next()).isTrue();
                assertThat(rs.getObject("zatca_config_id")).isNull();
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT zatca_config_id "
                            + "FROM zatca_standard_headers "
                            + "WHERE id = ?::uuid")) {
                ps.setString(1, rowCId.toString());
                ResultSet rs = ps.executeQuery();
                assertThat(rs.next()).isTrue();
                assertThat(rs.getObject("zatca_config_id",
                        UUID.class)).isEqualTo(historicConfigId);
            }
        }
    }

    private static void insertStubCompany(Connection c, UUID companyId,
            String name) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO companies (id, name_ar, name_en, tax_number) "
                        + "VALUES (?::uuid, ?, ?, ?)")) {
            ps.setString(1, companyId.toString());
            ps.setString(2, name);
            ps.setString(3, name);
            ps.setString(4, "TAX-" + companyId.toString().substring(0, 8));
            ps.executeUpdate();
        }
    }

    private static void insertStandardHeader(Connection c,
            UUID id, UUID companyId, short envId,
            String invoiceNumber, String responseData) throws Exception {
        String responseCol = responseData != null
                ? "zatca_response_data, " : "";
        String responseVal = responseData != null
                ? "?::jsonb, " : "";
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO zatca_standard_headers ("
                        + "id, company_id, authority_environment_id, "
                        + "invoice_number, invoice_type_code, "
                        + "transaction_type_code, issue_date, issue_time, "
                        + "seller_data, buyer_data, currency, tax_currency, "
                        + "line_extension_amount, "
                        + "tax_exclusive_amount, tax_inclusive_amount, "
                        + "tax_amount, prepaid_amount, payable_amount, "
                        + "status, version"
                        + (responseData != null
                                ? ", zatca_response_data" : "")
                        + ") VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, "
                        + "?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, ?"
                        + (responseData != null ? ", ?::jsonb" : "")
                        + ")")) {
            int i = 1;
            ps.setString(i++, id.toString());
            ps.setString(i++, companyId.toString());
            ps.setShort(i++, envId);
            ps.setString(i++, invoiceNumber);
            ps.setString(i++, "388");
            ps.setString(i++, "0100000");
            ps.setObject(i++, LocalDate.of(2026, 5, 19));
            ps.setObject(i++, LocalTime.of(14, 30, 0));
            ps.setString(i++, "{}");
            ps.setString(i++, "{}");
            ps.setString(i++, "SAR");
            ps.setString(i++, "SAR");
            ps.setBigDecimal(i++, new BigDecimal("1000.00"));
            ps.setBigDecimal(i++, new BigDecimal("900.00"));
            ps.setBigDecimal(i++, new BigDecimal("1035.00"));
            ps.setBigDecimal(i++, new BigDecimal("135.00"));
            ps.setBigDecimal(i++, BigDecimal.ZERO);
            ps.setBigDecimal(i++, new BigDecimal("1035.00"));
            ps.setString(i++, "DRAFT");
            ps.setLong(i++, 0L);
            if (responseData != null) {
                ps.setString(i++, responseData);
            }
            ps.executeUpdate();
        }
    }

    private DataSource pgDataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        return ds;
    }
}
