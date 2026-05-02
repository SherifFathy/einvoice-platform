package com.einvoice.security.jwt;

import com.einvoice.core.service.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** Generates, validates, and parses JWT tokens for authentication. */
@Service
public class JwtTokenProvider implements TokenService {

    private final SecretKey key;
    private final JwtProperties jwtProperties;

    /**
     * Creates a JwtTokenProvider with the given JWT configuration.
     *
     * @param jwtProperties JWT configuration properties
     */
    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        byte[] keyBytes = Base64.getDecoder().decode(jwtProperties.getSecret());
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public String generateAccessToken(Long userId, String name, String email,
            Long activeCompanyId, String role, List<String> permittedEnvironments,
            List<Map<String, Object>> availableCompanies, String activeEnvironment,
            String authority, String docType, String subEnv,
            Long lovContextId, List<String> permissions, boolean isSuperUser) {
        Date now = new Date();
        Date expiry = new Date(now.getTime()
                + jwtProperties.getAccessTokenExpirySeconds() * 1000);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("name", name)
                .claim("email", email)
                .claim("activeCompanyId", activeCompanyId)
                .claim("role", role)
                .claim("permittedEnvironments", permittedEnvironments)
                .claim("availableCompanies", availableCompanies)
                .claim("activeEnvironment", activeEnvironment)
                .claim("active_authority", authority)
                .claim("active_doc_type", docType)
                .claim("active_sub_env", subEnv)
                .claim("lov_context_id", lovContextId)
                .claim("permissions", permissions)
                .claim("is_super_user", isSuperUser)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    @Override
    public String generateRefreshToken(Long userId, Long activeCompanyId, Long lovContextId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime()
                + jwtProperties.getRefreshTokenExpirySeconds() * 1000);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("type", "refresh")
                .claim("activeCompanyId", activeCompanyId)
                .claim("lov_context_id", lovContextId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * Parses and validates a JWT, returning its claims.
     *
     * @param token the JWT string to parse
     * @return the parsed JWT claims
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Validates a token without throwing.
     *
     * @param token the JWT string to validate
     * @return true if the token is valid, false otherwise
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    @Override
    public Long getActiveCompanyIdFromToken(String token) {
        Claims claims = parseToken(token);
        Object activeCompanyId = claims.get("activeCompanyId");
        if (activeCompanyId instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    @Override
    public Long getLovContextIdFromToken(String token) {
        Claims claims = parseToken(token);
        Object lovContextId = claims.get("lov_context_id");
        if (lovContextId instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    @Override
    public long getAccessTokenExpirySeconds() {
        return jwtProperties.getAccessTokenExpirySeconds();
    }

    @Override
    public long getRefreshTokenExpirySeconds() {
        return jwtProperties.getRefreshTokenExpirySeconds();
    }
}
