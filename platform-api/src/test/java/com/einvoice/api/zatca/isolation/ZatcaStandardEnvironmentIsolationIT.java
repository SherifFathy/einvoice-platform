package com.einvoice.api.zatca.isolation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZatcaStandardEnvironmentIsolationIT extends ZatcaIsolationTestSupport {

    private String sandboxToken;
    private String prodToken;

    @BeforeEach
    void setUp() {
        Company company = createCompany("Std Iso Co", "ZSTD-ISO-001");
        companyId = company.getId();

        User user = createSuperUser("std-iso@zatca-isolation.com");
        userId = user.getId();

        grantRole(userId, companyId, ZATCA_SANDBOX_ENV,
                "STANDARD", "COMPANY_ADMIN");
        grantRole(userId, companyId, ZATCA_PRODUCTION_ENV,
                "STANDARD", "COMPANY_ADMIN");

        seedChainState(companyId, ZATCA_SANDBOX_ENV);
        seedChainState(companyId, ZATCA_PRODUCTION_ENV);

        sandboxToken = buildToken(userId, user.getEmail(), true,
                "ZATCA", "SANDBOX", ZATCA_SANDBOX_ENV, companyId);
        prodToken = buildToken(userId, user.getEmail(), true,
                "ZATCA", "PRODUCTION", ZATCA_PRODUCTION_ENV, companyId);
    }

    @Test
    void sandboxDocumentNotVisibleInProductionList() throws Exception {
        createStandardDoc(sandboxToken, "STD-ISO-SB-" + System.nanoTime())
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}"
                                        + "/zatca/standard",
                                companyId)
                                .header("Authorization",
                                        "Bearer " + prodToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void sandboxDocumentDirectGetReturns404inProduction()
            throws Exception {
        String createResponse = createStandardDoc(sandboxToken,
                "STD-ISO-DG-" + System.nanoTime())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String docId = objectMapper.readTree(createResponse)
                .get("id").asText();

        mockMvc.perform(get("/api/companies/{companyId}"
                                        + "/zatca/standard/{docId}",
                                companyId, docId)
                                .header("Authorization",
                                        "Bearer " + prodToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void chainStateRowsExistPerEnvironment() throws Exception {
        assertChainStateExists(companyId, ZATCA_SANDBOX_ENV, 0);
        assertChainStatePreviousHashIsNull(companyId, ZATCA_SANDBOX_ENV);

        assertChainStateExists(companyId, ZATCA_PRODUCTION_ENV, 0);
        assertChainStatePreviousHashIsNull(companyId, ZATCA_PRODUCTION_ENV);
    }

    @Test
    void sandboxDocumentNotVisibleInProductionWithNonSuperUser()
            throws Exception {
        User regular = createRegularUser(
                "std-iso-reg-" + UUID.randomUUID()
                        + "@zatca-isolation.com");
        UUID regularId = regular.getId();
        trackExtraUser(regularId);

        grantRole(regularId, companyId, ZATCA_SANDBOX_ENV,
                "STANDARD", "COMPANY_ADMIN");
        grantRole(regularId, companyId, ZATCA_PRODUCTION_ENV,
                "STANDARD", "COMPANY_ADMIN");

        String regSandboxToken = buildToken(regularId, regular.getEmail(),
                false, "ZATCA", "SANDBOX", ZATCA_SANDBOX_ENV, companyId);
        String regProdToken = buildToken(regularId, regular.getEmail(),
                false, "ZATCA", "PRODUCTION", ZATCA_PRODUCTION_ENV,
                companyId);

        createStandardDoc(regSandboxToken,
                "STD-ISO-REG-SB-" + System.nanoTime())
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}"
                                        + "/zatca/standard",
                                companyId)
                                .header("Authorization",
                                        "Bearer " + regProdToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
