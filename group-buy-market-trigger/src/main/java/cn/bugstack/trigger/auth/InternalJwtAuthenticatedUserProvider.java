package cn.bugstack.trigger.auth;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * Primary Agent API identity provider. It trusts only the request attribute written by the
 * verified internal-JWT filter, never a caller-supplied HTTP header.
 */
@Primary
@Component
public class InternalJwtAuthenticatedUserProvider implements AuthenticatedUserProvider {

    @Override
    public Optional<String> getAuthenticatedUserId() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes)) {
            return Optional.empty();
        }
        HttpServletRequest request = ((ServletRequestAttributes) attributes).getRequest();
        Object value = request.getAttribute(AgentInternalJwtAuthenticationFilter.AUTHENTICATED_USER_ID_ATTRIBUTE);
        if (!(value instanceof String) || StringUtils.isBlank((String) value)) {
            return Optional.empty();
        }
        return Optional.of(((String) value).trim());
    }

}
