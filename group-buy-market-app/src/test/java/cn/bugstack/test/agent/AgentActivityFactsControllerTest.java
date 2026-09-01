package cn.bugstack.test.agent;

import cn.bugstack.domain.activity.model.valobj.ActivityFactsVO;
import cn.bugstack.domain.activity.service.IAgentActivityFactsService;
import cn.bugstack.trigger.auth.AuthenticatedUserProvider;
import cn.bugstack.trigger.http.AgentActivityFactsController;
import cn.bugstack.types.enums.ActivityStatusEnumVO;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Date;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AgentActivityFactsControllerTest {

    private IAgentActivityFactsService activityFactsService;
    private AuthenticatedUserProvider authenticatedUserProvider;
    private MockMvc mockMvc;

    @Before
    public void setUp() {
        activityFactsService = mock(IAgentActivityFactsService.class);
        authenticatedUserProvider = mock(AuthenticatedUserProvider.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AgentActivityFactsController(
                activityFactsService, authenticatedUserProvider)).build();
    }

    @Test
    public void shouldReturnOnlyWhitelistedActivityFacts() throws Exception {
        authenticate();
        when(activityFactsService.getActivityFacts(1001L)).thenReturn(facts(ActivityStatusEnumVO.EFFECTIVE));

        MvcResult result = mockMvc.perform(post("/api/v1/agent/activity/facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.activity.activityId").value(1001))
                .andExpect(jsonPath("$.data.activity.status").value("EFFECTIVE"))
                .andExpect(jsonPath("$.data.activity.tagScope").value("tag-scope"))
                .andExpect(jsonPath("$.data.activity.userTakeLimit").value(2))
                .andExpect(jsonPath("$.data.activity.withinValidTime").value(true))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("groupType"));
        assertFalse(body.contains("validTime"));
        assertFalse(body.contains("target"));
        assertFalse(body.contains("source"));
        assertFalse(body.contains("channel"));
        assertFalse(body.contains("tagId"));
        assertFalse(body.contains("discountId"));
        assertFalse(body.contains("diagnosis"));
        assertFalse(body.contains("recommendation"));
        assertFalse(body.contains("eligible"));
    }

    @Test
    public void shouldReturnNonEffectiveStatusesAsFacts() throws Exception {
        authenticate();
        for (ActivityStatusEnumVO statusValue : new ActivityStatusEnumVO[]{
                ActivityStatusEnumVO.CREATE, ActivityStatusEnumVO.OVERDUE, ActivityStatusEnumVO.ABANDONED}) {
            when(activityFactsService.getActivityFacts(1001L)).thenReturn(facts(statusValue));
            mockMvc.perform(post("/api/v1/agent/activity/facts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"activityId\":1001}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0000"))
                    .andExpect(jsonPath("$.data.activity.status").value(statusValue.name()));
        }
    }

    @Test
    public void shouldRequireAuthenticatedUserBeforeCallingReadService() throws Exception {
        when(authenticatedUserProvider.getAuthenticatedUserId()).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/agent/activity/facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        verifyNoInteractions(activityFactsService);
    }

    @Test
    public void shouldRejectMissingInvalidOrAdditionalFieldsBeforeAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/agent/activity/facts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        mockMvc.perform(post("/api/v1/agent/activity/facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        mockMvc.perform(post("/api/v1/agent/activity/facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1001,\"userId\":\"other-user\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        mockMvc.perform(post("/api/v1/agent/activity/facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1001,\"token\":\"forbidden\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

        verifyNoInteractions(authenticatedUserProvider, activityFactsService);
    }

    @Test
    public void shouldReturnSafeNotFoundCode() throws Exception {
        authenticate();
        when(activityFactsService.getActivityFacts(404L)).thenReturn(null);

        mockMvc.perform(post("/api/v1/agent/activity/facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":404}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ACTIVITY_NOT_FOUND"));
    }

    @Test
    public void shouldHideUnexpectedExceptionDetails() throws Exception {
        authenticate();
        when(activityFactsService.getActivityFacts(1001L))
                .thenThrow(new IllegalStateException("raw activity mapper details"));

        MvcResult result = mockMvc.perform(post("/api/v1/agent/activity/facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVICE_ERROR"))
                .andReturn();

        assertFalse(result.getResponse().getContentAsString().contains("raw activity mapper details"));
    }

    private void authenticate() {
        when(authenticatedUserProvider.getAuthenticatedUserId()).thenReturn(Optional.of("trusted-user"));
    }

    private ActivityFactsVO facts(ActivityStatusEnumVO statusValue) {
        return ActivityFactsVO.builder()
                .activityId(1001L)
                .status(statusValue)
                .startTime(new Date(1767225600000L))
                .endTime(new Date(1767312000000L))
                .tagScope("tag-scope")
                .userTakeLimit(2)
                .evaluatedAt(new Date(1767229200000L))
                .withinValidTime(true)
                .build();
    }

}
