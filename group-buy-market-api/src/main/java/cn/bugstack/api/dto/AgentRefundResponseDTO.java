package cn.bugstack.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRefundResponseDTO implements Serializable {
    private String status;
    private String resultCode;
    private Boolean refundExecuted;
    private Boolean idempotentReplay;
}
