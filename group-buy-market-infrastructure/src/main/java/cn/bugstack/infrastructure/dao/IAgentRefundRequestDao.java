package cn.bugstack.infrastructure.dao;

import cn.bugstack.infrastructure.dao.po.AgentRefundRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface IAgentRefundRequestDao {
    int insert(AgentRefundRequest request);
    AgentRefundRequest queryByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
    int updateFinalResult(@Param("idempotencyKey") String idempotencyKey, @Param("status") String status,
                          @Param("resultCode") String resultCode, @Param("resultJson") String resultJson);
}
