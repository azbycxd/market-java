package cn.bugstack.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * Deliberately narrow, read-only response for activity facts.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentActivityFactsResponseDTO implements Serializable {

    private static final long serialVersionUID = 3773594241210536251L;

    private Activity activity;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Activity implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long activityId;
        private String status;
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Shanghai")
        private Date startTime;
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Shanghai")
        private Date endTime;
        private String tagScope;
        private Integer userTakeLimit;
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX", timezone = "Asia/Shanghai")
        private Date evaluatedAt;
        private Boolean withinValidTime;
    }

}
