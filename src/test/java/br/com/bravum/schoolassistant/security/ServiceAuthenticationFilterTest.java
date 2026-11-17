package br.com.bravum.schoolassistant.security;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ServiceAuthenticationFilterTest {
    private static final String TENANT = "00000000-0000-0000-0000-000000000001";
    private static final String ADMIN = "admin-token-with-at-least-32-bytes-value";
    private static final String ALEXA = "alexa-token-with-at-least-32-bytes-value";

    @Test void missingTokenIsUnauthorized() throws Exception {
        ServiceAuthenticationFilter filter = new ServiceAuthenticationFilter(ADMIN,TENANT,ALEXA);
        MockHttpServletRequest request = new MockHttpServletRequest("GET","/students");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)->fail("Não deveria chegar ao controller"));
        assertEquals(401,response.getStatus());
    }
    @Test void wrongTenantIsForbidden() throws Exception {
        ServiceAuthenticationFilter filter = new ServiceAuthenticationFilter(ADMIN,TENANT,ALEXA);
        MockHttpServletRequest request = new MockHttpServletRequest("GET","/students");
        request.addHeader("Authorization","Bearer "+ADMIN);
        request.addHeader("X-Tenant-Id", UUID.randomUUID().toString());
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)->fail("Não deveria chegar ao controller"));
        assertEquals(403,response.getStatus());
    }
    @Test void correctlyScopedTokenGrantsTenantPrincipal() throws Exception {
        ServiceAuthenticationFilter filter = new ServiceAuthenticationFilter(ADMIN,TENANT,ALEXA);
        MockHttpServletRequest request = new MockHttpServletRequest("GET","/students");
        request.addHeader("Authorization","Bearer "+ADMIN);
        request.addHeader("X-Tenant-Id",TENANT);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)->assertEquals(UUID.fromString(TENANT),TenantAccess.tenantId()));
        assertEquals(200,response.getStatus());
    }
    @Test void alexaKeyCannotCallAdminEndpoints() throws Exception {
        ServiceAuthenticationFilter filter = new ServiceAuthenticationFilter(ADMIN,TENANT,ALEXA);
        MockHttpServletRequest request = new MockHttpServletRequest("POST","/alexa/admin/link-codes/abc");
        request.addHeader("Authorization","Bearer "+ALEXA);
        request.addHeader("X-Tenant-Id",TENANT);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)->fail("Skill não pode entrar no admin"));
        assertEquals(401,response.getStatus());
    }
    @Test void linkedAlexaRequestGetsOwnPrincipal() throws Exception {
        ServiceAuthenticationFilter filter = new ServiceAuthenticationFilter(ADMIN,TENANT,ALEXA);
        MockHttpServletRequest request = new MockHttpServletRequest("GET","/alexa/skill/summary");
        request.addHeader("Authorization","Bearer "+ALEXA);
        request.addHeader("X-Alexa-User-Id","amzn1.ask.account.TEST12345678");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)->assertEquals("amzn1.ask.account.TEST12345678",
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal()));
        assertEquals(200,response.getStatus());
    }
}
