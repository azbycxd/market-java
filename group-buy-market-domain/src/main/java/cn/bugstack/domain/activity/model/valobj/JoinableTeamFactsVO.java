package cn.bugstack.domain.activity.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * Read-only, privacy-safe domain projection for Agent candidate team queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinableTeamFactsVO {

    private Long activityId;
    private List<CandidateTeam> candidateTeams;
    private TeamStatisticVO statistics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateTeam {
        private String teamId;
        private Integer targetCount;
        private Integer completeCount;
        private Integer lockCount;
        private Date validEndTime;
    }

}
