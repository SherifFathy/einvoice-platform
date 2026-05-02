package com.einvoice.security.tenant;

import com.einvoice.core.context.EnvironmentContext;
import com.einvoice.core.context.TenantContext;
import com.einvoice.core.domain.enums.Environment;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Extracts active_company_id and environment from JWT claims and sets ThreadLocal contexts. */
@Component
public class TenantFilter extends OncePerRequestFilter {

    private static final String ADMIN_PATH_PREFIX = "/api/admin";
    private static final String AUTH_PATH_PREFIX = "/api/auth";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path.startsWith(ADMIN_PATH_PREFIX) || path.startsWith(AUTH_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Object claimsObj = request.getAttribute("jwtClaims");
            if (claimsObj instanceof Claims claims) {
                Long companyId = toLong(claims.get("activeCompanyId"));
                if (companyId != null) {
                    TenantContext.setCurrentTenantId(companyId);
                }

                Long lovContextId = toLong(claims.get("lov_context_id"));
                if (lovContextId != null) {
                    TenantContext.setLovContextId(lovContextId);
                }

                String environment = claims.get("activeEnvironment", String.class);
                if (environment != null && !environment.isBlank()) {
                    try {
                        Environment.valueOf(environment);
                        EnvironmentContext.setCurrentEnvironment(environment);
                    } catch (IllegalArgumentException e) {
                        // Invalid environment in JWT — skip setting context
                    }
                } else {
                    String headerEnv = request.getHeader("X-Environment");
                    if (headerEnv != null && !headerEnv.isBlank()) {
                        setEnvironmentFromHeader(headerEnv, claims);
                    }
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            EnvironmentContext.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private void setEnvironmentFromHeader(String headerEnv, Claims claims) {
        try {
            Environment env = Environment.valueOf(headerEnv);
            List<String> permitted = claims.get("permittedEnvironments", List.class);
            if (permitted != null && permitted.contains(env.name())) {
                EnvironmentContext.setCurrentEnvironment(env.name());
            }
        } catch (IllegalArgumentException e) {
            // Invalid environment — skip
        }
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
}
