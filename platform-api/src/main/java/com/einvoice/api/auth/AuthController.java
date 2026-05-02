package com.einvoice.api.auth;

import com.einvoice.api.auth.dto.LoginRequest;
import com.einvoice.api.auth.dto.LoginResponse;
import com.einvoice.api.auth.dto.LoginResponse.UserInfo;
import com.einvoice.api.auth.dto.LogoutRequest;
import com.einvoice.api.auth.dto.RefreshRequest;
import com.einvoice.api.auth.dto.SelectEnvironmentRequest;
import com.einvoice.api.auth.dto.SwitchCompanyRequest;
import com.einvoice.core.exception.InvalidCredentialsException;
import com.einvoice.core.service.AuthenticationService;
import com.einvoice.core.service.AuthenticationService.AuthResult;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** REST controller for authentication, company switching, and token management. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    /**
     * Authenticates a user with LOV context selection and returns JWT tokens.
     *
     * @param request login credentials and LOV context fields
     * @return authentication response with tokens and user info
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResult result = authenticationService.login(
                    request.email(), request.password(),
                    request.authority(), request.docType(), request.subEnvironment());
            return ResponseEntity.ok(toLoginResponse(result));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Switches the active company for the authenticated user after password verification.
     * Preserves the current LOV context across the switch.
     *
     * @param request target company ID and current password
     * @param httpRequest the HTTP request (used to extract JWT claims)
     * @return new authentication response with updated company context
     */
    @PostMapping("/switch-company")
    public ResponseEntity<LoginResponse> switchCompany(
            @Valid @RequestBody SwitchCompanyRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId();
        LovClaims lovClaims = extractLovClaims(httpRequest);
        AuthResult result = authenticationService.switchCompany(
                userId, request.companyId(), request.password(),
                lovClaims.authority, lovClaims.docType, lovClaims.subEnv,
                lovClaims.lovContextId);
        return ResponseEntity.ok(toLoginResponse(result));
    }

    /**
     * Refreshes the access token using a valid refresh token.
     *
     * @param request the refresh token to rotate
     * @return new authentication response with refreshed tokens
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthResult result = authenticationService.refresh(request.refreshToken());
        return ResponseEntity.ok(toLoginResponse(result));
    }

    /**
     * Revokes a refresh token, ending the session.
     *
     * @param request the refresh token to revoke
     * @return 204 No Content
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authenticationService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * Selects an active environment for the authenticated user's session.
     * Validates permissions and re-issues JWT with the environment claim.
     *
     * @param request the environment to select
     * @param httpRequest the HTTP request (used to extract JWT claims)
     * @return new authentication response with updated JWT containing active environment
     */
    @PostMapping("/select-environment")
    public ResponseEntity<LoginResponse> selectEnvironment(
            @Valid @RequestBody SelectEnvironmentRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getCurrentUserId();
        Long companyId = getActiveCompanyId(httpRequest);
        LovClaims lovClaims = extractLovClaims(httpRequest);
        try {
            AuthResult result = authenticationService.selectEnvironment(
                    userId, companyId, request.environment(),
                    lovClaims.authority, lovClaims.docType, lovClaims.subEnv,
                    lovClaims.lovContextId);
            return ResponseEntity.ok(toLoginResponse(result));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long userId) {
            return userId;
        }
        throw new InvalidCredentialsException("Not authenticated");
    }

    private Long getActiveCompanyId(HttpServletRequest request) {
        Object claimsObj = request.getAttribute("jwtClaims");
        if (claimsObj instanceof Claims claims) {
            Long companyId = claims.get("activeCompanyId", Long.class);
            if (companyId != null) {
                return companyId;
            }
        }
        throw new InvalidCredentialsException("No active company context");
    }

    private LovClaims extractLovClaims(HttpServletRequest request) {
        Object claimsObj = request.getAttribute("jwtClaims");
        if (claimsObj instanceof Claims claims) {
            String authority = claims.get("active_authority", String.class);
            String docType = claims.get("active_doc_type", String.class);
            String subEnv = claims.get("active_sub_env", String.class);
            Long lovContextId = toLong(claims.get("lov_context_id"));
            return new LovClaims(authority, docType, subEnv, lovContextId);
        }
        return new LovClaims(null, null, null, null);
    }

    private Long toLong(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(raw.toString());
    }

    private LoginResponse toLoginResponse(AuthResult result) {
        UserInfo userInfo = new UserInfo(
                result.user.getId(),
                result.user.getName(),
                result.user.getEmail(),
                result.activeCompanyId,
                result.role,
                result.permittedEnvironments,
                result.availableCompanies,
                result.activeEnvironment,
                result.activeAuthority,
                result.activeDocType,
                result.activeSubEnv,
                result.lovContextId,
                result.permissions,
                result.isSuperUser);

        return new LoginResponse(
                result.accessToken,
                result.refreshToken,
                "Bearer",
                result.expiresIn,
                userInfo);
    }

    private record LovClaims(
            String authority,
            String docType,
            String subEnv,
            Long lovContextId) {}
}
