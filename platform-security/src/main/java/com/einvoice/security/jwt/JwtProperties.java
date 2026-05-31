package com.einvoice.security.jwt;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Javadoc. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * JWT TTL in seconds. Accepted range: [60, 86400].
     * Defaults to 28800 (8 hours). Configured via JWT_TTL_SECONDS env var.
     */
    private long ttlSeconds = 28800;

    private String secret;

    @PostConstruct
    void validateTtl() {
        if (ttlSeconds < 60 || ttlSeconds > 86400) {
            throw new IllegalArgumentException(
                    "jwt.ttl-seconds must be between 60 and 86400, got " + ttlSeconds);
        }
    }
}
