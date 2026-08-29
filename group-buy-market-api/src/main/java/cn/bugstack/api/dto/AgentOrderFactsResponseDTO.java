package cn.bugstack.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * Safe, read-only business facts for one authenticated user's order.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentOrderFactsResponseDTO implements Serializable {

    private static final long serialVersionUID = 4573814755510668363L;

    private OrderFacts order;
    private TeamFacts team;
    private ActivityFacts activity;
    private References references;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderFacts implements Serializable {
        private static final long serialVersionUID = 1L;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamFacts implements Serializable {
        private static final long serialVersionUID = 1L;
        private String status;
        private Integer targetCount;
        private Integer lockCount;
        private Integer completeCount;
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Shanghai")
        private Date validEndTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityFacts implements Serializable {
        private static final long serialVersionUID = 1L;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class References implements Serializable {
        private static final long serialVersionUID = 1L;
        private String teamId;
        private Long activityId;
    }

}
