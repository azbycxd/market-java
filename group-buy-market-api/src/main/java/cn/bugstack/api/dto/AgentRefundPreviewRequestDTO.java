package cn.bugstack.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Data;

import java.io.Serializable;

/**
 * Read-only refund preview request. The user identity is established only from the verified
 * internal JWT subject and is deliberately not accepted in the request body.
 */
@Data
public class AgentRefundPreviewRequestDTO implements Serializable {

    private static final long serialVersionUID = -4332636858090082446L;

    private String outTradeNo;

    @JsonAnySetter
    public void rejectUnexpectedField(String fieldName, Object ignored) {
        throw new IllegalArgumentException("Unsupported request field: " + fieldName);
    }

}
