package com.einvoice.security.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuration properties for JWT token generation and validation. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String secret;
    private String accessTokenExpiry = "15m";
    private String refreshTokenExpiry = "7d";

    /**
     * Parses the access token expiry duration string into seconds.
     *
     * @return access token expiry in seconds
     */
    public long getAccessTokenExpirySeconds() {
        return parseDuration(accessTokenExpiry);
    }

    /**
     * Parses the refresh token expiry duration string into seconds.
     *
     * @return refresh token expiry in seconds
     */
    public long getRefreshTokenExpirySeconds() {
        return parseDuration(refreshTokenExpiry);
    }

    private long parseDuration(String duration) {
        if (duration.endsWith("m")) {
            return Long.parseLong(duration.substring(0, duration.length() - 1)) * 60;
        } else if (duration.endsWith("h")) {
            return Long.parseLong(duration.substring(0, duration.length() - 1)) * 3600;
        } else if (duration.endsWith("d")) {
            return Long.parseLong(duration.substring(0, duration.length() - 1)) * 86400;
        } else if (duration.endsWith("s")) {
            return Long.parseLong(duration.substring(0, duration.length() - 1));
        }
        return Long.parseLong(duration);
    }
}
