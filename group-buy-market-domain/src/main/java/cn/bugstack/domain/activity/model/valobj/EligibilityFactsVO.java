package cn.bugstack.domain.activity.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Raw and deterministic eligibility observations, without a combined eligibility conclusion. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EligibilityFactsVO {

    private Long activityId;
    private Boolean tagRuleConfigured;
    private Boolean tagCrowdDataAvailable;
    private Boolean tagGatePassed;
    private Boolean tagVisibilityAllowed;
    private Boolean tagParticipationAllowed;
    private Integer userTakeCount;
    private Integer userTakeLimit;
    private Boolean participationLimitReached;
    private Boolean marketDowngraded;
    private Boolean userWithinReleaseRange;

}
