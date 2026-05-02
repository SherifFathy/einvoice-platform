package com.einvoice.security.tenant;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Servlet filter that adds the resolved LOV context identifier as a response header. */
@Component
public class LovContextResponseFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        filterChain.doFilter(request, response);

        Object claimsObj = request.getAttribute("jwtClaims");
        if (claimsObj instanceof Claims claims) {
            Object lovContextId = claims.get("lov_context_id");
            if (lovContextId != null) {
                response.setHeader("X-Lov-Context", String.valueOf(lovContextId));
            }
        }
    }
}
