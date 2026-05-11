package com.einvoice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.config.EtaConfigRepository;
import com.einvoice.core.repository.config.ZatcaConfigRepository;
import com.einvoice.core.repository.eta.EtaCustomerRepository;
import com.einvoice.core.repository.eta.EtaItemRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.core.repository.zatca.ZatcaChainStateRepository;
import com.einvoice.core.repository.zatca.ZatcaCustomerRepository;
import com.einvoice.core.repository.zatca.ZatcaItemRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class Wave6IsolationIT {

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
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private EtaCustomerRepository etaCustomerRepository;
    @Autowired private EtaItemRepository etaItemRepository;
    @Autowired private ZatcaCustomerRepository zatcaCustomerRepository;
    @Autowired private ZatcaItemRepository zatcaItemRepository;
    @Autowired private EtaConfigRepository etaConfigRepository;
    @Autowired private ZatcaConfigRepository zatcaConfigRepository;
    @Autowired private ZatcaChainStateRepository zatcaChainStateRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private UUID companyIdA;
    private UUID companyIdB;
    private UUID userId;
    private String etaPreprodTokenA;
    private String etaProdTokenA;
    private String zatcaSandboxTokenA;
    private String zatcaProdTokenA;
    private String etaPreprodTokenB;
    private String zatcaSandboxTokenB;

    @BeforeEach
    void setUp() {
        Company companyA = companyRepository.save(Company.builder()
                .nameEn("Wave6 Iso Co A").nameAr("شركة أ").taxNumber("W6ISOA").isActive(true).build());
        companyIdA = companyA.getId();

        Company companyB = companyRepository.save(Company.builder()
                .nameEn("Wave6 Iso Co B").nameAr("شركة ب").taxNumber("W6ISOB").isActive(true).build());
        companyIdB = companyB.getId();

        User user = User.builder()
                .name("Wave6 Iso User")
                .email("wave6-iso@isolation.com")
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        user = userRepository.save(user);
        userId = user.getId();

        etaPreprodTokenA = tokenFor("ETA", "PREPROD", (short) 2, companyIdA);
        etaProdTokenA = tokenFor("ETA", "PRODUCTION", (short) 1, companyIdA);
        zatcaSandboxTokenA = tokenFor("ZATCA", "SANDBOX", (short) 5, companyIdA);
        zatcaProdTokenA = tokenFor("ZATCA", "PRODUCTION", (short) 3, companyIdA);
        etaPreprodTokenB = tokenFor("ETA", "PREPROD", (short) 2, companyIdB);
        zatcaSandboxTokenB = tokenFor("ZATCA", "SANDBOX", (short) 5, companyIdB);
    }

    @AfterEach
    void tearDown() {
        zatcaChainStateRepository.deleteAll();
        zatcaConfigRepository.deleteAll();
        etaConfigRepository.deleteAll();
        zatcaItemRepository.deleteAll();
        etaItemRepository.deleteAll();
        zatcaCustomerRepository.deleteAll();
        etaCustomerRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    private String tokenFor(String authority, String environment, short envId, UUID companyId) {
        return jwtTokenProvider.createToken(
                userId, "wave6-iso@isolation.com", true,
                authority, environment, envId, companyId, TenantContext.Mode.OPERATIONAL_MODE);
    }

    private Map<String, Object> etaCustomerPayload(String name, String taxNumber) {
        return Map.of(
                "nameEn", name, "customerType", "B", "taxNumber", taxNumber,
                "addressData", Map.of(
                        "country", "EG", "governorate", "Cairo",
                        "regionCity", "Nasr City", "street", "10", "buildingNumber", "12"));
    }

    private Map<String, Object> zatcaCustomerPayload(String name, String vatNumber) {
        return Map.of(
                "nameEn", name, "customerType", "B", "vatNumber", vatNumber,
                "addressData", Map.of(
                        "streetName", "St", "buildingNumber", "1",
                        "city", "Riyadh", "postalCode", "11111",
                        "districtName", "Dist", "country", "SA"));
    }

    private Map<String, Object> etaItemPayload(String code, String name) {
        return Map.of(
                "internalCode", code, "itemType", "EGS",
                "itemCode", "EG-" + code, "nameEn", name,
                "unitPrice", 100.0, "taxRate", 14.0);
    }

    private Map<String, Object> zatcaItemPayload(String code, String name) {
        return Map.of(
                "internalCode", code, "nameEn", name,
                "vatCategory", "S", "vatRate", 15.0);
    }

    private Map<String, Object> etaConfigPayload(String suffix) {
        return Map.of(
                "clientId", "client-" + suffix,
                "clientSecret1", "s1-" + suffix,
                "clientSecret2", "s2-" + suffix,
                "tokenUrl", "https://token-" + suffix + ".url",
                "submissionUrl", "https://sub-" + suffix + ".url");
    }

    private Map<String, Object> zatcaConfigPayload(String suffix) {
        return Map.of(
                "privateKey", "-----BEGIN EC PRIVATE KEY-----\n" + suffix + "\n-----END EC PRIVATE KEY-----",
                "deviceUuid", UUID.randomUUID().toString(),
                "csr", "-----BEGIN CERTIFICATE REQUEST-----\n" + suffix + "\n-----END CERTIFICATE REQUEST-----",
                "complianceCertificate", "-----BEGIN CERTIFICATE-----\n" + suffix + "\n-----END CERTIFICATE-----",
                "complianceApiSecret", "secret-" + suffix);
    }

    @Test
    void allFourPartitions_isolatedAcrossEtaAndZatcaEnvironments() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyIdA)
                        .header("Authorization", "Bearer " + etaPreprodTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(etaCustomerPayload("ETA-PP-Cust", "TAX001"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyIdA)
                        .header("Authorization", "Bearer " + etaProdTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(etaCustomerPayload("ETA-PR-Cust", "TAX001"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyIdA)
                        .header("Authorization", "Bearer " + zatcaSandboxTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zatcaCustomerPayload("Z-SB-Cust", "300020001000003"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/zatca/customers", companyIdA)
                        .header("Authorization", "Bearer " + zatcaProdTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zatcaCustomerPayload("Z-PR-Cust", "300020002000003"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyIdA)
                        .header("Authorization", "Bearer " + etaPreprodTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("ETA-PP-Cust"));

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyIdA)
                        .header("Authorization", "Bearer " + etaProdTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("ETA-PR-Cust"));

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyIdA)
                        .header("Authorization", "Bearer " + zatcaSandboxTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Z-SB-Cust"));

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyIdA)
                        .header("Authorization", "Bearer " + zatcaProdTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("Z-PR-Cust"));
    }

    @Test
    void items_isolatedAcrossAllFourPartitions() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyIdA)
                        .header("Authorization", "Bearer " + etaPreprodTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(etaItemPayload("SKU-PP", "ETA-PP-Item"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyIdA)
                        .header("Authorization", "Bearer " + etaProdTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(etaItemPayload("SKU-PR", "ETA-PR-Item"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyIdA)
                        .header("Authorization", "Bearer " + zatcaSandboxTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zatcaItemPayload("SKU-ZSB", "Z-SB-Item"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/zatca/items", companyIdA)
                        .header("Authorization", "Bearer " + zatcaProdTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zatcaItemPayload("SKU-ZPR", "Z-PR-Item"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyIdA)
                        .header("Authorization", "Bearer " + etaPreprodTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(1));

        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyIdA)
                        .header("Authorization", "Bearer " + etaProdTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(1));

        mockMvc.perform(get("/api/companies/{companyId}/zatca/items", companyIdA)
                        .header("Authorization", "Bearer " + zatcaSandboxTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(1));

        mockMvc.perform(get("/api/companies/{companyId}/zatca/items", companyIdA)
                        .header("Authorization", "Bearer " + zatcaProdTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(1));
    }

    @Test
    void configs_isolatedAcrossEnvironmentsAndAuthorities() throws Exception {
        mockMvc.perform(put("/api/companies/{companyId}/eta/config", companyIdA)
                        .header("Authorization", "Bearer " + etaPreprodTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(etaConfigPayload("eta-pp"))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/companies/{companyId}/eta/config", companyIdA)
                        .header("Authorization", "Bearer " + etaProdTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(etaConfigPayload("eta-pr"))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/companies/{companyId}/zatca/config", companyIdA)
                        .header("Authorization", "Bearer " + zatcaSandboxTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(zatcaConfigPayload("zatca-sb"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/companies/{companyId}/eta/config", companyIdA)
                        .header("Authorization", "Bearer " + etaPreprodTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("client-eta-pp"));

        mockMvc.perform(get("/api/companies/{companyId}/eta/config", companyIdA)
                        .header("Authorization", "Bearer " + etaProdTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("client-eta-pr"));

        mockMvc.perform(get("/api/companies/{companyId}/zatca/config", companyIdA)
                        .header("Authorization", "Bearer " + zatcaSandboxTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complianceApiSecret").value("secret-zatca-sb"));

        mockMvc.perform(get("/api/companies/{companyId}/eta/config", companyIdA)
                        .header("Authorization", "Bearer " + zatcaSandboxTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").isEmpty());
    }

    @Test
    void data_isolatedAcrossCompanies() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyIdA)
                        .header("Authorization", "Bearer " + etaPreprodTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(etaCustomerPayload("CoA-Cust", "TAX-A"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/eta/items", companyIdA)
                        .header("Authorization", "Bearer " + etaPreprodTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(etaItemPayload("SKU-A", "CoA-Item"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyIdB)
                        .header("Authorization", "Bearer " + etaPreprodTokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(0));

        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyIdB)
                        .header("Authorization", "Bearer " + etaPreprodTokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(0));
    }

    @Test
    void detailGet_doesNotLeakAcrossPartitions() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyIdA)
                        .header("Authorization", "Bearer " + etaPreprodTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(etaCustomerPayload("Detail-Cust", "TAX-D"))))
                .andExpect(status().isCreated())
                .andReturn();

        Map<String, Object> created = objectMapper.readValue(
                result.getResponse().getContentAsString(), new TypeReference<Map<String, Object>>() {});
        String customerId = (String) created.get("id");

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers/{customerId}", companyIdA, customerId)
                        .header("Authorization", "Bearer " + etaProdTokenA))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers/{customerId}", companyIdA, customerId)
                        .header("Authorization", "Bearer " + zatcaSandboxTokenA))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers/{customerId}", companyIdB, customerId)
                        .header("Authorization", "Bearer " + etaPreprodTokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void search_doesNotLeakAcrossPartitions() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyIdA)
                            .header("Authorization", "Bearer " + etaPreprodTokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    etaCustomerPayload("SearchPP-" + i, "TAXPP" + i))))
                    .andExpect(status().isCreated());
        }

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyIdA)
                            .header("Authorization", "Bearer " + etaProdTokenA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    etaCustomerPayload("SearchPR-" + i, "TAXPR" + i))))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyIdA)
                        .param("q", "Search")
                        .header("Authorization", "Bearer " + etaPreprodTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(5))
                .andExpect(jsonPath("$.items.length()").value(5));

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyIdA)
                        .param("q", "Search")
                        .header("Authorization", "Bearer " + etaProdTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(5))
                .andExpect(jsonPath("$.items.length()").value(5));

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyIdA)
                        .param("q", "SearchPP")
                        .header("Authorization", "Bearer " + etaPreprodTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(5));

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyIdA)
                        .param("q", "SearchPR")
                        .header("Authorization", "Bearer " + etaPreprodTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(0));
    }

    @Test
    void companyFilter_doesNotLeakAcrossCompanies() throws Exception {
        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyIdA)
                        .header("Authorization", "Bearer " + etaPreprodTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(etaCustomerPayload("A-Cust", "TAX-A"))))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/companies/{companyId}/eta/customers", companyIdB)
                        .header("Authorization", "Bearer " + etaPreprodTokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(etaCustomerPayload("B-Cust", "TAX-B"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyIdA)
                        .param("companyId", companyIdA.toString())
                        .header("Authorization", "Bearer " + etaPreprodTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("A-Cust"));

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyIdB)
                        .param("companyId", companyIdB.toString())
                        .header("Authorization", "Bearer " + etaPreprodTokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(1))
                .andExpect(jsonPath("$.items[0].nameEn").value("B-Cust"));
    }

    @Test
    void randomizedFuzz_50Probes_noCrossContextLeakage() throws Exception {
        Random rng = new Random(42);
        int[] etaCustomerCounts = new int[2];
        int[] etaItemCounts = new int[2];
        int[] zatcaCustomerCounts = new int[2];
        int[] zatcaItemCounts = new int[2];

        record Partition(int companyIdx, String entityType, String authority, String environment,
                         short envId, UUID companyId) {}

        List<Partition> partitions = new ArrayList<>();
        partitions.add(new Partition(0, "customer", "ETA", "PREPROD", (short) 2, companyIdA));
        partitions.add(new Partition(0, "customer", "ETA", "PRODUCTION", (short) 1, companyIdA));
        partitions.add(new Partition(0, "customer", "ZATCA", "SANDBOX", (short) 5, companyIdA));
        partitions.add(new Partition(0, "customer", "ZATCA", "PRODUCTION", (short) 3, companyIdA));
        partitions.add(new Partition(0, "item", "ETA", "PREPROD", (short) 2, companyIdA));
        partitions.add(new Partition(0, "item", "ETA", "PRODUCTION", (short) 1, companyIdA));
        partitions.add(new Partition(0, "item", "ZATCA", "SANDBOX", (short) 5, companyIdA));
        partitions.add(new Partition(0, "item", "ZATCA", "PRODUCTION", (short) 3, companyIdA));

        for (int i = 0; i < 50; i++) {
            int partitionIndex = rng.nextInt(partitions.size());
            Partition p = partitions.get(partitionIndex);
            String token = tokenFor(p.authority, p.environment, p.envId, p.companyId);
            String name = "Fuzz-" + i;
            String url;

            if (p.authority.equals("ETA") && p.entityType.equals("customer")) {
                url = "/api/companies/{companyId}/eta/customers";
                mockMvc.perform(post(url, p.companyId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        etaCustomerPayload(name, "FUZZ" + String.format("%05d", i)))))
                        .andExpect(status().isCreated());
                int envArrIdx = p.envId == 2 ? 0 : 1;
                etaCustomerCounts[envArrIdx]++;
            } else if (p.authority.equals("ZATCA") && p.entityType.equals("customer")) {
                url = "/api/companies/{companyId}/zatca/customers";
                mockMvc.perform(post(url, p.companyId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        zatcaCustomerPayload(name, "3" + String.format("%013d", i) + "3"))))
                        .andExpect(status().isCreated());
                int envArrIdx = p.envId == 5 ? 0 : 1;
                zatcaCustomerCounts[envArrIdx]++;
            } else if (p.authority.equals("ETA") && p.entityType.equals("item")) {
                url = "/api/companies/{companyId}/eta/items";
                mockMvc.perform(post(url, p.companyId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        etaItemPayload("FUZZ-" + i, name))))
                        .andExpect(status().isCreated());
                int envArrIdx = p.envId == 2 ? 0 : 1;
                etaItemCounts[envArrIdx]++;
            } else {
                url = "/api/companies/{companyId}/zatca/items";
                mockMvc.perform(post(url, p.companyId)
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(
                                        zatcaItemPayload("FUZZ-" + i, name))))
                        .andExpect(status().isCreated());
                int envArrIdx = p.envId == 5 ? 0 : 1;
                zatcaItemCounts[envArrIdx]++;
            }
        }

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyIdA)
                        .header("Authorization", "Bearer " + etaPreprodTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(etaCustomerCounts[0]));

        mockMvc.perform(get("/api/companies/{companyId}/eta/customers", companyIdA)
                        .header("Authorization", "Bearer " + etaProdTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(etaCustomerCounts[1]));

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyIdA)
                        .header("Authorization", "Bearer " + zatcaSandboxTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(zatcaCustomerCounts[0]));

        mockMvc.perform(get("/api/companies/{companyId}/zatca/customers", companyIdA)
                        .header("Authorization", "Bearer " + zatcaProdTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(zatcaCustomerCounts[1]));

        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyIdA)
                        .header("Authorization", "Bearer " + etaPreprodTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(etaItemCounts[0]));

        mockMvc.perform(get("/api/companies/{companyId}/eta/items", companyIdA)
                        .header("Authorization", "Bearer " + etaProdTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(etaItemCounts[1]));

        mockMvc.perform(get("/api/companies/{companyId}/zatca/items", companyIdA)
                        .header("Authorization", "Bearer " + zatcaSandboxTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(zatcaItemCounts[0]));

        mockMvc.perform(get("/api/companies/{companyId}/zatca/items", companyIdA)
                        .header("Authorization", "Bearer " + zatcaProdTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.total").value(zatcaItemCounts[1]));

        assertThat(etaCustomerCounts[0] + etaCustomerCounts[1]
                + zatcaCustomerCounts[0] + zatcaCustomerCounts[1]
                + etaItemCounts[0] + etaItemCounts[1]
                + zatcaItemCounts[0] + zatcaItemCounts[1])
                .as("SC-001: all 50 fuzz probes must be accounted for across the 8 partitions")
                .isEqualTo(50);
    }
}
