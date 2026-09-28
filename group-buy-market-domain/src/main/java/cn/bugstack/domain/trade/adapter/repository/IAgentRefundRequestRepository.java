package cn.bugstack.domain.trade.adapter.repository;

import cn.bugstack.domain.trade.model.entity.AgentRefundRequestEntity;

public interface IAgentRefundRequestRepository {
    int insertProcessing(AgentRefundRequestEntity request);
    int insertAbandoned(AgentRefundRequestEntity request);
    AgentRefundRequestEntity queryByIdempotencyKey(String idempotencyKey);
    int updateFinalResult(String idempotencyKey, String status, String resultCode, String resultJson);
}
