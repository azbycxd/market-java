package cn.bugstack.trigger.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.JwtException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fail-closed service authentication for the Agent-only HTTP namespace.
 * User identity remains a separate trusted-header concern in AuthenticatedUserProvider.
 */
@Component
public class AgentInternalJwtAuthenticationFilter extends OncePerRequestFilter {

    static final String SECRET_ENV = "AGENT_INTERNAL_JWT_SECRET";
    static final String ISSUER_ENV = "AGENT_INTERNAL_JWT_ISSUER";
    static final String AUDIENCE_ENV = "AGENT_INTERNAL_JWT_AUDIENCE";

    private static final String AGENT_API_PREFIX = "/api/v1/agent/";
    private static final String BEARER_PREFIX = "Bearer ";
    private final Environment environment;
    private final ObjectMapper objectMapper;

    public AgentInternalJwtAuthenticationFilter(Environment environment, ObjectMapper objectMapper) {
        this.environment = environment;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(AGENT_API_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.isBlank(authorization) || !authorization.startsWith(BEARER_PREFIX)
                || !isValid(authorization.substring(BEARER_PREFIX.length()).trim())) {
            writeUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isValid(String token) {
        String secret = environment.getProperty(SECRET_ENV);
        String issuer = environment.getProperty(ISSUER_ENV);
        String audience = environment.getProperty(AUDIENCE_ENV);
        if (StringUtils.isAnyBlank(token, secret, issuer, audience)) {
            return false;
        }

        try {
            Jws<Claims> parsed = Jwts.parser()
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .setSigningKey(secret.getBytes(StandardCharsets.UTF_8))
                    .parseClaimsJws(token);
            if (!SignatureAlgorithm.HS256.getValue().equals(parsed.getHeader().getAlgorithm())) {
                return false;
            }
            Date expiration = parsed.getBody().getExpiration();
            return null != expiration && expiration.after(new Date());
        } catch (JwtException | IllegalArgumentException ignored) {
            return false;
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "UNAUTHORIZED");
        body.put("info", "内部服务认证失败");
        body.put("data", null);
        objectMapper.writeValue(response.getWriter(), body);
    }

}
