package com.einvoice.api.integration.config;

import com.einvoice.api.integration.filter.IngestionPayloadArchiveFilter;
import com.einvoice.api.integration.service.InboundPayloadArchiveService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the ingestion-payload-archive filter at exactly
 * {@code Ordered.HIGHEST_PRECEDENCE + 50} so it runs before Spring
 * Security's {@code DelegatingFilterProxy} (per tasks.md T009).
 */
@Configuration
public class IntegrationFilterConfig {

    /**
     * Creates the filter registration bean for the archive filter.
     *
     * @param archiveService the archive service
     * @param objectMapper the object mapper
     * @return the filter registration bean
     */
    @Bean
    public FilterRegistrationBean<Filter> ingestionPayloadArchiveFilterRegistration(
            InboundPayloadArchiveService archiveService, ObjectMapper objectMapper) {
        IngestionPayloadArchiveFilter filter = new IngestionPayloadArchiveFilter(archiveService, objectMapper);
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/api/integration/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 50);
        registration.setName("ingestionPayloadArchiveFilter");
        return registration;
    }
}
