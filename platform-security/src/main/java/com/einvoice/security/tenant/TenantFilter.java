package com.einvoice.security.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Javadoc. */
@Component
@Order(2)
public class TenantFilter extends OncePerRequestFilter {

    private static final String AUTH_PATH_PREFIX = "/api/auth";
    private static final String SESSION_CONTEXT_PATH = "/api/session/context";
    private static final String HEALTH_PATH_PREFIX = "/api/health";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if (path.startsWith(AUTH_PATH_PREFIX)
                || path.equals(SESSION_CONTEXT_PATH)
                || path.startsWith(HEALTH_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        TenantContext.Holder ctx = TenantContext.current();
        if (ctx == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (ctx.mode() == TenantContext.Mode.ADMIN_MODE) {
            if (path.startsWith("/api/admin")) {
                filterChain.doFilter(request, response);
                return;
            }
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"code\":\"COMPANY_CONTEXT_REQUIRED\","
                    + "\"message\":\"A company selection is required for operational endpoints\"}");
            return;
        }

        // AUTHORITY_SCOPED and OPERATIONAL_MODE sessions pass this coarse filter.
        // Fine-grained authorization is enforced downstream: cross-company reads
        // are intentionally open, while writes are gated by PermissionAspect (which
        // resolves and stamps the path company) and by the per-company constraint in
        // each service's loadWithinTenant. ADMIN_MODE is the only mode restricted here.
        filterChain.doFilter(request, response);
    }
}
