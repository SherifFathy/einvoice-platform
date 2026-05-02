package com.einvoice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.Invoice;
import com.einvoice.core.domain.User;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.domain.enums.InvoiceStatus;
import com.einvoice.core.domain.enums.InvoiceType;
import com.einvoice.core.repository.AuthorityConfigRepository;
import com.einvoice.core.repository.BranchRepository;
import com.einvoice.core.repository.CompanyRepository;
import com.einvoice.core.repository.InvoiceRepository;
import com.einvoice.core.repository.UserRepository;
import com.einvoice.security.jwt.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
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
class TenantIsolationSubmissionTest {

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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private AuthorityConfigRepository authorityConfigRepository;

    private Company companyA;
    private Company companyB;
    private Branch branchA;
    private Branch branchB;
    private Invoice invoiceA;
    private Invoice invoiceAInReview;
    private String tokenA;
    private String tokenB;

    @BeforeEach
    void setUp() {
        TenantContext.clear();

        companyA = new Company();
        companyA.setNameEn("Submitter A");
        companyA.setNameAr("أ");
        companyA.setVatNumber("300000000000001");
        companyA = companyRepository.save(companyA);

        companyB = new Company();
        companyB.setNameEn("Submitter B");
        companyB.setNameAr("ب");
        companyB.setVatNumber("400000000000002");
        companyB = companyRepository.save(companyB);

        branchA = Branch.builder().nameEn("Branch A").company(companyA).build();
        branchA = branchRepository.save(branchA);

        branchB = Branch.builder().nameEn("Branch B").company(companyB).build();
        branchB = branchRepository.save(branchB);

        User userA = new User();
        userA.setEmail("a@submit.com");
        userA.setPasswordHash(passwordEncoder.encode("pass"));
        userA.setName("User A");
        userRepository.save(userA);

        User userB = new User();
        userB.setEmail("b@submit.com");
        userB.setPasswordHash(passwordEncoder.encode("pass"));
        userB.setName("User B");
        userRepository.save(userB);

        tokenA = jwtTokenProvider.generateAccessToken(
                userA.getId(), userA.getName(), userA.getEmail(),
                companyA.getId(), "ACCOUNTANT",
                List.of(), List.of(), null);
        tokenB = jwtTokenProvider.generateAccessToken(
                userB.getId(), userB.getName(), userB.getEmail(),
                companyB.getId(), "ACCOUNTANT",
                List.of(), List.of(), null);

        invoiceA = Invoice.builder()
                .company(companyA)
                .branch(branchA)
                .type(InvoiceType.TAX_INVOICE)
                .authority(Authority.ZATCA)
                .environment(Environment.ZATCA_SANDBOX)
                .status(InvoiceStatus.READY_FOR_SUBMISSION)
                .issueDate(LocalDate.now())
                .currency("SAR")
                .createdBy(userA)
                .build();
        invoiceA = invoiceRepository.save(invoiceA);

        invoiceAInReview = Invoice.builder()
                .company(companyA)
                .branch(branchA)
                .type(InvoiceType.TAX_INVOICE)
                .authority(Authority.ETA)
                .environment(Environment.ETA_PREPRODUCTION)
                .status(InvoiceStatus.IN_REVIEW)
                .issueDate(LocalDate.now())
                .currency("EGP")
                .createdBy(userA)
                .build();
        invoiceAInReview = invoiceRepository.save(invoiceAInReview);

        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        invoiceRepository.deleteAll();
        authorityConfigRepository.deleteAll();
        branchRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();
    }

    @Test
    void companyBCannotSubmitCompanyAInvoice() throws Exception {
        mockMvc.perform(post("/api/invoices/{id}/submit", invoiceA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).isIn(403, 404);
                });
    }

    @Test
    void companyBCannotValidateCompanyAInvoice() throws Exception {
        mockMvc.perform(post("/api/invoices/{id}/validate", invoiceA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).isIn(403, 404);
                });
    }

    @Test
    void companyBCannotRetryCompanyAInvoice() throws Exception {
        mockMvc.perform(post("/api/invoices/{id}/retry", invoiceA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).isIn(403, 404);
                });
    }

    @Test
    void companyBCannotReturnCompanyAInvoiceToDraft() throws Exception {
        mockMvc.perform(post("/api/invoices/{id}/return-to-draft", invoiceA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).isIn(403, 404);
                });
    }

    @Test
    void companyBCannotSeeCompanyASubmissionHistory() throws Exception {
        mockMvc.perform(get("/api/invoices/{id}/submissions", invoiceA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).isIn(403, 404);
                });
    }

    @Test
    void companyBCannotSeeCompanyAArtifacts() throws Exception {
        mockMvc.perform(get("/api/invoices/{id}/artifacts", invoiceA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).isIn(403, 404);
                });
    }

    @Test
    void companyBCannotDownloadCompanyAArtifact() throws Exception {
        mockMvc.perform(get("/api/invoices/{id}/artifacts/SIGNED_XML",
                        invoiceA.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).isIn(403, 404);
                });
    }

    @Test
    void companyBCannotCheckStatusOfCompanyAInvoice() throws Exception {
        mockMvc.perform(post("/api/invoices/{id}/check-status",
                        invoiceAInReview.getId())
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status).isIn(403, 404);
                });
    }

    @Test
    void companyACanSeeOwnInvoiceSubmissions() throws Exception {
        mockMvc.perform(get("/api/invoices/{id}/submissions", invoiceA.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }

    @Test
    void companyACanSeeOwnInvoiceArtifacts() throws Exception {
        mockMvc.perform(get("/api/invoices/{id}/artifacts", invoiceA.getId())
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk());
    }
}
