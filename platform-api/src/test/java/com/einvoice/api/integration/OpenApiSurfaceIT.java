package com.einvoice.api.integration;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OpenApiSurfaceIT {

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

    @Test
    void allFourEndpointPathsAndOperationIdsArePresent() throws Exception {
        mockMvc.perform(get("/v3/api-docs/integration-gateway"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths").isMap())
                .andExpect(jsonPath(
                        "$.paths['/api/integration/v1/eta/receipts']").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/integration/v1/eta/invoices']").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/integration/v1/zatca/standard']").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/integration/v1/zatca/simplified']").exists())
                .andExpect(jsonPath(
                        "$.paths['/api/integration/v1/eta/receipts'].post.operationId",
                        is("ingestEtaReceipt")))
                .andExpect(jsonPath(
                        "$.paths['/api/integration/v1/eta/invoices'].post.operationId",
                        is("ingestEtaInvoice")))
                .andExpect(jsonPath(
                        "$.paths['/api/integration/v1/zatca/standard'].post.operationId",
                        is("ingestZatcaStandard")))
                .andExpect(jsonPath(
                        "$.paths['/api/integration/v1/zatca/simplified'].post.operationId",
                        is("ingestZatcaSimplified")));
    }

    @Test
    void integrationEnvironmentEnumHasOnlySandboxAndPreprod() throws Exception {
        mockMvc.perform(get("/v3/api-docs/integration-gateway"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.IntegrationEnvironment.enum",
                        containsInAnyOrder("SANDBOX", "PREPROD")));
    }

    @Test
    void integrationDocumentStatusEnumHasAllEightValues() throws Exception {
        mockMvc.perform(get("/v3/api-docs/integration-gateway"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.components.schemas.IntegrationDocumentStatus.enum",
                        containsInAnyOrder(
                                "DRAFT", "VALID", "INVALID", "CLEARED",
                                "REPORTED", "REJECTED", "FAILED", "CANCELLED")));
    }

    @Test
    void swaggerUiReturns200() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
