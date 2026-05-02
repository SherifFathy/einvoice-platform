package com.einvoice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.einvoice.security.permission.LovContextMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class LovContextMapperIT {

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

    @Test
    void zatcaSandbox_mapsToZatcaSandbox() {
        assertThat(LovContextMapper.toAuthorityEnvironment("ZATCA", "SANDBOX"))
                .isEqualTo("ZATCA_SANDBOX");
    }

    @Test
    void zatcaSimulation_mapsToZatcaSimulation() {
        assertThat(LovContextMapper.toAuthorityEnvironment("ZATCA", "SIMULATION"))
                .isEqualTo("ZATCA_SIMULATION");
    }

    @Test
    void zatcaProduction_mapsToZatcaProduction() {
        assertThat(LovContextMapper.toAuthorityEnvironment("ZATCA", "PRODUCTION"))
                .isEqualTo("ZATCA_PRODUCTION");
    }

    @Test
    void etaPreprod_mapsToEtaPreproduction() {
        assertThat(LovContextMapper.toAuthorityEnvironment("ETA", "PREPROD"))
                .isEqualTo("ETA_PREPRODUCTION");
    }

    @Test
    void etaProduction_mapsToEtaProduction() {
        assertThat(LovContextMapper.toAuthorityEnvironment("ETA", "PRODUCTION"))
                .isEqualTo("ETA_PRODUCTION");
    }

    @Test
    void zatcaPreprod_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> LovContextMapper.toAuthorityEnvironment("ZATCA", "PREPROD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void etaSimulation_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> LovContextMapper.toAuthorityEnvironment("ETA", "SIMULATION"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
