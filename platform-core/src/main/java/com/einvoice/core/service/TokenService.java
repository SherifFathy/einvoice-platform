package com.einvoice.core.service;

import java.util.List;
import java.util.Map;

/**
 * SPI interface for JWT token generation and validation.
 * Implemented by platform-security's JwtTokenProvider to decouple
 * platform-core from platform-security.
 */
public interface TokenService {

    /**
     * Generates an access token containing user identity and session claims.
     *
     * @param userId the authenticated user's ID
     * @param name the user's display name
     * @param email the user's email address
     * @param activeCompanyId the currently active company ID
     * @param role the user's role in the active company
     * @param permittedEnvironments list of environments the user may access
     * @param availableCompanies list of company maps the user belongs to
     * @param activeEnvironment the currently selected environment, or null
     * @param authority the active authority (ZATCA or ETA)
     * @param docType the active document type (INVOICE or RECEIPT)
     * @param subEnv the active sub-environment (SANDBOX, SIMULATION, PRODUCTION, PREPROD)
     * @param lovContextId the resolved LOV context ID
     * @param permissions list of granted permission keys for the active company+context
     * @param isSuperUser whether the user has super-user privileges
     * @return a signed JWT access token string
     */
    String generateAccessToken(Long userId, String name, String email,
            Long activeCompanyId, String role, List<String> permittedEnvironments,
            List<Map<String, Object>> availableCompanies, String activeEnvironment,
            String authority, String docType, String subEnv,
            Long lovContextId, List<String> permissions, boolean isSuperUser);

    /**
     * Generates a refresh token scoped to a specific company and LOV context.
     *
     * @param userId the user's ID
     * @param activeCompanyId the active company to preserve across refresh
     * @param lovContextId the LOV context to preserve across refresh (stored as lov_context_id claim)
     * @return a signed JWT refresh token string
     */
    String generateRefreshToken(Long userId, Long activeCompanyId, Long lovContextId);

    /**
     * Extracts the user ID (subject) from a token.
     *
     * @param token the JWT string
     * @return the user ID stored in the token subject
     */
    Long getUserIdFromToken(String token);

    /**
     * Extracts the active company ID claim from a refresh token.
     *
     * @param token the refresh JWT string
     * @return the active company ID, or null if not present
     */
    Long getActiveCompanyIdFromToken(String token);

    /**
     * Extracts the LOV context ID claim from a token.
     *
     * @param token the JWT string
     * @return the LOV context ID, or null if not present
     */
    Long getLovContextIdFromToken(String token);

    /**
     * Returns the access token expiry in seconds.
     *
     * @return access token expiry in seconds
     */
    long getAccessTokenExpirySeconds();

    /**
     * Returns the refresh token expiry in seconds.
     *
     * @return refresh token expiry in seconds
     */
    long getRefreshTokenExpirySeconds();
}
