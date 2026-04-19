package com.einvoice.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.einvoice.core.domain.AuthorityConfig;
import com.einvoice.core.domain.Branch;
import com.einvoice.core.domain.Company;
import com.einvoice.core.domain.enums.Authority;
import com.einvoice.core.domain.enums.Environment;
import com.einvoice.core.repository.AuthorityConfigRepository;
import com.einvoice.core.repository.BranchRepository;
import com.einvoice.core.repository.CompanyRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ConcurrentSubmissionDbTest {

    @Container
    @ServiceConnection
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
    private AuthorityConfigRepository authorityConfigRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private PlatformTransactionManager txManager;

    private TransactionTemplate txTemplate;

    private Company company;
    private Branch branch;

    @BeforeEach
    void setUp() {
        txTemplate = new TransactionTemplate(txManager);

        company = new Company();
        company.setNameEn("Concurrent Test Co");
        company.setNameAr("شركة اختبار");
        company.setVatNumber("500000000000001");
        company.setCountryCode("SA");
        company = companyRepository.save(company);

        branch = Branch.builder().nameEn("Concurrent Branch").company(company).build();
        branch = branchRepository.save(branch);
    }

    @AfterEach
    void tearDown() {
        authorityConfigRepository.deleteAll();
        branchRepository.deleteAll();
        companyRepository.deleteAll();
    }

    private AuthorityConfig createConfig() {
        AuthorityConfig config = AuthorityConfig.builder()
                .branch(branch)
                .authority(Authority.ZATCA)
                .environment(Environment.ZATCA_SANDBOX)
                .invoiceCounter(0L)
                .previousInvoiceHash("seed-hash-base64-value")
                .build();
        return authorityConfigRepository.save(config);
    }

    @Test
    void secondTransactionObservesFirstCommitHash() throws Exception {
        AuthorityConfig config = createConfig();

        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch secondDone = new CountDownLatch(1);
        AtomicReference<String> observedHash = new AtomicReference<>();
        AtomicReference<Long> observedCounter = new AtomicReference<>();
        AtomicReference<Exception> secondException = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        executor.submit(() -> {
            try {
                txTemplate.execute(status -> {
                    AuthorityConfig locked = authorityConfigRepository
                            .findWithLockByBranchIdAndAuthorityAndEnvironment(
                                    branch.getId(), Authority.ZATCA, Environment.ZATCA_SANDBOX)
                            .orElseThrow();
                    locked.setPreviousInvoiceHash("hash-from-first-tx");
                    locked.setInvoiceCounter(1L);
                    authorityConfigRepository.save(locked);

                    firstLocked.countDown();

                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
            } catch (Exception e) {
                firstLocked.countDown();
            }
        });

        assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();

        executor.submit(() -> {
            try {
                txTemplate.execute(status -> {
                    AuthorityConfig locked = authorityConfigRepository
                            .findWithLockByBranchIdAndAuthorityAndEnvironment(
                                    branch.getId(), Authority.ZATCA, Environment.ZATCA_SANDBOX)
                            .orElseThrow();
                    observedHash.set(locked.getPreviousInvoiceHash());
                    observedCounter.set(locked.getInvoiceCounter());
                    return null;
                });
            } catch (Exception e) {
                secondException.set(e);
            } finally {
                secondDone.countDown();
            }
        });

        assertThat(secondDone.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(secondException.get()).isNull();
        assertThat(observedHash.get()).isEqualTo("hash-from-first-tx");
        assertThat(observedCounter.get()).isEqualTo(1L);

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void counterMonotonicallyIncreasesAcrossTransactions() throws Exception {
        createConfig();

        int iterations = 5;
        for (int i = 0; i < iterations; i++) {
            txTemplate.execute(status -> {
                AuthorityConfig locked = authorityConfigRepository
                        .findWithLockByBranchIdAndAuthorityAndEnvironment(
                                branch.getId(), Authority.ZATCA, Environment.ZATCA_SANDBOX)
                        .orElseThrow();
                long current = locked.getInvoiceCounter() != null
                        ? locked.getInvoiceCounter() : 0L;
                locked.setInvoiceCounter(current + 1);
                locked.setPreviousInvoiceHash("hash-" + (current + 1));
                authorityConfigRepository.save(locked);
                return null;
            });
        }

        AuthorityConfig finalConfig = authorityConfigRepository
                .findByBranchIdAndAuthorityAndEnvironment(
                        branch.getId(), Authority.ZATCA, Environment.ZATCA_SANDBOX)
                .orElseThrow();
        assertThat(finalConfig.getInvoiceCounter()).isEqualTo(5L);
        assertThat(finalConfig.getPreviousInvoiceHash()).isEqualTo("hash-5");
    }
}
