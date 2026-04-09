package com.einvoice.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Entry point for the E-Invoice Platform Spring Boot application.
 */
@SpringBootApplication(scanBasePackages = "com.einvoice")
@EntityScan(basePackages = "com.einvoice")
@EnableJpaRepositories(basePackages = "com.einvoice")
public class EInvoiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EInvoiceApplication.class, args);
    }
}
