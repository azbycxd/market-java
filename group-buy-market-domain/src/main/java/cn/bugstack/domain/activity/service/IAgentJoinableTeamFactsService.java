package cn.bugstack.domain.activity.service;

import cn.bugstack.domain.activity.model.valobj.JoinableTeamFactsVO;

/**
 * Agent-facing read service. The authenticated user is used only by the existing candidate filter.
 */
public interface IAgentJoinableTeamFactsService {

    JoinableTeamFactsVO getJoinableTeamFacts(String authenticatedUserId, Long activityId);

}
