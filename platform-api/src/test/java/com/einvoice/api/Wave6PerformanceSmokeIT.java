package com.einvoice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.eta.EtaCustomer;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.core.repository.eta.EtaCustomerRepository;
import com.einvoice.core.repository.user.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.einvoice.security.tenant.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Wave6PerformanceSmokeIT {

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
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private EtaCustomerRepository etaCustomerRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private static final int COMPANY_COUNT = 10;
    private static final int CUSTOMERS_PER_COMPANY = 5000;
    private static final int WARMUP_RUNS = 3;
    private static final int MEASURED_RUNS = 20;
    private static final long FIRST_PAGE_P95_MS = 2000;
    private static final long FILTER_P95_MS = 500;

    private List<UUID> companyIds;
    private String superUserToken;

    @BeforeAll
    void setUp() {
        User user = User.builder()
                .name("Perf User")
                .email("perf@wave6-perf.com")
                .passwordHash(passwordEncoder.encode("P"))
                .isSuperUser(true)
                .isActive(true)
                .build();
        user = userRepository.save(user);

        companyIds = new ArrayList<>();
        for (int i = 0; i < COMPANY_COUNT; i++) {
            Company company = companyRepository.save(Company.builder()
                    .nameEn("Perf Co " + i)
                    .nameAr("شركة " + i)
                    .taxNumber("PERF" + String.format("%03d", i))
                    .isActive(true)
                    .build());
            companyIds.add(company.getId());
        }

        UUID firstCompany = companyIds.get(0);
        superUserToken = jwtTokenProvider.createToken(
                user.getId(), user.getEmail(), true,
                "ETA", "PREPROD", (short) 2, firstCompany, TenantContext.Mode.OPERATIONAL_MODE);

        for (int c = 0; c < COMPANY_COUNT; c++) {
            UUID companyId = companyIds.get(c);
            List<EtaCustomer> batch = new ArrayList<>();
            for (int j = 0; j < CUSTOMERS_PER_COMPANY; j++) {
                EtaCustomer cust = EtaCustomer.builder()
                        .companyId(companyId)
                        .authorityEnvironmentId((short) 2)
                        .nameEn("Customer-" + c + "-" + j)
                        .customerType("B")
                        .taxNumber("TAX" + String.format("%02d", c) + String.format("%06d", j))
                        .addressData(Map.of("country", "EG", "governorate", "Cairo",
                                "regionCity", "Nasr City", "street", String.valueOf(j),
                                "buildingNumber", "1"))
                        .isActive(true)
                        .build();
                batch.add(cust);
                if (batch.size() >= 500) {
                    etaCustomerRepository.saveAll(batch);
                    etaCustomerRepository.flush();
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                etaCustomerRepository.saveAll(batch);
                etaCustomerRepository.flush();
            }
        }
    }

    @AfterAll
    void tearDown() {
        etaCustomerRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    @Order(1)
    void firstPage_etaCustomers_p95Under2Seconds() throws Exception {
        UUID targetCompany = companyIds.get(0);
        String token = superUserToken;

        List<Long> durations = new ArrayList<>();
        for (int run = 0; run < WARMUP_RUNS + MEASURED_RUNS; run++) {
            long start = System.nanoTime();
            mockMvc.perform(get("/api/companies/{companyId}/eta/customers", targetCompany)
                            .header("Authorization", "Bearer " + token)
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.total").value(CUSTOMERS_PER_COMPANY))
                    .andExpect(jsonPath("$.items.length()").value(20));
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            if (run >= WARMUP_RUNS) {
                durations.add(elapsed);
            }
        }

        durations.sort(Long::compareTo);
        int p95Index = (int) Math.ceil(0.95 * durations.size()) - 1;
        long p95 = durations.get(p95Index);

        assertThat(p95)
                .as("GET /eta/customers first page p95 latency should be < %d ms, was %d ms",
                        FIRST_PAGE_P95_MS, p95)
                .isLessThan(FIRST_PAGE_P95_MS);
    }

    @Test
    @Order(2)
    void companyFilter_etaCustomers_p95Under500ms() throws Exception {
        UUID targetCompany = companyIds.get(0);
        String token = superUserToken;

        List<Long> durations = new ArrayList<>();
        for (int run = 0; run < WARMUP_RUNS + MEASURED_RUNS; run++) {
            long start = System.nanoTime();
            mockMvc.perform(get("/api/companies/{companyId}/eta/customers", targetCompany)
                            .header("Authorization", "Bearer " + token)
                            .param("companyId", targetCompany.toString())
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.page.total").value(CUSTOMERS_PER_COMPANY));
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            if (run >= WARMUP_RUNS) {
                durations.add(elapsed);
            }
        }

        durations.sort(Long::compareTo);
        int p95Index = (int) Math.ceil(0.95 * durations.size()) - 1;
        long p95 = durations.get(p95Index);

        assertThat(p95)
                .as("GET /eta/customers with company filter p95 latency should be < %d ms, was %d ms",
                        FILTER_P95_MS, p95)
                .isLessThan(FILTER_P95_MS);
    }
}
