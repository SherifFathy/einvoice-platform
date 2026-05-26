package com.einvoice.core;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Test-scoped Spring Boot configuration for {@code platform-core} persistence slice tests.
 * The production Spring Boot application lives in {@code platform-api}; this class exists
 * solely so {@code @DataJpaTest} and other test slices in this module can locate a
 * {@code @SpringBootConfiguration} by package walk. */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan("com.einvoice.core.domain")
@EnableJpaRepositories("com.einvoice.core.repository")
public class CorePersistenceTestConfig {
}
