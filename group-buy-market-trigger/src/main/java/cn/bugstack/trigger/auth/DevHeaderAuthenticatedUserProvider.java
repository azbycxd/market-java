package cn.bugstack.trigger.auth;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Optional;

/**
 * DEV/TEST ONLY identity adapter. It never trusts the header unless both a dev/test profile and the
 * explicit opt-in property are active. Production must supply a real authentication adapter instead.
 */
@Component
public class DevHeaderAuthenticatedUserProvider implements AuthenticatedUserProvider {

    public static final String HEADER_NAME = "X-Dev-Authenticated-User-Id";
    private static final String ENABLED_PROPERTY = "agent.facts.dev-header-auth.enabled";

    private final Environment environment;

    public DevHeaderAuthenticatedUserProvider(Environment environment) {
        this.environment = environment;
    }

    @Override
    public Optional<String> getAuthenticatedUserId() {
        if (!isDevOrTestProfile() || !environment.getProperty(ENABLED_PROPERTY, Boolean.class, false)) {
            return Optional.empty();
        }

        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes)) {
            return Optional.empty();
        }

        HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
        String userId = request.getHeader(HEADER_NAME);
        return StringUtils.isBlank(userId) ? Optional.empty() : Optional.of(userId.trim());
    }

    private boolean isDevOrTestProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev")
                || Arrays.asList(environment.getActiveProfiles()).contains("test");
    }

}
