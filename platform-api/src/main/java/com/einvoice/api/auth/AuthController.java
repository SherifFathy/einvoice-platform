package com.einvoice.api.auth;

import com.einvoice.api.auth.dto.CompaniesRequest;
import com.einvoice.api.auth.dto.CompaniesResponse;
import com.einvoice.api.auth.dto.EnvironmentsRequest;
import com.einvoice.api.auth.dto.EnvironmentsResponse;
import com.einvoice.api.auth.dto.LoginRequest;
import com.einvoice.api.auth.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for authentication and authorization endpoints. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/environments")
    public EnvironmentsResponse listEnvironments(
            @Valid @RequestBody EnvironmentsRequest request) {
        return authService.listEnvironments(request);
    }

    @PostMapping("/companies")
    public CompaniesResponse listCompanies(
            @Valid @RequestBody CompaniesRequest request) {
        return authService.listCompanies(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}
