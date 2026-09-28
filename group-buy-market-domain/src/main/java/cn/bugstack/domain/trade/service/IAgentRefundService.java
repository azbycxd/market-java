package cn.bugstack.domain.trade.service;

import cn.bugstack.domain.trade.model.valobj.AgentRefundResultVO;

public interface IAgentRefundService {
    AgentRefundResultVO refund(String authenticatedUserId, String outTradeNo, String idempotencyKey,
                               String expectedVersion, String expectedRefundType);

    AgentRefundResultVO queryResult(String authenticatedUserId, String idempotencyKey);
}
