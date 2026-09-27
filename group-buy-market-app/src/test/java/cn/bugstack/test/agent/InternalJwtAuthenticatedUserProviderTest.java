package cn.bugstack.test.agent;

import cn.bugstack.trigger.auth.AgentInternalJwtAuthenticationFilter;
import cn.bugstack.trigger.auth.InternalJwtAuthenticatedUserProvider;
import org.junit.After;
import org.junit.Test;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class InternalJwtAuthenticatedUserProviderTest {

    @After
    public void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void shouldReadVerifiedJwtSubjectEvenWhenHeaderAttemptsToSpoofIt() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AgentInternalJwtAuthenticationFilter.AUTHENTICATED_USER_ID_ATTRIBUTE, "xfg05");
        request.addHeader("X-Authenticated-User-Id", "xfg03");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertEquals("xfg05", new InternalJwtAuthenticatedUserProvider()
                .getAuthenticatedUserId().orElse(null));
    }

    @Test
    public void shouldRejectRequestWithoutVerifiedJwtAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Authenticated-User-Id", "xfg03");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        assertFalse(new InternalJwtAuthenticatedUserProvider().getAuthenticatedUserId().isPresent());
    }

    @Test
    public void shouldBeThePrimaryAuthenticatedUserProvider() {
        assertNotNull(InternalJwtAuthenticatedUserProvider.class.getAnnotation(Primary.class));
    }

}
