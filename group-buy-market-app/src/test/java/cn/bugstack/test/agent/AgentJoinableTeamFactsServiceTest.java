package cn.bugstack.test.agent;

import cn.bugstack.domain.activity.model.entity.UserGroupBuyOrderDetailEntity;
import cn.bugstack.domain.activity.model.valobj.JoinableTeamFactsVO;
import cn.bugstack.domain.activity.model.valobj.TeamStatisticVO;
import cn.bugstack.domain.activity.service.IIndexGroupBuyMarketService;
import cn.bugstack.domain.activity.service.facts.AgentJoinableTeamFactsService;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/** Unit tests only: the Agent service delegates solely to established activity read methods. */
public class AgentJoinableTeamFactsServiceTest {

    private IIndexGroupBuyMarketService indexGroupBuyMarketService;
    private AgentJoinableTeamFactsService service;

    @Before
    public void setUp() {
        indexGroupBuyMarketService = mock(IIndexGroupBuyMarketService.class);
        service = new AgentJoinableTeamFactsService(indexGroupBuyMarketService);
    }

    @Test
    public void shouldProjectOnlyCandidateTeamFactsFromExistingReadService() {
        Date validEndTime = new Date(1767225600000L);
        when(indexGroupBuyMarketService.queryInProgressUserGroupBuyOrderDetailList(1001L, "user-1", 0, 2))
                .thenReturn(Arrays.asList(UserGroupBuyOrderDetailEntity.builder()
                        .userId("other-user")
                        .outTradeNo("other-users-trade")
                        .teamId("team-1")
                        .activityId(1001L)
                        .targetCount(3)
                        .completeCount(1)
                        .lockCount(1)
                        .validEndTime(validEndTime)
                        .build()));
        TeamStatisticVO statistics = new TeamStatisticVO(4, 1, 7);
        when(indexGroupBuyMarketService.queryTeamStatisticByActivityId(1001L)).thenReturn(statistics);

        JoinableTeamFactsVO facts = service.getJoinableTeamFacts("user-1", 1001L);

        assertEquals(Long.valueOf(1001L), facts.getActivityId());
        assertEquals(1, facts.getCandidateTeams().size());
        JoinableTeamFactsVO.CandidateTeam candidate = facts.getCandidateTeams().get(0);
        assertEquals("team-1", candidate.getTeamId());
        assertEquals(Integer.valueOf(3), candidate.getTargetCount());
        assertEquals(Integer.valueOf(1), candidate.getCompleteCount());
        assertEquals(Integer.valueOf(1), candidate.getLockCount());
        assertEquals(validEndTime, candidate.getValidEndTime());
        assertEquals(statistics, facts.getStatistics());
        verify(indexGroupBuyMarketService).queryInProgressUserGroupBuyOrderDetailList(1001L, "user-1", 0, 2);
        verify(indexGroupBuyMarketService).queryTeamStatisticByActivityId(1001L);
        verifyNoMoreInteractions(indexGroupBuyMarketService);
    }

    @Test
    public void shouldTreatNoCandidatesAsAValidEmptyResult() {
        when(indexGroupBuyMarketService.queryInProgressUserGroupBuyOrderDetailList(1001L, "user-1", 0, 2))
                .thenReturn(Collections.emptyList());
        when(indexGroupBuyMarketService.queryTeamStatisticByActivityId(1001L))
                .thenReturn(new TeamStatisticVO(0, 0, 0));

        JoinableTeamFactsVO facts = service.getJoinableTeamFacts("user-1", 1001L);

        assertTrue(facts.getCandidateTeams().isEmpty());
        assertEquals(Integer.valueOf(0), facts.getStatistics().getAllTeamCount());
        assertEquals(Integer.valueOf(0), facts.getStatistics().getAllTeamCompleteCount());
        assertEquals(Integer.valueOf(0), facts.getStatistics().getAllTeamUserCount());
        verify(indexGroupBuyMarketService).queryInProgressUserGroupBuyOrderDetailList(1001L, "user-1", 0, 2);
        verify(indexGroupBuyMarketService).queryTeamStatisticByActivityId(1001L);
        verifyNoMoreInteractions(indexGroupBuyMarketService);
    }

}
