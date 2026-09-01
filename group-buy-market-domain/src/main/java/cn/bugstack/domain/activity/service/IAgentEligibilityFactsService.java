package cn.bugstack.domain.activity.service;

import cn.bugstack.domain.activity.model.valobj.EligibilityFactsVO;

/** Agent-facing, read-only service for individual activity constraints. */
public interface IAgentEligibilityFactsService {

    EligibilityFactsVO getEligibilityFacts(String authenticatedUserId, Long activityId);

}
