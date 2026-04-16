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
     * Authenticates a user and returns JWT tokens.
     *
     * @param request login credentials
     * @return authentication response with tokens and user info
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResult result = authenticationService.login(request.email(), request.password());
        return ResponseEntity.ok(toLoginResponse(result));
    }

    /**
     * Switches the active company for the authenticated user after password verification.
     *
     * @param request target company ID and current password
     * @return new authentication response with updated company context
     */
    @PostMapping("/switch-company")
    public ResponseEntity<LoginResponse> switchCompany(
            @Valid @RequestBody SwitchCompanyRequest request) {
        Long userId = getCurrentUserId();
        AuthResult result = authenticationService.switchCompany(
                userId, request.companyId(), request.password());
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
        try {
            AuthResult result = authenticationService.selectEnvironment(
                    userId, companyId, request.environment());
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

    private LoginResponse toLoginResponse(AuthResult result) {
        UserInfo userInfo = new UserInfo(
                result.user.getId(),
                result.user.getName(),
                result.user.getEmail(),
                result.activeCompanyId,
                result.role,
                result.permittedEnvironments,
                result.availableCompanies,
                result.activeEnvironment);

        return new LoginResponse(
                result.accessToken,
                result.refreshToken,
                "Bearer",
                result.expiresIn,
                userInfo);
    }
}
