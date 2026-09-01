package cn.bugstack.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Privacy-safe raw and deterministic eligibility facts; it intentionally contains no diagnosis. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentEligibilityFactsResponseDTO implements Serializable {

    private static final long serialVersionUID = -8526149475918128919L;

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
