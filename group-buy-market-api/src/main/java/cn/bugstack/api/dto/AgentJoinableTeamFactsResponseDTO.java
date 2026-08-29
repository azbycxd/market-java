package cn.bugstack.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * Safe projection of joinable teams. It intentionally omits candidate owner and trade identifiers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentJoinableTeamFactsResponseDTO implements Serializable {

    private static final long serialVersionUID = 4445373295126494664L;

    private Long activityId;
    private List<CandidateTeam> candidateTeams;
    private Statistics statistics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateTeam implements Serializable {
        private static final long serialVersionUID = 1L;

        private String teamId;
        private Integer targetCount;
        private Integer completeCount;
        private Integer lockCount;
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Shanghai")
        private Date validEndTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Statistics implements Serializable {
        private static final long serialVersionUID = 1L;

        private Integer allTeamCount;
        private Integer allTeamCompleteCount;
        private Integer allTeamUserCount;
    }

}
