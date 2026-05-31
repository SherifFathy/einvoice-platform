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

@Testcontainers
class V60RoundTripTest {

    // Non-static: a fresh container (and therefore a clean database) is
    // started per test method. Both tests migrate the same DB to V60, so a
    // shared static container would let whichever ran first leave the schema
    // at V60 and break the other's pre-V60 INSERT.
    @Container
    PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("einvoice_test")
                    .withUsername("test")
                    .withPassword("test");

    @Test
    void zatcaStandardLine_unitPriceBackfilledToItemNetPrice()
            throws Exception {
        DataSource ds = pgDataSource();

        Flyway flyway59 = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .target("59")
                .placeholders(java.util.Map.of(
                        "BOOTSTRAP_SUPERUSER_EMAIL", "",
                        "BOOTSTRAP_SUPERUSER_PASSWORD_HASH", ""))
                .load();
        flyway59.migrate();

        UUID headerId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        try (Connection c = ds.getConnection()) {
            c.createStatement().execute(
                    "SET session_replication_role = replica");
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO zatca_standard_headers ("
                            + "id, company_id, authority_environment_id, "
                            + "invoice_number, invoice_type_code, "
                            + "transaction_type_code, issue_date, issue_time, "
                            + "seller_data, buyer_data, currency, tax_currency, "
                            + "line_extension_amount, "
                            + "tax_exclusive_amount, tax_inclusive_amount, "
                            + "tax_amount, prepaid_amount, payable_amount, "
                            + "status, version) "
                            + "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?, "
                            + "?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, "
                            + "?, ?, ?)")) {
                ps.setString(1, headerId.toString());
                ps.setString(2, UUID.randomUUID().toString());
                ps.setShort(3, (short) 1);
                ps.setString(4, "INV-V60-001");
                ps.setString(5, "388");
                ps.setString(6, "0100000");
                ps.setObject(7, LocalDate.of(2026, 5, 19));
                ps.setObject(8, LocalTime.of(14, 30, 0));
                ps.setString(9, "{}");
                ps.setString(10, "{}");
                ps.setString(11, "SAR");
                ps.setString(12, "SAR");
                ps.setBigDecimal(13, new BigDecimal("25.50"));
                ps.setBigDecimal(14, new BigDecimal("25.50"));
                ps.setBigDecimal(15, new BigDecimal("29.33"));
                ps.setBigDecimal(16, new BigDecimal("3.83"));
                ps.setBigDecimal(17, BigDecimal.ZERO);
                ps.setBigDecimal(18, new BigDecimal("29.33"));
                ps.setString(19, "DRAFT");
                ps.setLong(20, 0L);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO zatca_standard_lines ("
                            + "id, header_id, line_number, "
                            + "item_code, description, unit_type, "
                            + "quantity, unit_price, "
                            + "line_extension_amount, discount_amount, "
                            + "allowance_amount, net_amount, "
                            + "vat_category_code, vat_rate, vat_amount) "
                            + "VALUES (?::uuid, ?::uuid, ?, "
                            + "?, ?, ?, ?, ?, "
                            + "?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, lineId.toString());
                ps.setString(2, headerId.toString());
                ps.setInt(3, 1);
                ps.setString(4, "ITM-001");
                ps.setString(5, "Test item");
                ps.setString(6, "PCE");
                ps.setBigDecimal(7, new BigDecimal("1.00000"));
                ps.setBigDecimal(8, new BigDecimal("25.50"));
                ps.setBigDecimal(9, new BigDecimal("25.50"));
                ps.setBigDecimal(10, BigDecimal.ZERO);
                ps.setBigDecimal(11, BigDecimal.ZERO);
                ps.setBigDecimal(12, new BigDecimal("25.50"));
                ps.setString(13, "S");
                ps.setBigDecimal(14, new BigDecimal("15.00"));
                ps.setBigDecimal(15, new BigDecimal("3.83"));
                ps.executeUpdate();
            }
            c.createStatement().execute(
                    "SET session_replication_role = DEFAULT");
        }

        Flyway flyway60 = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .target("60")
                .placeholders(java.util.Map.of(
                        "BOOTSTRAP_SUPERUSER_EMAIL", "",
                        "BOOTSTRAP_SUPERUSER_PASSWORD_HASH", ""))
                .load();
        flyway60.migrate();

        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT item_net_price, item_price_base_quantity "
                                + "FROM zatca_standard_lines "
                                + "WHERE id = ?::uuid")) {
            ps.setString(1, lineId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("line must exist after V60 migration")
                        .isTrue();
                assertThat(rs.getBigDecimal("item_net_price"))
                        .isEqualByComparingTo("25.50");
                assertThat(
                        rs.getBigDecimal("item_price_base_quantity"))
                        .isEqualByComparingTo("1");
            }
        }
    }

    @Test
    void etaReceiptLine_unitValueJsonbBackfilledToUnitPrice()
            throws Exception {
        DataSource ds = pgDataSource();

        Flyway flyway59 = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .target("59")
                .placeholders(java.util.Map.of(
                        "BOOTSTRAP_SUPERUSER_EMAIL", "",
                        "BOOTSTRAP_SUPERUSER_PASSWORD_HASH", ""))
                .load();
        flyway59.migrate();

        UUID headerId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        try (Connection c = ds.getConnection()) {
            c.createStatement().execute(
                    "SET session_replication_role = replica");
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO eta_receipt_headers ("
                            + "id, company_id, authority_environment_id, "
                            + "receipt_number, document_type, "
                            + "document_type_version, "
                            + "issue_datetime, seller_data, "
                            + "payment_method, currency, "
                            + "state, version) "
                            + "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, "
                            + "?, ?::jsonb, ?, ?, ?, ?)")) {
                ps.setString(1, headerId.toString());
                ps.setString(2, UUID.randomUUID().toString());
                ps.setShort(3, (short) 2);
                ps.setString(4, "REC-V60-001");
                ps.setString(5, "r");
                ps.setString(6, "1.2");
                ps.setObject(7, java.time.OffsetDateTime.parse(
                        "2026-05-13T14:30:00+02:00"));
                ps.setString(8, "{}");
                ps.setString(9, "CASH");
                ps.setString(10, "USD");
                ps.setString(11, "DRAFT");
                ps.setLong(12, 0L);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO eta_receipt_lines ("
                            + "id, header_id, line_number, "
                            + "item_type, item_code, description, unit_type, "
                            + "quantity, unit_value) "
                            + "VALUES (?::uuid, ?::uuid, ?, "
                            + "?, ?, ?, ?, "
                            + "?, ?::jsonb)")) {
                ps.setString(1, lineId.toString());
                ps.setString(2, headerId.toString());
                ps.setInt(3, 1);
                ps.setString(4, "EGS");
                ps.setString(5, "EGS-001");
                ps.setString(6, "Test item");
                ps.setString(7, "EA");
                ps.setBigDecimal(8, new BigDecimal("1.00000"));
                ps.setString(9,
                        "{\"currencySold\":\"USD\","
                                + "\"amountEGP\":\"50.00\","
                                + "\"amountSold\":\"3.00\","
                                + "\"currencyExchangeRate\":\"16.67\"}");
                ps.executeUpdate();
            }
            c.createStatement().execute(
                    "SET session_replication_role = DEFAULT");
        }

        Flyway flyway60 = Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .target("60")
                .placeholders(java.util.Map.of(
                        "BOOTSTRAP_SUPERUSER_EMAIL", "",
                        "BOOTSTRAP_SUPERUSER_PASSWORD_HASH", ""))
                .load();
        flyway60.migrate();

        try (Connection c = ds.getConnection();
                PreparedStatement ps = c.prepareStatement(
                        "SELECT unit_price FROM eta_receipt_lines "
                                + "WHERE id = ?::uuid")) {
            ps.setString(1, lineId.toString());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next())
                        .as("line must exist after V60 migration")
                        .isTrue();
                assertThat(rs.getBigDecimal("unit_price"))
                        .as("unit_price should be amountSold (3.00) "
                                + "for non-EGP currency, NOT amountEGP (50.00)")
                        .isEqualByComparingTo("3.00");
            }
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
