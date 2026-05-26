package com.einvoice.zatca.client;

import com.einvoice.core.domain.config.ZatcaConfig;
import com.einvoice.core.repository.config.ZatcaConfigRepository;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** HTTP client for communicating with the ZATCA API. */
@Component
public class ZatcaHttpClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final ZatcaConfigRepository configRepository;

    public ZatcaHttpClient(ZatcaConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    /**
     * Resolves the base URL for a given company and ZATCA environment.
     *
     * @param companyId the owning company identifier
     * @param authorityEnvironmentId the ZATCA environment identifier
     * @return the configured base URL for the ZATCA API
     */
    public String getBaseUrl(UUID companyId, Short authorityEnvironmentId) {
        ZatcaConfig config = configRepository
                .findByCompanyAndAuthorityEnvironment(
                        companyId, authorityEnvironmentId)
                .orElseThrow(() -> new com.einvoice.core.error
                        .NoCertificateConfiguredException(
                        "No ZATCA config for company",
                        companyId, authorityEnvironmentId));
        return config.getBaseUrl();
    }

    /**
     * Builds a configured {@link RestClient} for the given base URL.
     *
     * @param baseUrl the ZATCA API base URL
     * @return a ready-to-use REST client instance
     */
    public RestClient buildClient(String baseUrl) {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
