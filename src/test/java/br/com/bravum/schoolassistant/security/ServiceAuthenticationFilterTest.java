package br.com.bravum.schoolassistant.security;

import static org.junit.jupiter.api.Assertions.*;
import jakarta.servlet.FilterChain;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ServiceAuthenticationFilterTest {
    private static final String TENANT = "00000000-0000-0000-0000-000000000001";

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        ServiceAuthenticationFilter filter = new ServiceAuthenticationFilter("secret", TENANT);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/students");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("Não deveria chegar ao controller"));
        assertEquals(401, response.getStatus());
    }

    @Test
    void wrongTenantIsForbidden() throws Exception {
        ServiceAuthenticationFilter filter = new ServiceAuthenticationFilter("secret", TENANT);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/students");
        request.addHeader("Authorization", "Bearer secret");
        request.addHeader("X-Tenant-Id", UUID.randomUUID().toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("Não deveria chegar ao controller"));
        assertEquals(403, response.getStatus());
    }

    @Test
    void correctlyScopedTokenGrantsTenantPrincipal() throws Exception {
        ServiceAuthenticationFilter filter = new ServiceAuthenticationFilter("secret", TENANT);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/students");
        request.addHeader("Authorization", "Bearer secret");
        request.addHeader("X-Tenant-Id", TENANT);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> assertEquals(UUID.fromString(TENANT), TenantAccess.tenantId());
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
    }
}
