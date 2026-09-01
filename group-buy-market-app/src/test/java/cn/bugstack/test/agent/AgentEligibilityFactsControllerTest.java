package cn.bugstack.test.agent;

import cn.bugstack.domain.activity.model.valobj.EligibilityFactsVO;
import cn.bugstack.domain.activity.service.IAgentEligibilityFactsService;
import cn.bugstack.trigger.auth.AuthenticatedUserProvider;
import cn.bugstack.trigger.http.AgentEligibilityFactsController;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AgentEligibilityFactsControllerTest {

    private IAgentEligibilityFactsService eligibilityFactsService;
    private AuthenticatedUserProvider authenticatedUserProvider;
    private MockMvc mockMvc;

    @Before
    public void setUp() {
        eligibilityFactsService = mock(IAgentEligibilityFactsService.class);
        authenticatedUserProvider = mock(AuthenticatedUserProvider.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AgentEligibilityFactsController(
                eligibilityFactsService, authenticatedUserProvider)).build();
    }

    @Test
    public void shouldReturnOnlyEligibilityFactsWithoutIdentityOrDiagnosis() throws Exception {
        when(authenticatedUserProvider.getAuthenticatedUserId()).thenReturn(Optional.of("trusted-user"));
        when(eligibilityFactsService.getEligibilityFacts("trusted-user", 1001L)).thenReturn(facts());

        MvcResult result = mockMvc.perform(post("/api/v1/agent/activity/eligibility-facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.activityId").value(1001))
                .andExpect(jsonPath("$.data.tagCrowdDataAvailable").value(true))
                .andExpect(jsonPath("$.data.tagGatePassed").value(true))
                .andExpect(jsonPath("$.data.participationLimitReached").value(false))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("trusted-user"));
        assertFalse(body.contains("userId"));
        assertFalse(body.contains("tagId"));
        assertFalse(body.contains("redis"));
        assertFalse(body.contains("eligible"));
        assertFalse(body.contains("diagnosis"));
        assertFalse(body.contains("rootCause"));
        assertFalse(body.contains("recommendation"));
        assertFalse(body.contains("reasonCode"));
    }

    @Test
    public void shouldRequireTrustedIdentityBeforeServiceCall() throws Exception {
        when(authenticatedUserProvider.getAuthenticatedUserId()).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/agent/activity/eligibility-facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        verifyNoInteractions(eligibilityFactsService);
    }

    @Test
    public void shouldRejectInvalidAndInjectedRequestFieldsBeforeAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/agent/activity/eligibility-facts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        mockMvc.perform(post("/api/v1/agent/activity/eligibility-facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":0}"))
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        mockMvc.perform(post("/api/v1/agent/activity/eligibility-facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1001,\"userId\":\"other-user\"}"))
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        mockMvc.perform(post("/api/v1/agent/activity/eligibility-facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1001,\"token\":\"forbidden\"}"))
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
        mockMvc.perform(post("/api/v1/agent/activity/eligibility-facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1001,\"redisKey\":\"forbidden\"}"))
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

        verifyNoInteractions(authenticatedUserProvider, eligibilityFactsService);
    }

    @Test
    public void shouldReturnSafeNotFoundCode() throws Exception {
        when(authenticatedUserProvider.getAuthenticatedUserId()).thenReturn(Optional.of("trusted-user"));
        when(eligibilityFactsService.getEligibilityFacts("trusted-user", 404L)).thenReturn(null);

        mockMvc.perform(post("/api/v1/agent/activity/eligibility-facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":404}"))
                .andExpect(jsonPath("$.code").value("ACTIVITY_NOT_FOUND"));
    }

    @Test
    public void shouldHideUnexpectedExceptionDetails() throws Exception {
        when(authenticatedUserProvider.getAuthenticatedUserId()).thenReturn(Optional.of("trusted-user"));
        when(eligibilityFactsService.getEligibilityFacts("trusted-user", 1001L))
                .thenThrow(new IllegalStateException("internal eligibility detail"));

        MvcResult result = mockMvc.perform(post("/api/v1/agent/activity/eligibility-facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1001}"))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVICE_ERROR"))
                .andReturn();

        assertFalse(result.getResponse().getContentAsString().contains("internal eligibility detail"));
    }

    private EligibilityFactsVO facts() {
        return EligibilityFactsVO.builder()
                .activityId(1001L)
                .tagRuleConfigured(true)
                .tagCrowdDataAvailable(true)
                .tagGatePassed(true)
                .tagVisibilityAllowed(true)
                .tagParticipationAllowed(true)
                .userTakeCount(1)
                .userTakeLimit(3)
                .participationLimitReached(false)
                .marketDowngraded(false)
                .userWithinReleaseRange(true)
                .build();
    }

}
