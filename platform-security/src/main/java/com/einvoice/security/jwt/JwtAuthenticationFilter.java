package com.einvoice.security.jwt;

import com.einvoice.core.domain.enums.Role;
import com.einvoice.security.rbac.RolePermissions;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Extracts JWT from Authorization header, validates it, and sets Spring Security context. */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final RolePermissions rolePermissions;

    /**
     * Creates the JWT authentication filter.
     *
     * @param jwtTokenProvider the JWT token provider
     * @param rolePermissions the role-to-authority mapper
     */
    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider,
            RolePermissions rolePermissions) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.rolePermissions = rolePermissions;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtTokenProvider.validateToken(token)) {
                Claims claims = jwtTokenProvider.parseToken(token);
                Long userId = Long.parseLong(claims.getSubject());
                String roleStr = claims.get("role", String.class);

                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_" + roleStr));

                if (roleStr != null) {
                    try {
                        Role role = Role.valueOf(roleStr);
                        Set<String> perms = rolePermissions.getAuthorities(role);
                        for (String perm : perms) {
                            authorities.add(new SimpleGrantedAuthority(perm));
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId, null, authorities);
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                request.setAttribute("jwtClaims", claims);
            }
        }
        filterChain.doFilter(request, response);
    }
}
