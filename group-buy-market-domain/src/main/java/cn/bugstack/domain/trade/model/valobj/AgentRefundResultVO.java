package cn.bugstack.domain.trade.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRefundResultVO {
    private String status;
    private String resultCode;
    private Boolean refundExecuted;
    private Boolean idempotentReplay;
}
