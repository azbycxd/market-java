package cn.bugstack.test.agent;

import cn.bugstack.domain.activity.model.valobj.JoinableTeamFactsVO;
import cn.bugstack.domain.activity.model.valobj.TeamStatisticVO;
import cn.bugstack.domain.activity.service.IAgentJoinableTeamFactsService;
import cn.bugstack.trigger.auth.AuthenticatedUserProvider;
import cn.bugstack.trigger.http.AgentJoinableTeamFactsController;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AgentJoinableTeamFactsControllerTest {

    private IAgentJoinableTeamFactsService joinableTeamFactsService;
    private AuthenticatedUserProvider authenticatedUserProvider;
    private MockMvc mockMvc;

    @Before
    public void setUp() {
        joinableTeamFactsService = mock(IAgentJoinableTeamFactsService.class);
        authenticatedUserProvider = mock(AuthenticatedUserProvider.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AgentJoinableTeamFactsController(
                joinableTeamFactsService, authenticatedUserProvider)).build();
    }

    @Test
    public void shouldReturnOnlyWhitelistedCandidateFields() throws Exception {
        when(authenticatedUserProvider.getAuthenticatedUserId()).thenReturn(Optional.of("user-1"));
        when(joinableTeamFactsService.getJoinableTeamFacts("user-1", 1001L))
                .thenReturn(factsWithOneCandidate());

        MvcResult result = mockMvc.perform(post("/api/v1/agent/team/joinable-facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.activityId").value(1001))
                .andExpect(jsonPath("$.data.candidateTeams[0].teamId").value("team-1"))
                .andExpect(jsonPath("$.data.candidateTeams[0].targetCount").value(3))
                .andExpect(jsonPath("$.data.candidateTeams[0].completeCount").value(1))
                .andExpect(jsonPath("$.data.candidateTeams[0].lockCount").value(1))
                .andExpect(jsonPath("$.data.statistics.allTeamCount").value(4))
                .andExpect(jsonPath("$.data.statistics.allTeamCompleteCount").value(1))
                .andExpect(jsonPath("$.data.statistics.allTeamUserCount").value(7))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("userId"));
        assertFalse(body.contains("outTradeNo"));
        assertFalse(body.contains("source"));
        assertFalse(body.contains("channel"));
        assertFalse(body.contains("notify"));
        verify(joinableTeamFactsService).getJoinableTeamFacts("user-1", 1001L);
    }

    @Test
    public void shouldRequireAuthenticationBeforeCallingReadService() throws Exception {
        when(authenticatedUserProvider.getAuthenticatedUserId()).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/agent/team/joinable-facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        verifyNoInteractions(joinableTeamFactsService);
    }

    @Test
    public void shouldRejectMissingInvalidOrAdditionalRequestFieldsBeforeAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/agent/team/joinable-facts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

        mockMvc.perform(post("/api/v1/agent/team/joinable-facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

        mockMvc.perform(post("/api/v1/agent/team/joinable-facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1001,\"userId\":\"other-user\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

        verifyNoInteractions(authenticatedUserProvider, joinableTeamFactsService);
    }

    @Test
    public void shouldReturnSuccessfulEmptyCandidates() throws Exception {
        when(authenticatedUserProvider.getAuthenticatedUserId()).thenReturn(Optional.of("user-1"));
        when(joinableTeamFactsService.getJoinableTeamFacts("user-1", 1001L))
                .thenReturn(JoinableTeamFactsVO.builder()
                        .activityId(1001L)
                        .candidateTeams(Collections.emptyList())
                        .statistics(new TeamStatisticVO(0, 0, 0))
                        .build());

        mockMvc.perform(post("/api/v1/agent/team/joinable-facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.candidateTeams").isArray())
                .andExpect(jsonPath("$.data.candidateTeams").isEmpty())
                .andExpect(jsonPath("$.data.statistics.allTeamCount").value(0));
    }

    @Test
    public void shouldHideUnexpectedExceptionDetails() throws Exception {
        when(authenticatedUserProvider.getAuthenticatedUserId()).thenReturn(Optional.of("user-1"));
        when(joinableTeamFactsService.getJoinableTeamFacts("user-1", 1001L))
                .thenThrow(new IllegalStateException("internal activity repository detail"));

        MvcResult result = mockMvc.perform(post("/api/v1/agent/team/joinable-facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":1001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVICE_ERROR"))
                .andReturn();

        assertFalse(result.getResponse().getContentAsString().contains("internal activity repository detail"));
    }

    private JoinableTeamFactsVO factsWithOneCandidate() {
        return JoinableTeamFactsVO.builder()
                .activityId(1001L)
                .candidateTeams(Arrays.asList(JoinableTeamFactsVO.CandidateTeam.builder()
                        .teamId("team-1")
                        .targetCount(3)
                        .completeCount(1)
                        .lockCount(1)
                        .validEndTime(new Date(1767225600000L))
                        .build()))
                .statistics(new TeamStatisticVO(4, 1, 7))
                .build();
    }

}
