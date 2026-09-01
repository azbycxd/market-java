package cn.bugstack.domain.activity.service;

import cn.bugstack.domain.activity.model.valobj.ActivityFactsVO;

/**
 * Agent-facing, read-only activity facts service.
 */
public interface IAgentActivityFactsService {

    ActivityFactsVO getActivityFacts(Long activityId);

}
