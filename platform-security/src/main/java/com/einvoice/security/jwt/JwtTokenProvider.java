package com.einvoice.security.jwt;

import com.einvoice.security.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Javadoc. */
@Service
public class JwtTokenProvider {

    private final SecretKey key;
    private final JwtProperties jwtProperties;

    /**
     * Javadoc.
     * @param jwtProperties jwt configuration
     */
    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getSecret());
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Javadoc.
     * @param userId user identifier
     * @param email user email
     * @param isSuperUser super user flag
     * @param authority authority name
     * @param environment environment name
     * @param authorityEnvironmentId authority environment id
     * @param companyId company identifier
     * @param mode session mode
     * @return token string
     */
    public String createToken(UUID userId, String email, boolean isSuperUser,
            String authority, String environment, Short authorityEnvironmentId,
            UUID companyId, TenantContext.Mode mode) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getTtlSeconds() * 1000L);

        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim("email", email)
                .claim("isSuperUser", isSuperUser)
                .claim("authority", authority)
                .claim("environment", environment)
                .claim("authorityEnvironmentId", authorityEnvironmentId)
                .claim("mode", mode.name())
                .issuedAt(now)
                .expiration(expiry);

        if (companyId != null) {
            builder.claim("companyId", companyId.toString());
        }

        return builder.signWith(key).compact();
    }

    /**
     * Javadoc.
     * @param token JWT token string
     * @return tenant context holder
     */
    public TenantContext.Holder parseToTenantContext(String token) {
        Claims claims = parseToken(token);

        UUID userId = UUID.fromString(claims.getSubject());
        String email = claims.get("email", String.class);
        boolean isSuperUser = Boolean.TRUE.equals(claims.get("isSuperUser", Boolean.class));
        String authority = claims.get("authority", String.class);
        String environment = claims.get("environment", String.class);
        Short authorityEnvironmentId = toShort(claims.get("authorityEnvironmentId"));
        TenantContext.Mode mode = TenantContext.Mode.valueOf(claims.get("mode", String.class));

        String companyIdStr = claims.get("companyId", String.class);
        UUID companyId = companyIdStr != null ? UUID.fromString(companyIdStr) : null;

        long issuedAt = claims.getIssuedAt() != null ? claims.getIssuedAt().getTime() : 0L;
        String jti = claims.getId();

        return new TenantContext.Holder(
                userId, companyId, authorityEnvironmentId,
                authority, environment, mode, isSuperUser, issuedAt, jti);
    }

    /**
     * Javadoc.
     * @param token JWT token string
     * @return parsed claims
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Javadoc.
     * @param token JWT token string
     * @return true if valid
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public long getTtlSeconds() {
        return jwtProperties.getTtlSeconds();
    }

    private Short toShort(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.shortValue();
        }
        return Short.valueOf(raw.toString());
    }
}
