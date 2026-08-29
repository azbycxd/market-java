package cn.bugstack.domain.activity.service.facts;

import cn.bugstack.domain.activity.model.entity.UserGroupBuyOrderDetailEntity;
import cn.bugstack.domain.activity.model.valobj.JoinableTeamFactsVO;
import cn.bugstack.domain.activity.model.valobj.TeamStatisticVO;
import cn.bugstack.domain.activity.service.IAgentJoinableTeamFactsService;
import cn.bugstack.domain.activity.service.IIndexGroupBuyMarketService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reuses the established activity read capability and projects only the fields safe for Agent use.
 */
@Service
public class AgentJoinableTeamFactsService implements IAgentJoinableTeamFactsService {

    /** Existing activity read code fetches a small pool and randomly selects this many candidates. */
    private static final int CANDIDATE_TEAM_LIMIT = 2;

    private final IIndexGroupBuyMarketService indexGroupBuyMarketService;

    public AgentJoinableTeamFactsService(IIndexGroupBuyMarketService indexGroupBuyMarketService) {
        this.indexGroupBuyMarketService = indexGroupBuyMarketService;
    }

    @Override
    public JoinableTeamFactsVO getJoinableTeamFacts(String authenticatedUserId, Long activityId) {
        List<UserGroupBuyOrderDetailEntity> candidates = indexGroupBuyMarketService
                .queryInProgressUserGroupBuyOrderDetailList(activityId, authenticatedUserId, 0, CANDIDATE_TEAM_LIMIT);
        TeamStatisticVO statistics = indexGroupBuyMarketService.queryTeamStatisticByActivityId(activityId);

        List<JoinableTeamFactsVO.CandidateTeam> safeCandidates = null == candidates
                ? Collections.emptyList()
                : candidates.stream()
                .map(candidate -> JoinableTeamFactsVO.CandidateTeam.builder()
                        .teamId(candidate.getTeamId())
                        .targetCount(candidate.getTargetCount())
                        .completeCount(candidate.getCompleteCount())
                        .lockCount(candidate.getLockCount())
                        .validEndTime(candidate.getValidEndTime())
                        .build())
                .collect(Collectors.toList());

        return JoinableTeamFactsVO.builder()
                .activityId(activityId)
                .candidateTeams(safeCandidates)
                .statistics(statistics)
                .build();
    }

}
