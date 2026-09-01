package cn.bugstack.domain.activity.service.facts;

import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.valobj.ActivityFactsVO;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityFactsSourceVO;
import cn.bugstack.domain.activity.service.IAgentActivityFactsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Date;

/**
 * Projects activity state without applying participation, eligibility, or diagnosis rules.
 */
@Service
public class AgentActivityFactsService implements IAgentActivityFactsService {

    private final IActivityRepository activityRepository;
    private final Clock clock;

    @Autowired
    public AgentActivityFactsService(IActivityRepository activityRepository) {
        this(activityRepository, Clock.systemDefaultZone());
    }

    public AgentActivityFactsService(IActivityRepository activityRepository, Clock clock) {
        this.activityRepository = activityRepository;
        this.clock = clock;
    }

    @Override
    public ActivityFactsVO getActivityFacts(Long activityId) {
        GroupBuyActivityFactsSourceVO source = activityRepository
                .queryGroupBuyActivityFactsSourceByActivityId(activityId);
        if (null == source) return null;

        Date evaluatedAt = Date.from(clock.instant());
        boolean withinValidTime = !evaluatedAt.before(source.getStartTime())
                && !evaluatedAt.after(source.getEndTime());

        return ActivityFactsVO.builder()
                .activityId(source.getActivityId())
                .status(source.getStatus())
                .startTime(source.getStartTime())
                .endTime(source.getEndTime())
                .tagScope(source.getTagScope())
                .userTakeLimit(source.getUserTakeLimit())
                .evaluatedAt(evaluatedAt)
                .withinValidTime(withinValidTime)
                .build();
    }

}
