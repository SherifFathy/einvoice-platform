package com.einvoice.api.zatca.isolation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZatcaCrossClassIsolationIT extends ZatcaIsolationTestSupport {

    private String token;

    @BeforeEach
    void setUp() {
        Company company = createCompany("Cross Class Iso Co",
                "ZCROSS-ISO-001");
        companyId = company.getId();

        User user = createSuperUser("cross-iso@zatca-isolation.com");
        userId = user.getId();

        grantRole(userId, companyId, ZATCA_SANDBOX_ENV,
                "STANDARD", "COMPANY_ADMIN");
        grantRole(userId, companyId, ZATCA_SANDBOX_ENV,
                "SIMPLIFIED", "COMPANY_ADMIN");

        seedChainState(companyId, ZATCA_SANDBOX_ENV);

        token = buildToken(userId, user.getEmail(), true,
                "ZATCA", "SANDBOX", ZATCA_SANDBOX_ENV, companyId);
    }

    @Test
    void standardDocumentNotVisibleInSimplifiedList() throws Exception {
        createStandardDoc(token, "CROSS-STD-" + System.nanoTime())
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}"
                                        + "/zatca/simplified",
                                companyId)
                                .header("Authorization",
                                        "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void simplifiedDocumentNotVisibleInStandardList() throws Exception {
        createSimplifiedDoc(token, "CROSS-SIMP-" + System.nanoTime())
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}"
                                        + "/zatca/standard",
                                companyId)
                                .header("Authorization",
                                        "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void standardDocumentDirectGetReturns404onSimplifiedEndpoint()
            throws Exception {
        String createResponse = createStandardDoc(token,
                "CROSS-STD-DG-" + System.nanoTime())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String docId = objectMapper.readTree(createResponse)
                .get("id").asText();

        mockMvc.perform(get("/api/companies/{companyId}"
                                        + "/zatca/simplified/{docId}",
                                companyId, docId)
                                .header("Authorization",
                                        "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void userWithOnlyStandardRoleRejectedFromSimplifiedEndpoint()
            throws Exception {
        User standardOnly = createRegularUser(
                "cross-std-only-" + UUID.randomUUID()
                        + "@zatca-isolation.com");
        UUID stdOnlyId = standardOnly.getId();
        trackExtraUser(stdOnlyId);

        grantRole(stdOnlyId, companyId, ZATCA_SANDBOX_ENV,
                "STANDARD", "COMPANY_ADMIN");

        String stdOnlyToken = buildToken(stdOnlyId,
                standardOnly.getEmail(), false,
                "ZATCA", "SANDBOX", ZATCA_SANDBOX_ENV, companyId);

        mockMvc.perform(get("/api/companies/{companyId}"
                                        + "/zatca/simplified",
                                companyId)
                                .header("Authorization",
                                        "Bearer " + stdOnlyToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void userWithOnlySimplifiedRoleRejectedFromStandardEndpoint()
            throws Exception {
        User simpOnly = createRegularUser(
                "cross-simp-only-" + UUID.randomUUID()
                        + "@zatca-isolation.com");
        UUID simpOnlyId = simpOnly.getId();
        trackExtraUser(simpOnlyId);

        grantRole(simpOnlyId, companyId, ZATCA_SANDBOX_ENV,
                "SIMPLIFIED", "COMPANY_ADMIN");

        String simpOnlyToken = buildToken(simpOnlyId,
                simpOnly.getEmail(), false,
                "ZATCA", "SANDBOX", ZATCA_SANDBOX_ENV, companyId);

        mockMvc.perform(get("/api/companies/{companyId}"
                                        + "/zatca/standard",
                                companyId)
                                .header("Authorization",
                                        "Bearer " + simpOnlyToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void sandboxStandardDocReturns404viaProdSimplifiedEndpoint()
            throws Exception {
        String createResponse = createStandardDoc(token,
                "CROSS-ENV-CLS-" + System.nanoTime())
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String docId = objectMapper.readTree(createResponse)
                .get("id").asText();

        grantRole(userId, companyId, ZATCA_PRODUCTION_ENV,
                "SIMPLIFIED", "COMPANY_ADMIN");
        seedChainState(companyId, ZATCA_PRODUCTION_ENV);

        String prodSimpToken = buildToken(userId,
                "cross-iso@zatca-isolation.com", true,
                "ZATCA", "PRODUCTION", ZATCA_PRODUCTION_ENV, companyId);

        mockMvc.perform(get("/api/companies/{companyId}"
                                        + "/zatca/simplified/{docId}",
                                companyId, docId)
                                .header("Authorization",
                                        "Bearer " + prodSimpToken))
                .andExpect(status().isNotFound());
    }
}
