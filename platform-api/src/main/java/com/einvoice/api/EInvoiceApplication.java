package com.einvoice.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Spring Boot entry point for the e-invoice platform API. */
@SpringBootApplication(scanBasePackages = "com.einvoice")
@EntityScan(basePackages = "com.einvoice")
@EnableJpaRepositories(basePackages = "com.einvoice")
@EnableScheduling
public class EInvoiceApplication {

    /**
     * Application entry point.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(EInvoiceApplication.class, args);
    }
}
