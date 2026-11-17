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
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class ServiceAuthenticationFilter extends OncePerRequestFilter {
    private static final Pattern ALEXA_USER = Pattern.compile("amzn1\\.ask\\.account\\.[a-zA-Z0-9._-]{5,512}");
    private final String apiKey, tenantId, alexaKey;
    public ServiceAuthenticationFilter(String apiKey, String tenantId, String alexaKey) {
        this.apiKey = apiKey == null ? "" : apiKey;
        this.tenantId = tenantId == null ? "" : tenantId;
        this.alexaKey = alexaKey == null ? "" : alexaKey;
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod()) ||
                "/actuator/health".equals(request.getServletPath());
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        boolean isAlexa = request.getServletPath().startsWith("/alexa/skill/");
        String key = isAlexa ? alexaKey : apiKey;
        String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (key.length() < 32 || auth == null || !auth.startsWith("Bearer ") ||
                !MessageDigest.isEqual(auth.substring(7).getBytes(StandardCharsets.UTF_8),
                                       key.getBytes(StandardCharsets.UTF_8))) {
            response.sendError(401); return;
        }
        Object principal;
        String role;
        if (isAlexa) {
            String user = request.getHeader("X-Alexa-User-Id");
            if (user == null || !ALEXA_USER.matcher(user).matches()) { response.sendError(401); return; }
            principal = user; role = "ROLE_ALEXA";
        } else {
            if (!tenantId.equals(request.getHeader("X-Tenant-Id"))) {response.sendError(403);return;}
            try { principal = UUID.fromString(tenantId); }
            catch (IllegalArgumentException e) { response.sendError(401); return; }
            role = "ROLE_SERVICE";
        }
        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null,
                            List.of(new SimpleGrantedAuthority(role))));
            chain.doFilter(request,response);
        } finally { SecurityContextHolder.clearContext(); }
    }
}
