package cn.bugstack.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

import java.io.Serializable;

/**
 * The Agent activity facts contract contains only an activity identifier.
 */
@Data
public class AgentActivityFactsRequestDTO implements Serializable {

    private static final long serialVersionUID = -8050754533453211331L;

    private Long activityId;

    @JsonAnySetter
    public void rejectUnexpectedField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("Unsupported request field: " + fieldName);
    }

}
