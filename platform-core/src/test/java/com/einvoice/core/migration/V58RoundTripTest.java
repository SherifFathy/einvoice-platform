package com.einvoice.core.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.einvoice.core.CorePersistenceTestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
class V58RoundTripTest {

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
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute(
                "SET session_replication_role = replica");
        jdbcTemplate.execute(
                "DELETE FROM zatca_standard_lines");
        jdbcTemplate.execute(
                "DELETE FROM zatca_simplified_lines");
        jdbcTemplate.execute(
                "DELETE FROM zatca_standard_headers");
        jdbcTemplate.execute(
                "DELETE FROM zatca_simplified_headers");
        jdbcTemplate.execute(
                "DELETE FROM eta_receipt_line_taxes");
        jdbcTemplate.execute(
                "DELETE FROM eta_receipt_lines");
        jdbcTemplate.execute(
                "DELETE FROM eta_receipt_headers");
        jdbcTemplate.execute(
                "SET session_replication_role = DEFAULT");
    }

    @Test
    void zatcaStandard_promotedColumns_persistAndRead() {
        Map<String, Object> sellerData = new LinkedHashMap<>();
        sellerData.put("partyName", "Seller Corp");
        sellerData.put("addressStreet", "Main St");
        sellerData.put("addressCityName", "Riyadh");
        sellerData.put("addressPostalZone", "11111");
        sellerData.put("addressCountryCode", "SA");

        Map<String, Object> buyerData = new LinkedHashMap<>();
        buyerData.put("partyName", "Buyer Corp");
        buyerData.put("addressStreet", "First St");
        buyerData.put("addressCityName", "Jeddah");

        final UUID id = UUID.randomUUID();
        final UUID companyId = UUID.randomUUID();
        jdbcTemplate.execute(
                "SET session_replication_role = replica");
        jdbcTemplate.update(
                "INSERT INTO zatca_standard_headers "
                        + "(id, company_id, authority_environment_id, "
                        + "invoice_number, invoice_type_code, "
                        + "transaction_type_code, issue_date, issue_time, "
                        + "seller_data, buyer_data, currency, tax_currency, "
                        + "line_extension_amount, tax_exclusive_amount, "
                        + "tax_inclusive_amount, tax_amount, "
                        + "allowance_total_amount, prepaid_amount, "
                        + "payable_amount, status, version, "
                        + "seller_vat_number, seller_postal_code, "
                        + "seller_country_code, "
                        + "buyer_vat_number, buyer_postal_code, "
                        + "buyer_country_code, "
                        + "business_process_code, "
                        + "tax_amount_accounting_currency, "
                        + "rounding_amount, payment_means_code, "
                        + "payment_means_text, issuance_reason, "
                        + "billing_reference_id, "
                        + "original_invoice_number, erp_reference_id) "
                        + "VALUES (CAST(? AS uuid), CAST(? AS uuid), "
                        + "?, ?, ?, ?, ?, ?, "
                        + "CAST(? AS jsonb), CAST(? AS jsonb), "
                        + "?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "?, ?, ?, ?, ?, ?, ?)",
                id.toString(), companyId.toString(),
                (short) 1, "INV-V58-001", "388", "0100000",
                LocalDate.of(2026, 5, 19),
                LocalTime.of(14, 30, 0),
                toJson(sellerData), toJson(buyerData),
                "SAR", "SAR",
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                new BigDecimal("115.00"),
                new BigDecimal("15.00"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("115.00"),
                "DRAFT", 0L,
                "300000000000003", "11111", "SA",
                "300000000100003", "22222", "SA",
                "reporting:1.0",
                new BigDecimal("15.00"),
                BigDecimal.ZERO, "30", "Cash",
                "Credit note for return",
                "INV-ORIG-001",
                "INV-ORIG-001",
                "ERP-REF-001");
        jdbcTemplate.execute(
                "SET session_replication_role = DEFAULT");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT seller_vat_number, seller_postal_code, "
                        + "seller_country_code, "
                        + "buyer_vat_number, buyer_postal_code, "
                        + "buyer_country_code, "
                        + "business_process_code, "
                        + "tax_amount_accounting_currency, "
                        + "rounding_amount, payment_means_code, "
                        + "payment_means_text, issuance_reason, "
                        + "billing_reference_id, "
                        + "original_invoice_number, erp_reference_id "
                        + "FROM zatca_standard_headers "
                        + "WHERE id = CAST(? AS uuid)",
                id.toString());

        assertThat(row.get("seller_vat_number"))
                .isEqualTo("300000000000003");
        assertThat(row.get("seller_postal_code")).isEqualTo("11111");
        assertThat(row.get("seller_country_code")).isEqualTo("SA");
        assertThat(row.get("buyer_vat_number"))
                .isEqualTo("300000000100003");
        assertThat(row.get("buyer_postal_code")).isEqualTo("22222");
        assertThat(row.get("buyer_country_code")).isEqualTo("SA");
        assertThat(row.get("business_process_code"))
                .isEqualTo("reporting:1.0");
        assertThat(row.get("tax_amount_accounting_currency"))
                .isEqualTo(new BigDecimal("15.00"));
        assertThat(row.get("rounding_amount"))
                .isEqualTo(new BigDecimal("0.00"));
        assertThat(row.get("payment_means_code")).isEqualTo("30");
        assertThat(row.get("payment_means_text")).isEqualTo("Cash");
        assertThat(row.get("issuance_reason"))
                .isEqualTo("Credit note for return");
        assertThat(row.get("billing_reference_id"))
                .isEqualTo("INV-ORIG-001");
        assertThat(row.get("original_invoice_number"))
                .isEqualTo("INV-ORIG-001");
        assertThat(row.get("erp_reference_id"))
                .isEqualTo("ERP-REF-001");
    }

    @Test
    void etaReceipt_commercialDiscount_renameRoundTrip() {
        Map<String, Object> sellerData = new LinkedHashMap<>();
        sellerData.put("branchNumber", "0");
        sellerData.put("companyName", "Test Co");
        sellerData.put("country", "EG");

        final UUID id = UUID.randomUUID();
        final UUID companyId = UUID.randomUUID();
        jdbcTemplate.execute(
                "SET session_replication_role = replica");
        jdbcTemplate.update(
                "INSERT INTO eta_receipt_headers "
                        + "(id, company_id, authority_environment_id, "
                        + "receipt_number, document_type, "
                        + "document_type_version, "
                        + "issue_datetime, seller_data, "
                        + "payment_method, currency, "
                        + "total_sales_amount, "
                        + "total_commercial_discount, "
                        + "extra_discount_amount, "
                        + "total_items_discount_amount, "
                        + "net_amount, total_amount, state, version) "
                        + "VALUES (CAST(? AS uuid), CAST(? AS uuid), "
                        + "?, ?, ?, ?, "
                        + "CAST(? AS timestamptz), CAST(? AS jsonb), "
                        + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id.toString(), companyId.toString(),
                (short) 2, "REC-V58-001", "r", "1.2",
                "2026-05-19T14:30:00+02:00",
                toJson(sellerData),
                "CASH", "EGP",
                new BigDecimal("100.00000"),
                new BigDecimal("5.00000"),
                BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("95.00000"),
                new BigDecimal("108.30000"),
                "DRAFT", 0L);
        jdbcTemplate.execute(
                "SET session_replication_role = DEFAULT");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT total_commercial_discount "
                        + "FROM eta_receipt_headers "
                        + "WHERE id = CAST(? AS uuid)",
                id.toString());

        assertThat(row.get("total_commercial_discount"))
                .isEqualTo(new BigDecimal("5.00000"));
    }

    @Test
    void etaReceipt_v12NewColumns_persistAndRead() {
        Map<String, Object> sellerData = new LinkedHashMap<>();
        sellerData.put("branchNumber", "0");
        sellerData.put("companyName", "Test Co");
        sellerData.put("country", "EG");

        List<Map<String, Object>> taxTotals = List.of(
                Map.of("taxType", "T1",
                        "amount", new BigDecimal("13.30000")));

        Map<String, Object> contractorData =
                Map.of("companyName", "Contractor Inc");

        final UUID id = UUID.randomUUID();
        final UUID companyId = UUID.randomUUID();
        jdbcTemplate.execute(
                "SET session_replication_role = replica");
        jdbcTemplate.update(
                "INSERT INTO eta_receipt_headers "
                        + "(id, company_id, authority_environment_id, "
                        + "receipt_number, document_type, "
                        + "document_type_version, "
                        + "issue_datetime, seller_data, "
                        + "payment_method, currency, "
                        + "total_sales_amount, "
                        + "total_commercial_discount, "
                        + "extra_discount_amount, "
                        + "total_items_discount_amount, "
                        + "net_amount, total_amount, state, version, "
                        + "exchange_rate, previous_uuid, "
                        + "reference_old_uuid, "
                        + "s_order_name_code, "
                        + "order_delivery_mode, "
                        + "gross_weight, net_weight, "
                        + "tax_totals, "
                        + "extra_receipt_discount_data, "
                        + "contractor_data, beneficiary_data, "
                        + "fees_amount, adjustment, "
                        + "erp_reference_id, "
                        + "original_invoice_number) "
                        + "VALUES (CAST(? AS uuid), CAST(? AS uuid), "
                        + "?, ?, ?, ?, "
                        + "CAST(? AS timestamptz), CAST(? AS jsonb), "
                        + "?, ?, ?, ?, ?, ?, "
                        + "?, ?, ?, ?, "
                        + "?, ?, ?, ?, ?, ?, ?, "
                        + "CAST(? AS jsonb), CAST(? AS jsonb), "
                        + "CAST(? AS jsonb), CAST(? AS jsonb), "
                        + "?, ?, ?, ?)",
                id.toString(), companyId.toString(),
                (short) 2, "REC-V58-002", "r", "1.2",
                "2026-05-19T14:30:00+02:00",
                toJson(sellerData),
                "CASH", "EGP",
                new BigDecimal("100.00000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("100.00000"),
                new BigDecimal("113.30000"),
                "DRAFT", 0L,
                new BigDecimal("1.00000"),
                "prev-uuid-123",
                "ref-old-uuid-456",
                "SOrderCode1",
                "DELIVERY",
                new BigDecimal("2.50000"),
                new BigDecimal("2.30000"),
                toJson(taxTotals),
                "[]",
                toJson(contractorData),
                null,
                new BigDecimal("0.50000"),
                BigDecimal.ZERO,
                "ERP-REC-001",
                "INV-ORIG-002");
        jdbcTemplate.execute(
                "SET session_replication_role = DEFAULT");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT exchange_rate, previous_uuid, "
                        + "reference_old_uuid, "
                        + "s_order_name_code, "
                        + "order_delivery_mode, "
                        + "gross_weight, net_weight, "
                        + "fees_amount, adjustment, "
                        + "erp_reference_id, "
                        + "original_invoice_number "
                        + "FROM eta_receipt_headers "
                        + "WHERE id = CAST(? AS uuid)",
                id.toString());

        assertThat(row.get("exchange_rate"))
                .isEqualTo(new BigDecimal("1.00000"));
        assertThat(row.get("previous_uuid"))
                .isEqualTo("prev-uuid-123");
        assertThat(row.get("reference_old_uuid"))
                .isEqualTo("ref-old-uuid-456");
        assertThat(row.get("s_order_name_code"))
                .isEqualTo("SOrderCode1");
        assertThat(row.get("order_delivery_mode"))
                .isEqualTo("DELIVERY");
        assertThat(row.get("gross_weight"))
                .isEqualTo(new BigDecimal("2.50000"));
        assertThat(row.get("net_weight"))
                .isEqualTo(new BigDecimal("2.30000"));
        assertThat(row.get("fees_amount"))
                .isEqualTo(new BigDecimal("0.50000"));
        assertThat(row.get("adjustment"))
                .isEqualTo(new BigDecimal("0.00000"));
        assertThat(row.get("erp_reference_id"))
                .isEqualTo("ERP-REC-001");
        assertThat(row.get("original_invoice_number"))
                .isEqualTo("INV-ORIG-002");
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
