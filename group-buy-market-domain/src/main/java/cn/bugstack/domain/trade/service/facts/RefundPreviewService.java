package cn.bugstack.domain.trade.service.facts;

import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.domain.trade.model.entity.GroupBuyTeamEntity;
import cn.bugstack.domain.trade.model.entity.MarketPayOrderEntity;
import cn.bugstack.domain.trade.model.valobj.RefundPreviewVO;
import cn.bugstack.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import cn.bugstack.domain.trade.service.IRefundPreviewService;
import cn.bugstack.types.enums.GroupBuyOrderEnumVO;
import org.springframework.stereotype.Service;

/**
 * Resolves only the current order/team facts needed by a later refund-proposal workflow.
 * No refund service, transaction, message, cache, or write-capable repository method is used.
 */
@Service
public class RefundPreviewService implements IRefundPreviewService {

    private final ITradeRepository repository;

    public RefundPreviewService(ITradeRepository repository) {
        this.repository = repository;
    }

    @Override
    public RefundPreviewVO getRefundPreview(String authenticatedUserId, String outTradeNo) {
        MarketPayOrderEntity order = repository.queryMarketPayOrderEntityByOutTradeNo(authenticatedUserId, outTradeNo);
        if (null == order) {
            return null;
        }

        GroupBuyTeamEntity team = repository.queryGroupBuyTeamByTeamId(order.getTeamId());
        if (null == team) {
            throw new IllegalStateException("Order references a missing team");
        }

        return buildPreview(order, team);
    }

    private RefundPreviewVO buildPreview(MarketPayOrderEntity order, GroupBuyTeamEntity team) {
        TradeOrderStatusEnumVO orderStatus = order.getTradeOrderStatusEnumVO();
        GroupBuyOrderEnumVO teamStatus = team.getStatus();

        // A closed order has already left the refundable state. This endpoint only reports it.
        if (TradeOrderStatusEnumVO.CLOSE.equals(orderStatus)) {
            return response(order, team, null, false, false);
        }

        if (TradeOrderStatusEnumVO.CREATE.equals(orderStatus)
                && GroupBuyOrderEnumVO.PROGRESS.equals(teamStatus)) {
            return response(order, team, "UNPAID", true, false);
        }

        if (TradeOrderStatusEnumVO.COMPLETE.equals(orderStatus)
                && GroupBuyOrderEnumVO.PROGRESS.equals(teamStatus)) {
            return response(order, team, "PAID_UNFORMED", true, false);
        }

        if (TradeOrderStatusEnumVO.COMPLETE.equals(orderStatus)
                && (GroupBuyOrderEnumVO.COMPLETE.equals(teamStatus)
                || GroupBuyOrderEnumVO.COMPLETE_FAIL.equals(teamStatus))) {
            // A formed, paid group can produce a proposal, but execution requires a human review.
            return response(order, team, "PAID_FORMED", true, true);
        }

        // Unknown or terminal combinations must not be automatically proposed or executed.
        return response(order, team, null, false, true);
    }

    private RefundPreviewVO response(MarketPayOrderEntity order, GroupBuyTeamEntity team,
                                     String refundType, boolean refundProposalAllowed,
                                     boolean requiresManualReview) {
        return RefundPreviewVO.builder()
                .orderStatus(order.getTradeOrderStatusEnumVO().name())
                .teamStatus(team.getStatus().name())
                .refundType(refundType)
                .refundProposalAllowed(refundProposalAllowed)
                .requiresManualReview(requiresManualReview)
                .orderUpdateTime(order.getUpdateTime())
                .teamUpdateTime(team.getUpdateTime())
                .build();
    }

}
