package cn.bugstack.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

import java.io.Serializable;

/** Agent refund execution request. User identity is accepted only from the verified JWT subject. */
@Data
public class AgentRefundRequestDTO implements Serializable {
    private String outTradeNo;
    private String idempotencyKey;
    private String expectedVersion;
    private String expectedRefundType;

    @JsonAnySetter
    public void rejectUnexpectedField(String fieldName, Object ignored) {
        throw new IllegalArgumentException("Unsupported request field: " + fieldName);
    }
}
