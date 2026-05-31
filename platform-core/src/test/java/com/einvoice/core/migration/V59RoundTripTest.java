package com.einvoice.core.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
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

/**
 * Exercises the V59 backfill path explicitly: migrate to V58, insert a row
 * whose {@code allowance_total_amount} is non-zero, migrate to V59, then
 * assert the synthesised {@code zatca_standard_allowances} row and the drop
 * of {@code allowance_total_amount}. This is the only place that proves the
 * backfill INSERT runs <em>before</em> the DROP COLUMN (FR-003 ordering).
 */
@Testcontainers
class V59RoundTripTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("einvoice_test")
                    .withUsername("test")
                    .withPassword("test");

    @Test
    void aggregateAllowance_backfilledIntoChildTable() throws Exception {
        DataSource ds = pgDataSource();

        // Step 1: migrate up to V58 (so allowance_total_amount column exists).
        Flyway flyway58 = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .target("58")
                .placeholders(java.util.Map.of(
                        "BOOTSTRAP_SUPERUSER_EMAIL", "",
                        "BOOTSTRAP_SUPERUSER_PASSWORD_HASH", ""))
                .load();
        flyway58.migrate();

        // Step 2: insert a header with non-zero allowance_total_amount.
        UUID headerId = UUID.randomUUID();
        try (Connection c = ds.getConnection()) {
            c.createStatement().execute(
                    "SET session_replication_role = replica");
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO zatca_standard_headers ("
                            + "id, company_id, authority_environment_id, "
                            + "invoice_number, invoice_type_code, "
                            + "transaction_type_code, issue_date, issue_time, "
                            + "seller_data, buyer_data, currency, tax_currency, "
                            + "line_extension_amount, allowance_total_amount, "
                            + "tax_exclusive_amount, tax_inclusive_amount, "
                            + "tax_amount, prepaid_amount, payable_amount, "
                            + "status, version) "
                            + "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, "
                            + "?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?, "
                            + "?, ?, ?)")) {
                ps.setString(1, headerId.toString());
                ps.setString(2, UUID.randomUUID().toString());
                ps.setShort(3, (short) 1);
                ps.setString(4, "INV-V59-001");
                ps.setString(5, "388");
                ps.setString(6, "0100000");
                ps.setObject(7, LocalDate.of(2026, 5, 19));
                ps.setObject(8, LocalTime.of(14, 30, 0));
                ps.setString(9, "{}");
                ps.setString(10, "{}");
                ps.setString(11, "SAR");
                ps.setString(12, "SAR");
                ps.setBigDecimal(13, new BigDecimal("1000.00"));
                ps.setBigDecimal(14, new BigDecimal("100.00"));
                ps.setBigDecimal(15, new BigDecimal("900.00"));
                ps.setBigDecimal(16, new BigDecimal("1035.00"));
                ps.setBigDecimal(17, new BigDecimal("135.00"));
                ps.setBigDecimal(18, BigDecimal.ZERO);
                ps.setBigDecimal(19, new BigDecimal("1035.00"));
                ps.setString(20, "DRAFT");
                ps.setLong(21, 0L);
                ps.executeUpdate();
            }
            c.createStatement().execute(
                    "SET session_replication_role = DEFAULT");
        }

        // Step 3: migrate to V59 (runs the backfill + DROP).
        Flyway flyway59 = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .target("59")
                .placeholders(java.util.Map.of(
                        "BOOTSTRAP_SUPERUSER_EMAIL", "",
                        "BOOTSTRAP_SUPERUSER_PASSWORD_HASH", ""))
                .load();
        flyway59.migrate();

        // Step 4a: one synthetic allowance row exists for this header.
        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT amount, reason, sequence, vat_category_code,"
                                + " vat_rate FROM zatca_standard_allowances"
                                + " WHERE header_id = ?::uuid")) {
            ps.setString(1, headerId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("backfill must insert one row for the header")
                        .isTrue();
                assertThat(rs.getBigDecimal("amount"))
                        .isEqualByComparingTo("100.00");
                assertThat(rs.getString("reason"))
                        .isEqualTo("migrated-from-aggregate");
                assertThat(rs.getShort("sequence")).isEqualTo((short) 1);
                assertThat(rs.getString("vat_category_code"))
                        .isEqualTo("S");
                assertThat(rs.getBigDecimal("vat_rate"))
                        .isEqualByComparingTo("15.00");
                assertThat(rs.next())
                        .as("at most one allowance per header from backfill")
                        .isFalse();
            }
        }

        // Step 4b: allowance_total_amount column is gone.
        try (Connection c = ds.getConnection();
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(
                        "SELECT column_name FROM information_schema.columns"
                                + " WHERE table_name ="
                                + " 'zatca_standard_headers'"
                                + " AND column_name ="
                                + " 'allowance_total_amount'")) {
            assertThat(rs.next())
                    .as("V59 must DROP allowance_total_amount")
                    .isFalse();
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
