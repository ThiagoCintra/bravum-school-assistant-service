package br.com.bravum.schoolassistant.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Credencial estritamente interna. Substituir pelo contexto do login Bravum antes de acesso familiar. */
@Component
public class ServiceAuthenticationFilter extends OncePerRequestFilter {
    private final String expectedKey;
    private final String expectedTenant;

    public ServiceAuthenticationFilter(@Value("${assistant.security.api-key:}") String expectedKey,
            @Value("${assistant.security.tenant-id:}") String expectedTenant) {
        this.expectedKey = expectedKey;
        this.expectedTenant = expectedTenant;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || "/actuator/health".equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        String requestedTenant = request.getHeader("X-Tenant-Id");
        if (expectedKey.isBlank() || expectedTenant.isBlank() || header == null
                || !header.startsWith("Bearer ") || !constantTimeEquals(header.substring(7), expectedKey)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        if (!expectedTenant.equals(requestedTenant)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        final UUID tenant;
        try { tenant = UUID.fromString(expectedTenant); }
        catch (IllegalArgumentException ex) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(tenant, null,
                            List.of(new SimpleGrantedAuthority("ROLE_SERVICE"))));
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
