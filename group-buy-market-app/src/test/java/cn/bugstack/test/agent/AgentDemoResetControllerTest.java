package cn.bugstack.test.agent;

import cn.bugstack.domain.demo.model.valobj.DemoResetResultVO;
import cn.bugstack.domain.demo.service.IDemoResetService;
import cn.bugstack.trigger.auth.AuthenticatedUserProvider;
import cn.bugstack.trigger.http.AgentDemoResetController;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AgentDemoResetControllerTest {

    private IDemoResetService demoResetService;
    private AuthenticatedUserProvider authenticatedUserProvider;
    private MockMvc mockMvc;

    @Before
    public void setUp() {
        demoResetService = mock(IDemoResetService.class);
        authenticatedUserProvider = mock(AuthenticatedUserProvider.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AgentDemoResetController(demoResetService, authenticatedUserProvider)).build();
    }

    @Test
    public void shouldResetForJwtDemoUserAndIgnoreSpoofHeader() throws Exception {
        when(authenticatedUserProvider.getAuthenticatedUserId()).thenReturn(Optional.of("demo_user"));
        when(demoResetService.reset("demo_user")).thenReturn(success(3L));

        mockMvc.perform(post("/api/v1/agent/demo/reset")
                        .header("X-Authenticated-User-Id", "other_user")
                        .param("userId", "other_user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.reset").value(true))
                .andExpect(jsonPath("$.data.orderCount").value(4))
                .andExpect(jsonPath("$.data.teamCount").value(4))
                .andExpect(jsonPath("$.data.redisKeysDeleted").value(3))
                .andExpect(jsonPath("$.data.redisResetStrategy").value("TARGETED_DEMO_KEYS"));

        verify(demoResetService).reset("demo_user");
    }

    @Test
    public void shouldRejectNonDemoUser() throws Exception {
        when(authenticatedUserProvider.getAuthenticatedUserId()).thenReturn(Optional.of("other_user"));

        mockMvc.perform(post("/api/v1/agent/demo/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("DEMO_RESET_FORBIDDEN"));

        verifyNoInteractions(demoResetService);
    }

    @Test
    public void shouldRejectCallerControlledUserId() throws Exception {
        mockMvc.perform(post("/api/v1/agent/demo/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"demo_user\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

        verifyNoInteractions(authenticatedUserProvider, demoResetService);
    }

    @Test
    public void shouldReturnBlockedWhenRefundIsProcessing() throws Exception {
        when(authenticatedUserProvider.getAuthenticatedUserId()).thenReturn(Optional.of("demo_user"));
        when(demoResetService.reset("demo_user")).thenReturn(DemoResetResultVO.builder()
                .reset(false).blocked(true).redisResetStrategy("TARGETED_DEMO_KEYS").build());

        mockMvc.perform(post("/api/v1/agent/demo/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("DEMO_RESET_BLOCKED"));
    }

    private DemoResetResultVO success(long deletedKeys) {
        return DemoResetResultVO.builder().reset(true).blocked(false).orderCount(4).teamCount(4)
                .redisKeysDeleted(deletedKeys).redisResetStrategy("TARGETED_DEMO_KEYS").build();
    }

}
