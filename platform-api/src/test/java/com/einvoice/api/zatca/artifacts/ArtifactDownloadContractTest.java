package com.einvoice.api.zatca.artifacts;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.authority.AuthorityResponse;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import com.einvoice.zatca.engine.ZatcaAuthorityEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ArtifactDownloadContractTest {

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
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private ZatcaAuthorityEngine engine;

    private String token;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder()
                .nameEn("Artifact Test Co").nameAr("اختبار")
                .taxNumber("AT001").isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name("Test Admin")
                .email("artifact-test@test.com")
                .passwordHash(passwordEncoder.encode("password"))
                .build();
        user = userRepository.save(user);

        jdbcTemplate.update(
                "INSERT INTO user_company_transaction_roles "
                        + "(user_id, company_id, authority_environment_id, "
                        + "transaction_type, role_code) "
                        + "VALUES (?, ?, 5, 'STANDARD', 'COMPANY_ADMIN')",
                user.getId(), companyId);

        jdbcTemplate.update(
                "INSERT INTO zatca_chain_state "
                        + "(company_id, authority_environment_id, "
                        + "invoice_counter, last_updated_at) "
                        + "VALUES (?, ?, 0, NOW())",
                companyId, (short) 5);

        jdbcTemplate.update(
                "INSERT INTO zatca_configs "
                        + "(id, company_id, authority_environment_id, "
                        + "private_key, device_uuid, csr, "
                        + "compliance_certificate, compliance_api_secret) "
                        + "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID().toString(), companyId.toString(), (short) 5,
                "test-private-key", "test-device-uuid",
                "test-csr", "test-compliance-cert", "test-compliance-secret");

        token = jwtTokenProvider.createToken(user.getId(),
                user.getEmail(), true,
                "ZATCA", "SANDBOX", (short) 5,
                companyId, TenantContext.Mode.OPERATIONAL_MODE);
    }

    @AfterEach
    void tearDown() {
        try {
            jdbcTemplate.update(
                    "DELETE FROM submission_attempts WHERE "
                            + "company_id = ?", companyId);
            jdbcTemplate.update(
                    "DELETE FROM invoice_artifacts WHERE "
                            + "company_id = ?", companyId);
            jdbcTemplate.update(
                    "DELETE FROM zatca_standard_lines WHERE "
                            + "header_id IN (SELECT id FROM "
                            + "zatca_standard_headers WHERE "
                            + "company_id = ?)", companyId);
            jdbcTemplate.update(
                    "DELETE FROM zatca_standard_headers WHERE "
                            + "company_id = ?", companyId);
            jdbcTemplate.update(
                    "DELETE FROM user_company_transaction_roles "
                            + "WHERE company_id = ?", companyId);
            jdbcTemplate.update(
                    "DELETE FROM zatca_chain_state WHERE "
                            + "company_id = ?", companyId);
            jdbcTemplate.update(
                    "DELETE FROM zatca_configs WHERE "
                            + "company_id = ?", companyId);
            jdbcTemplate.update(
                    "DELETE FROM users WHERE email = "
                            + "'artifact-test@test.com'");
            jdbcTemplate.update(
                    "DELETE FROM companies WHERE id = ?",
                    companyId);
        } catch (Exception ignored) {
        }
    }

    @Test
    void downloadEachArtifactTypeForStandard() throws Exception {
        var line = Map.of(
                "lineNumber", 1,
                "description", "Service",
                "quantity", "1.00000",
                "unitPrice", "1000.00000",
                "vatCategoryCode", "S",
                "vatRate", "15.00");

        String body = objectMapper.writeValueAsString(Map.of(
                "invoiceNumber", "STD-AR-" + System.nanoTime(),
                "invoiceTypeCode", "388",
                "transactionTypeCode", "0100000",
                "issueDate", "2026-05-19",
                "issueTime", "14:30:00",
                "currency", "SAR",
                "sellerData", Map.of(
                        "taxRegistrationNumber", "300000000000003",
                        "partyName", "Seller Co"),
                "buyerData", Map.of(
                        "taxRegistrationNumber", "300000000100003",
                        "partyName", "Buyer Co"),
                "lines", List.of(line)));

        String createResponse = mockMvc.perform(
                        post("/api/companies/{companyId}"
                                        + "/zatca/standard",
                                companyId)
                                .header("Authorization",
                                        "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String docId = objectMapper.readTree(createResponse)
                .get("id").asText();

        byte[] signedXml = "<SignedInvoice/>".getBytes();
        byte[] qrPng = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47};
        byte[] ublXml = "<Invoice/>".getBytes();

        when(engine.prepareStandardSubmission(any(), any(), any()))
                .thenReturn(new ZatcaAuthorityEngine
                        .ZatcaSubmissionResult(
                        ublXml, signedXml,
                        "abc123", "base64qr", qrPng));

        String zatcaResponseJson =
                "{\"status\":\"CLEARED\",\"uuid\":\""
                        + UUID.randomUUID() + "\"}";
        when(engine.submitClearance(any(), any(), any()))
                .thenReturn(new AuthorityResponse(
                        true, "CLEARED", null, null,
                        UUID.randomUUID().toString(),
                        200, null, zatcaResponseJson));

        mockMvc.perform(
                        post("/api/companies/{companyId}"
                                        + "/zatca/standard"
                                        + "/{docId}/submit",
                                companyId, docId)
                                .header("Authorization",
                                        "Bearer " + token))
                .andExpect(status().isOk());

        byte[] expectedSigned = Base64.getEncoder()
                .encode(signedXml);
        byte[] expectedUbl = Base64.getEncoder().encode(ublXml);
        byte[] expectedQr = Base64.getEncoder().encode(qrPng);

        byte[] signedResp = mockMvc.perform(
                        get("/api/companies/{companyId}"
                                        + "/zatca/standard"
                                        + "/{docId}"
                                        + "/artifacts/SIGNED_UBL_XML",
                                companyId, docId)
                                .header("Authorization",
                                        "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Artifact-Hash",
                        org.hamcrest.Matchers.notNullValue()))
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(
                new String(expectedSigned).getBytes(),
                signedResp);

        byte[] ublResp = mockMvc.perform(
                        get("/api/companies/{companyId}"
                                        + "/zatca/standard"
                                        + "/{docId}"
                                        + "/artifacts/UBL_XML",
                                companyId, docId)
                                .header("Authorization",
                                        "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(
                new String(expectedUbl).getBytes(), ublResp);

        byte[] qrResp = mockMvc.perform(
                        get("/api/companies/{companyId}"
                                        + "/zatca/standard"
                                        + "/{docId}"
                                        + "/artifacts/QR_PNG",
                                companyId, docId)
                                .header("Authorization",
                                        "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(
                new String(expectedQr).getBytes(), qrResp);

        byte[] zatcaRespBody = mockMvc.perform(
                        get("/api/companies/{companyId}"
                                        + "/zatca/standard"
                                        + "/{docId}"
                                        + "/artifacts/ZATCA_RESPONSE",
                                companyId, docId)
                                .header("Authorization",
                                        "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals(zatcaResponseJson,
                new String(zatcaRespBody));

        mockMvc.perform(
                        get("/api/companies/{companyId}"
                                        + "/zatca/standard"
                                        + "/{docId}"
                                        + "/artifacts/CLEARED_XML",
                                companyId, docId)
                                .header("Authorization",
                                        "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
