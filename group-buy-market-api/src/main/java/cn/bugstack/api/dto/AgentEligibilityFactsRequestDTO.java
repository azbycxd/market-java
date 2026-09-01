package cn.bugstack.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

import java.io.Serializable;

/** Request contract for user-scoped eligibility facts; identity is deliberately absent. */
@Data
public class AgentEligibilityFactsRequestDTO implements Serializable {

    private static final long serialVersionUID = -6087613536690912258L;

    private Long activityId;

    @JsonAnySetter
    public void rejectUnexpectedField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("Unsupported request field: " + fieldName);
    }

}
