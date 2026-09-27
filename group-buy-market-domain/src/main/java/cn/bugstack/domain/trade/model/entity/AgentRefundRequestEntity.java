package cn.bugstack.domain.trade.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRefundRequestEntity {
    private String idempotencyKey;
    private String userId;
    private String outTradeNo;
    private String expectedVersion;
    private String expectedRefundType;
    private String status;
    private String resultCode;
    private String resultJson;
    private Date createdAt;
    private Date updatedAt;
}
