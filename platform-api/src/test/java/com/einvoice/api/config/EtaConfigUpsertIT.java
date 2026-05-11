package com.einvoice.api.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.config.EtaConfigRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class EtaConfigUpsertIT {

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
    @Autowired private EtaConfigRepository etaConfigRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String token;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        Company company = companyRepository.save(Company.builder()
                .nameEn("Upsert Co").nameAr("شركة").taxNumber("UPS001").isActive(true).build());
        companyId = company.getId();

        User user = User.builder()
                .name("Upsert Admin")
                .email("admin@eta-config-upsert.com")
                .passwordHash(passwordEncoder.encode("Password1"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        user = userRepository.save(user);
        token = jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), true,
                "ETA", "PREPROD", (short) 2, companyId, TenantContext.Mode.OPERATIONAL_MODE);
    }

    @AfterEach
    void tearDown() {
        etaConfigRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void multiplePutsProduceOneRow() throws Exception {
        Map<String, Object> body = Map.of(
                "clientId", "client-1",
                "clientSecret1", "s1",
                "clientSecret2", "s2",
                "tokenUrl", "https://token.url",
                "submissionUrl", "https://sub.url");

        mockMvc.perform(put("/api/companies/{companyId}/eta/config", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk());

        Map<String, Object> body2 = Map.of(
                "clientId", "client-2",
                "clientSecret1", "s1b",
                "clientSecret2", "s2b",
                "tokenUrl", "https://token2.url",
                "submissionUrl", "https://sub2.url");

        mockMvc.perform(put("/api/companies/{companyId}/eta/config", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body2)))
                .andExpect(status().isOk());

        long count = etaConfigRepository.count();
        org.junit.jupiter.api.Assertions.assertEquals(1, count,
                "Only one config row should exist per (company, environment)");
    }

    @Test
    void concurrentPuts_eitherSucceedOr409() throws Exception {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Map<String, Object> body = Map.of(
                            "clientId", "concurrent-" + idx,
                            "clientSecret1", "s1",
                            "clientSecret2", "s2",
                            "tokenUrl", "https://token.url",
                            "submissionUrl", "https://sub.url");

                    String json = objectMapper.writeValueAsString(body);
                    var result = mockMvc.perform(put("/api/companies/{companyId}/eta/config", companyId)
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    if (status == 200) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        org.junit.jupiter.api.Assertions.assertTrue(successCount.get() >= 1,
                "At least one concurrent PUT should succeed");
        org.junit.jupiter.api.Assertions.assertEquals(0, errorCount.get(),
                "No unexpected errors should occur");

        long rowCount = etaConfigRepository.count();
        org.junit.jupiter.api.Assertions.assertEquals(1, rowCount,
                "Only one config row should exist after concurrent upserts");
    }

    @Test
    void readAfterMultiplePuts_returnsLatestValues() throws Exception {
        Map<String, Object> body1 = Map.of(
                "clientId", "initial-client",
                "clientSecret1", "s1",
                "clientSecret2", "s2",
                "tokenUrl", "https://token.url",
                "submissionUrl", "https://sub.url");

        mockMvc.perform(put("/api/companies/{companyId}/eta/config", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body1)))
                .andExpect(status().isOk());

        Map<String, Object> body2 = Map.of(
                "clientId", "updated-client",
                "clientSecret1", "s1-new",
                "clientSecret2", "s2-new",
                "tokenUrl", "https://new-token.url",
                "submissionUrl", "https://new-sub.url");

        mockMvc.perform(put("/api/companies/{companyId}/eta/config", companyId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body2)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/companies/{companyId}/eta/config", companyId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value("updated-client"))
                .andExpect(jsonPath("$.clientSecret1").value("s1-new"));
    }
}
