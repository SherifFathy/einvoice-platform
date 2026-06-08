package com.einvoice.api.zatca;

import static org.assertj.core.api.Assertions.assertThat;

import com.einvoice.core.domain.company.Company;
import com.einvoice.core.domain.zatca.ZatcaChainState;
import com.einvoice.core.error.ChainBusyException;
import com.einvoice.core.repository.company.CompanyRepository;
import com.einvoice.zatca.chain.ZatcaChainService;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Wave 9 ZATCA same-company concurrency test (FR-019). Verifies the existing
 * {@code SELECT … FOR UPDATE} serialization in {@link ZatcaChainService}:
 * when two threads concurrently acquire the chain-state row for the same
 * (company, environment), exactly one proceeds and advances the counter, while
 * the other receives {@link ChainBusyException} ({@code CHAIN_BUSY}) once the
 * configured 30-second {@code lock_timeout} elapses, and
 * {@code zatca_chain_state.invoice_counter} advances exactly once.
 *
 * <p><b>Runtime.</b> The losing thread must outwait the production
 * {@code SET LOCAL lock_timeout = '30s'} hardcoded in
 * {@code ZatcaChainService.acquireForUpdate()} before Postgres cancels its
 * statement and Spring translates the failure into
 * {@link ChainBusyException}. Per the Phase 6 handoff the lock is
 * <b>not</b> weakened or made configurable for this test, so this case takes a
 * little over 30 seconds. It fails fast on regression: if the pessimistic lock
 * were removed, the loser would acquire immediately and no
 * {@code ChainBusyException} would be thrown, failing the assertion.
 *
 * <p>The winner holds its transaction open (via a {@link TransactionTemplate})
 * so the {@code FOR UPDATE} lock survives across the separate
 * {@code acquireForUpdate} + {@code advance} service calls; both service calls
 * join that outer transaction (default {@code REQUIRED} propagation).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ZatcaChainConcurrencyTest {

    private static final short ZATCA_SANDBOX_ENV = 5;
    private static final String WINNER_HASH = "hash-from-winner";
    private static final long LOCK_TIMEOUT_MS = 30_000L;
    private static final long FUTURE_WAIT_MS = LOCK_TIMEOUT_MS + 30_000L;

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

    @Autowired private ZatcaChainService chainService;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UUID companyId;

    @AfterEach
    void tearDown() {
        if (companyId != null) {
            jdbcTemplate.update(
                    "DELETE FROM zatca_chain_state WHERE company_id = ?",
                    (Object) companyId);
            companyRepository.deleteById(companyId);
            companyId = null;
        }
    }

    @Test
    void oneProceedsOtherGetsChainBusyAndCounterAdvancesOnce() throws Exception {
        companyId = companyRepository.save(Company.builder()
                .nameEn("Concurrency Co").nameAr("شركة")
                .taxNumber("3000000300001").isActive(true).build()).getId();
        seedChainState(companyId, ZATCA_SANDBOX_ENV);
        assertCounter(0);

        CountDownLatch winnerAcquired = new CountDownLatch(1);
        CountDownLatch releaseWinner = new CountDownLatch(1);
        ExecutorService exec = Executors.newFixedThreadPool(2);
        TransactionTemplate winnerTx = new TransactionTemplate(txManager);

        try {
            Future<Void> winner = exec.submit(() -> {
                winnerTx.executeWithoutResult(status -> {
                    ZatcaChainState row = chainService
                            .acquireForUpdate(companyId, ZATCA_SANDBOX_ENV);
                    chainService.advance(row, WINNER_HASH);
                    winnerAcquired.countDown();
                    awaitUninterruptibly(releaseWinner);
                });
                return null;
            });

            Future<Throwable> loser = exec.submit(() -> {
                try {
                    awaitUninterruptibly(winnerAcquired);
                    chainService.acquireForUpdate(companyId, ZATCA_SANDBOX_ENV);
                    return null;
                } catch (Throwable t) {
                    return t;
                }
            });

            Throwable loserError = loser.get(FUTURE_WAIT_MS, TimeUnit.MILLISECONDS);
            assertThat(loserError)
                    .as("the loser must receive CHAIN_BUSY, not silently proceed")
                    .isNotNull()
                    .isInstanceOf(ChainBusyException.class);
            assertThat(((ChainBusyException) loserError).getCode())
                    .isEqualTo(ChainBusyException.CODE);

            assertThat(winner.isDone())
                    .as("the winner is still holding the lock (not committed)")
                    .isFalse();

            releaseWinner.countDown();
            winner.get(FUTURE_WAIT_MS, TimeUnit.MILLISECONDS);

            Integer counter = jdbcTemplate.queryForObject(
                    "SELECT invoice_counter FROM zatca_chain_state "
                            + "WHERE company_id = ? AND authority_environment_id = ?",
                    Integer.class, companyId, ZATCA_SANDBOX_ENV);
            assertThat(counter)
                    .as("counter advanced exactly once (winner), loser never advanced")
                    .isEqualTo(1);
            String storedHash = jdbcTemplate.queryForObject(
                    "SELECT previous_invoice_hash FROM zatca_chain_state "
                            + "WHERE company_id = ? AND authority_environment_id = ?",
                    String.class, companyId, ZATCA_SANDBOX_ENV);
            assertThat(storedHash).isEqualTo(WINNER_HASH);
        } finally {
            exec.shutdownNow();
            exec.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private void seedChainState(UUID companyId, short envId) {
        jdbcTemplate.update(
                "INSERT INTO zatca_chain_state "
                        + "(company_id, authority_environment_id, invoice_counter) "
                        + "VALUES (?, ?, 0)",
                companyId, envId);
    }

    private void assertCounter(int expected) {
        Integer counter = jdbcTemplate.queryForObject(
                "SELECT invoice_counter FROM zatca_chain_state "
                        + "WHERE company_id = ? AND authority_environment_id = ?",
                Integer.class, companyId, ZATCA_SANDBOX_ENV);
        assertThat(counter).isEqualTo(expected);
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        while (latch.getCount() > 0) {
            try {
                latch.await();
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
