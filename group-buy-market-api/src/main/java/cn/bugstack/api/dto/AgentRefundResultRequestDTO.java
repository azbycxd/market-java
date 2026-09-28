package cn.bugstack.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

import java.io.Serializable;

/** Query an Agent refund outcome by the caller-owned idempotency key. */
@Data
public class AgentRefundResultRequestDTO implements Serializable {
    private String idempotencyKey;

    @JsonAnySetter
    public void rejectUnexpectedField(String fieldName, Object ignored) {
        throw new IllegalArgumentException("Unsupported request field: " + fieldName);
    }
}
