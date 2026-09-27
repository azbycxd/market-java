package cn.bugstack.infrastructure.dao.po;

import lombok.Data;
import java.util.Date;

@Data
public class AgentRefundRequest {
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
