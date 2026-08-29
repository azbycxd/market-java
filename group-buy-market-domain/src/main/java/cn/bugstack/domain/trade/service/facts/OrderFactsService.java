package cn.bugstack.domain.trade.service.facts;

import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.domain.trade.model.entity.GroupBuyActivityEntity;
import cn.bugstack.domain.trade.model.entity.GroupBuyTeamEntity;
import cn.bugstack.domain.trade.model.entity.MarketPayOrderEntity;
import cn.bugstack.domain.trade.model.valobj.OrderFactsVO;
import cn.bugstack.domain.trade.service.IOrderFactsService;
import org.springframework.stereotype.Service;

/**
 * Domain read facade. It performs no state transition and exposes only facts needed by the Agent contract.
 */
@Service
public class OrderFactsService implements IOrderFactsService {

    private final ITradeRepository repository;

    public OrderFactsService(ITradeRepository repository) {
        this.repository = repository;
    }

    @Override
    public OrderFactsVO getOrderFacts(String authenticatedUserId, String outTradeNo) {
        MarketPayOrderEntity order = repository.queryMarketPayOrderEntityByOutTradeNo(authenticatedUserId, outTradeNo);
        if (null == order) {
            return null;
        }

        GroupBuyTeamEntity team = repository.queryGroupBuyTeamByTeamId(order.getTeamId());
        if (null == team) {
            throw new IllegalStateException("Order references a missing team");
        }

        GroupBuyActivityEntity activity = repository.queryGroupBuyActivityEntityByActivityId(team.getActivityId());
        if (null == activity) {
            throw new IllegalStateException("Team references a missing activity");
        }

        return OrderFactsVO.builder()
                .orderStatus(order.getTradeOrderStatusEnumVO().name())
                .teamStatus(team.getStatus().name())
                .targetCount(team.getTargetCount())
                .lockCount(team.getLockCount())
                .completeCount(team.getCompleteCount())
                .validEndTime(team.getValidEndTime())
                .activityStatus(activity.getStatus().name())
                .teamId(team.getTeamId())
                .activityId(activity.getActivityId())
                .build();
    }

}
