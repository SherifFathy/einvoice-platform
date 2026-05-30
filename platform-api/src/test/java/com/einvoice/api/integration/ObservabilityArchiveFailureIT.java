package com.einvoice.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.api.integration.service.InboundPayloadArchiveService;
import com.einvoice.core.domain.company.Company;
import com.einvoice.core.error.InboundPayloadArchiveException;
import com.einvoice.core.repository.company.CompanyRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * FR-OBS-005 fault-injection slice: when the archive write fails, the gateway
 * must abort the request with HTTP 503 and leave NO operational rows behind.
 *
 * <p>Lives in its own class because @MockitoBean dirties the application context
 * and would corrupt the cross-endpoint scenarios in {@link ObservabilityIT}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ObservabilityArchiveFailureIT {

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

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private InboundPayloadArchiveService archiveService;

    private UUID companyId;

    @BeforeEach
    void setUp() {
        companyId = companyRepository.save(Company.builder()
                .nameEn("Archive Failure Co")
                .nameAr("Archive Failure Co AR")
                .taxNumber("100200300")
                .isActive(true)
                .build()).getId();

        when(archiveService.archive(anyString(), any(byte[].class)))
                .thenThrow(new InboundPayloadArchiveException(
                        "simulated archive outage", new RuntimeException("disk full")));
    }

    @AfterEach
    void tearDown() {
        companyRepository.deleteAll();
    }

    @Test
    void archiveFailure_returns503AndWritesNoOperationalRows() throws Exception {
        mockMvc.perform(post("/api/integration/v1/eta/receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(IngestionPayloads.etaReceipt("FR-OBS-005-REC", "100200300",
                                "PREPROD", "VALID")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ARCHIVE_WRITE_FAILED"));

        assertThat(rowCount("eta_receipt_headers")).as("no header row when archive fails")
                .isEqualTo(0);
        assertThat(rowCount("eta_receipt_lines")).isEqualTo(0);
        assertThat(rowCount("audit_logs")).isEqualTo(0);
        assertThat(rowCount("inbound_payload_archive")).isEqualTo(0);
    }

    private int rowCount(String table) {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return n == null ? 0 : n;
    }
}
