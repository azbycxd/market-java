package cn.bugstack.test.agent;

import cn.bugstack.api.dto.AgentOrderFactsRequestDTO;
import cn.bugstack.trigger.auth.DevHeaderAuthenticatedUserProvider;
import org.junit.After;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class DevHeaderAuthenticatedUserProviderTest {

    @After
    public void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    public void shouldReadHeaderOnlyWithDevOrTestProfileAndExplicitOptIn() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(DevHeaderAuthenticatedUserProvider.HEADER_NAME, " user-1 ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        MockEnvironment testEnvironment = new MockEnvironment();
        testEnvironment.setActiveProfiles("test");
        testEnvironment.setProperty("agent.facts.dev-header-auth.enabled", "true");
        assertEquals("user-1", new DevHeaderAuthenticatedUserProvider(testEnvironment)
                .getAuthenticatedUserId().orElse(null));

        MockEnvironment productionEnvironment = new MockEnvironment();
        productionEnvironment.setActiveProfiles("prod");
        productionEnvironment.setProperty("agent.facts.dev-header-auth.enabled", "true");
        assertFalse(new DevHeaderAuthenticatedUserProvider(productionEnvironment)
                .getAuthenticatedUserId().isPresent());
    }

    @Test
    public void requestDtoDoesNotContainUserIdentity() {
        for (Field field : AgentOrderFactsRequestDTO.class.getDeclaredFields()) {
            assertFalse("userId must stay outside the request DTO", "userId".equals(field.getName()));
        }
    }

}
