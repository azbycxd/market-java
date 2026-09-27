package cn.bugstack.infrastructure.adapter.repository;

import cn.bugstack.domain.trade.adapter.repository.IAgentRefundRequestRepository;
import cn.bugstack.domain.trade.exception.AgentRefundRequestDuplicateException;
import cn.bugstack.domain.trade.model.entity.AgentRefundRequestEntity;
import cn.bugstack.infrastructure.dao.IAgentRefundRequestDao;
import cn.bugstack.infrastructure.dao.po.AgentRefundRequest;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DuplicateKeyException;

@Repository
public class AgentRefundRequestRepository implements IAgentRefundRequestRepository {
    private final IAgentRefundRequestDao dao;

    public AgentRefundRequestRepository(IAgentRefundRequestDao dao) { this.dao = dao; }

    @Override
    public int insertProcessing(AgentRefundRequestEntity request) {
        AgentRefundRequest po = new AgentRefundRequest();
        po.setIdempotencyKey(request.getIdempotencyKey()); po.setUserId(request.getUserId());
        po.setOutTradeNo(request.getOutTradeNo()); po.setExpectedVersion(request.getExpectedVersion());
        po.setExpectedRefundType(request.getExpectedRefundType()); po.setStatus(request.getStatus());
        try {
            return dao.insert(po);
        } catch (DuplicateKeyException duplicate) {
            throw new AgentRefundRequestDuplicateException();
        }
    }

    @Override
    public AgentRefundRequestEntity queryByIdempotencyKey(String idempotencyKey) {
        AgentRefundRequest po = dao.queryByIdempotencyKey(idempotencyKey);
        if (null == po) return null;
        return AgentRefundRequestEntity.builder().idempotencyKey(po.getIdempotencyKey()).userId(po.getUserId())
                .outTradeNo(po.getOutTradeNo()).expectedVersion(po.getExpectedVersion())
                .expectedRefundType(po.getExpectedRefundType()).status(po.getStatus()).resultCode(po.getResultCode())
                .resultJson(po.getResultJson()).createdAt(po.getCreatedAt()).updatedAt(po.getUpdatedAt()).build();
    }

    @Override
    public int updateFinalResult(String idempotencyKey, String status, String resultCode, String resultJson) {
        return dao.updateFinalResult(idempotencyKey, status, resultCode, resultJson);
    }
}
