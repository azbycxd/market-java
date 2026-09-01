package cn.bugstack.domain.activity.service.facts;

import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.valobj.EligibilityFactsVO;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityFactsSourceVO;
import cn.bugstack.domain.activity.service.IAgentEligibilityFactsService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Reads individual eligibility constraints without calculating or returning a combined eligibility verdict.
 */
@Service
public class AgentEligibilityFactsService implements IAgentEligibilityFactsService {

    private final IActivityRepository activityRepository;

    public AgentEligibilityFactsService(IActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    @Override
    public EligibilityFactsVO getEligibilityFacts(String authenticatedUserId, Long activityId) {
        GroupBuyActivityFactsSourceVO activity = activityRepository
                .queryGroupBuyActivityFactsSourceByActivityId(activityId);
        if (null == activity) return null;

        boolean tagRuleConfigured = StringUtils.isNotBlank(activity.getTagId());
        boolean tagCrowdDataAvailable = tagRuleConfigured
                && activityRepository.isTagCrowdDataAvailable(activity.getTagId());
        boolean tagGatePassed = !tagRuleConfigured
                || activityRepository.isTagCrowdRange(activity.getTagId(), authenticatedUserId);

        GroupBuyActivityDiscountVO tagScopeRule = GroupBuyActivityDiscountVO.builder()
                .tagScope(activity.getTagScope())
                .build();
        boolean tagVisibilityAllowed = !tagRuleConfigured || tagScopeRule.isVisible() || tagGatePassed;
        boolean tagParticipationAllowed = !tagRuleConfigured || tagScopeRule.isEnable() || tagGatePassed;

        Integer userTakeCount = activityRepository.queryOrderCountByActivityId(activityId, authenticatedUserId);
        Integer userTakeLimit = activity.getUserTakeLimit();
        boolean participationLimitReached = null != userTakeLimit && userTakeCount >= userTakeLimit;

        return EligibilityFactsVO.builder()
                .activityId(activity.getActivityId())
                .tagRuleConfigured(tagRuleConfigured)
                .tagCrowdDataAvailable(tagCrowdDataAvailable)
                .tagGatePassed(tagGatePassed)
                .tagVisibilityAllowed(tagVisibilityAllowed)
                .tagParticipationAllowed(tagParticipationAllowed)
                .userTakeCount(userTakeCount)
                .userTakeLimit(userTakeLimit)
                .participationLimitReached(participationLimitReached)
                .marketDowngraded(activityRepository.downgradeSwitch())
                .userWithinReleaseRange(activityRepository.cutRange(authenticatedUserId))
                .build();
    }

}
