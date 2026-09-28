package cn.bugstack.test.agent;

import cn.bugstack.domain.demo.model.valobj.DemoResetResultVO;
import cn.bugstack.domain.demo.service.IDemoResetService;
import cn.bugstack.trigger.auth.AuthenticatedUserProvider;
import cn.bugstack.trigger.http.AgentDemoResetController;
import org.junit.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AgentDemoResetProfileTest {

    @Test
    public void shouldReturn404OutsideDemoProfile() throws Exception {
        try (AnnotationConfigWebApplicationContext context = context("test")) {
            MockMvcBuilders.webAppContextSetup(context).build()
                    .perform(post("/api/v1/agent/demo/reset"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    public void shouldRegisterRouteInDemoProfile() throws Exception {
        try (AnnotationConfigWebApplicationContext context = context("demo")) {
            MockMvcBuilders.webAppContextSetup(context).build()
                    .perform(post("/api/v1/agent/demo/reset"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0000"));
        }
    }

    private AnnotationConfigWebApplicationContext context(String profile) {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().setActiveProfiles(profile);
        context.register(WebConfig.class, AgentDemoResetController.class);
        context.refresh();
        return context;
    }

    @Configuration
    @EnableWebMvc
    static class WebConfig {
        @Bean
        IDemoResetService demoResetService() {
            IDemoResetService service = mock(IDemoResetService.class);
            when(service.reset("demo_user")).thenReturn(DemoResetResultVO.builder()
                    .reset(true).blocked(false).orderCount(4).teamCount(4)
                    .redisKeysDeleted(0L).redisResetStrategy("TARGETED_DEMO_KEYS").build());
            return service;
        }

        @Bean
        AuthenticatedUserProvider authenticatedUserProvider() {
            AuthenticatedUserProvider provider = mock(AuthenticatedUserProvider.class);
            when(provider.getAuthenticatedUserId()).thenReturn(Optional.of("demo_user"));
            return provider;
        }
    }

}
