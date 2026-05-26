package com.einvoice.api.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.einvoice.api.admin.service.AdminUserService;
import com.einvoice.core.domain.user.User;
import com.einvoice.core.error.LastSuperUserProtectedException;
import com.einvoice.core.repository.user.UserRepository;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class LastSuperUserConcurrencyIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("einvoice_test").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.clean-disabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired private AdminUserService adminUserService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User su1;

    @BeforeEach
    void setUp() {
        su1 = userRepository.save(User.builder()
                .name("SU1").email("su1@concurrency.com")
                .passwordHash(passwordEncoder.encode("P")).isSuperUser(true).isActive(true).build());
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    /**
     * Single active Super User; two threads simultaneously try to deactivate the same row.
     *
     * Both threads must serialize on the FOR UPDATE row lock and pg_advisory_xact_lock,
     * each independently observe count(other active SUs) == 0, and both throw
     * LastSuperUserProtectedException. The invariant (>=1 active SU) must hold.
     *
     * Note: T084a's spec text suggested a 1-success / 1-conflict outcome, which is
     * incompatible with strong locking when only one SU exists — once both threads
     * serialize, they both observe the same zero-peer count and must both reject.
     * The honest assertion is 0 successes / 2 conflicts and an unchanged DB state.
     * The cross-target case below covers the actual race the locking is designed to close.
     */
    @Test
    void sameTargetRace_bothCorrectlyRejected() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Runnable deactivator = () -> {
            try {
                startLatch.await();
                adminUserService.deactivate(su1.getId());
                successCount.incrementAndGet();
            } catch (LastSuperUserProtectedException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                doneLatch.countDown();
            }
        };

        Thread t1 = new Thread(deactivator, "deactivate-1");
        Thread t2 = new Thread(deactivator, "deactivate-2");
        t1.start();
        t2.start();
        startLatch.countDown();
        doneLatch.await();

        assertThat(successCount.get()).isEqualTo(0);
        assertThat(conflictCount.get()).isEqualTo(2);

        User reloaded = userRepository.findById(su1.getId()).orElseThrow();
        assertThat(reloaded.getIsActive()).isTrue();
        assertThat(reloaded.getIsSuperUser()).isTrue();
    }

    @Test
    void crossTargetRace_exactlyOneSurvives() throws Exception {
        User su2 = userRepository.save(User.builder()
                .name("SU2").email("su2@concurrency.com")
                .passwordHash(passwordEncoder.encode("P")).isSuperUser(true).isActive(true).build());

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        Thread t1 = new Thread(() -> {
            try {
                startLatch.await();
                adminUserService.deactivate(su1.getId());
                successCount.incrementAndGet();
            } catch (LastSuperUserProtectedException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                doneLatch.countDown();
            }
        }, "deactivate-su1");

        Thread t2 = new Thread(() -> {
            try {
                startLatch.await();
                adminUserService.deactivate(su2.getId());
                successCount.incrementAndGet();
            } catch (LastSuperUserProtectedException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                doneLatch.countDown();
            }
        }, "deactivate-su2");

        t1.start();
        t2.start();
        startLatch.countDown();
        doneLatch.await();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        long activeSuperUsers = userRepository.countActiveSuperUsers();
        assertThat(activeSuperUsers).isEqualTo(1);
    }
}
