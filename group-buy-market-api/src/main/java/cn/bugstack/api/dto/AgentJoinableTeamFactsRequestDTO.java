package cn.bugstack.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

import java.io.Serializable;

/**
 * Read-only request for joinable team facts. Caller identity is deliberately absent.
 */
@Data
public class AgentJoinableTeamFactsRequestDTO implements Serializable {

    private static final long serialVersionUID = 513568002197686421L;

    private Long activityId;

    /**
     * The Agent contract contains only activityId. Reject, rather than silently ignore, an attempted
     * user identity, token, pagination control, or any other unsupported input.
     */
    @JsonAnySetter
    public void rejectUnexpectedField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("Unsupported request field: " + fieldName);
    }

}
