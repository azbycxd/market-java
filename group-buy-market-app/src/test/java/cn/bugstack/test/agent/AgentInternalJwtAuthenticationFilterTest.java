package cn.bugstack.test.agent;

import cn.bugstack.trigger.auth.AgentInternalJwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AgentInternalJwtAuthenticationFilterTest {

    private static final String SECRET = "b3-3-test-secret-with-at-least-thirty-two-bytes";
    private static final String ISSUER = "group-buy-agent";
    private static final String AUDIENCE = "group-buy-market";
    private AgentInternalJwtAuthenticationFilter filter;

    @Before
    public void setUp() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("AGENT_INTERNAL_JWT_SECRET", SECRET);
        environment.setProperty("AGENT_INTERNAL_JWT_ISSUER", ISSUER);
        environment.setProperty("AGENT_INTERNAL_JWT_AUDIENCE", AUDIENCE);
        filter = new AgentInternalJwtAuthenticationFilter(environment, new ObjectMapper());
    }

    @Test
    public void shouldAllowValidHs256TokenForAgentApi() throws Exception {
        MockFilterChain chain = invokeAgentApi(token(ISSUER, AUDIENCE, new Date(System.currentTimeMillis() + 60000L)));

        assertNotNull(chain.getRequest());
    }

    @Test
    public void shouldRejectMissingToken() throws Exception {
        MockHttpServletResponse response = invokeWithoutToken();

        assertUnauthorized(response);
    }

    @Test
    public void shouldRejectWrongSignatureToken() throws Exception {
        String invalid = Jwts.builder().setIssuer(ISSUER).setAudience(AUDIENCE)
                .setExpiration(new Date(System.currentTimeMillis() + 60000L))
                .signWith(SignatureAlgorithm.HS256, "another-b3-3-test-secret-with-at-least-thirty-two-bytes"
                        .getBytes(StandardCharsets.UTF_8)).compact();

        assertUnauthorized(invokeAgentApiResponse(invalid));
    }

    @Test
    public void shouldRejectExpiredToken() throws Exception {
        assertUnauthorized(invokeAgentApiResponse(token(ISSUER, AUDIENCE,
                new Date(System.currentTimeMillis() - 60000L))));
    }

    @Test
    public void shouldRejectIssuerMismatch() throws Exception {
        assertUnauthorized(invokeAgentApiResponse(token("another-issuer", AUDIENCE,
                new Date(System.currentTimeMillis() + 60000L))));
    }

    @Test
    public void shouldRejectAudienceMismatch() throws Exception {
        assertUnauthorized(invokeAgentApiResponse(token(ISSUER, "another-audience",
                new Date(System.currentTimeMillis() + 60000L))));
    }

    @Test
    public void shouldRejectAgentRequestWhenRequiredEnvironmentValuesAreMissing() throws Exception {
        AgentInternalJwtAuthenticationFilter missingConfigFilter = new AgentInternalJwtAuthenticationFilter(
                new MockEnvironment(), new ObjectMapper());
        MockHttpServletRequest request = agentRequest(token(ISSUER, AUDIENCE,
                new Date(System.currentTimeMillis() + 60000L)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        missingConfigFilter.doFilter(request, response, new MockFilterChain());

        assertUnauthorized(response);
    }

    @Test
    public void shouldNotProtectExistingNonAgentApi() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    private MockFilterChain invokeAgentApi(String jwt) throws Exception {
        MockHttpServletRequest request = agentRequest(jwt);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertEquals(200, response.getStatus());
        return chain;
    }

    private MockHttpServletResponse invokeAgentApiResponse(String jwt) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(agentRequest(jwt), response, new MockFilterChain());
        return response;
    }

    private MockHttpServletResponse invokeWithoutToken() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/agent/order/facts"), response,
                new MockFilterChain());
        return response;
    }

    private MockHttpServletRequest agentRequest(String jwt) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/agent/order/facts");
        request.addHeader("Authorization", "Bearer " + jwt);
        return request;
    }

    private String token(String issuer, String audience, Date expiration) {
        return Jwts.builder().setIssuer(issuer).setAudience(audience).setExpiration(expiration)
                .signWith(SignatureAlgorithm.HS256, SECRET.getBytes(StandardCharsets.UTF_8)).compact();
    }

    private void assertUnauthorized(MockHttpServletResponse response) throws Exception {
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("UNAUTHORIZED"));
    }

}
