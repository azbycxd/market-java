package cn.bugstack.domain.trade.service.refund;

import cn.bugstack.domain.trade.adapter.repository.IAgentRefundRequestRepository;
import cn.bugstack.domain.trade.exception.AgentRefundRequestDuplicateException;
import cn.bugstack.domain.trade.model.entity.AgentRefundRequestEntity;
import cn.bugstack.domain.trade.model.entity.TradeRefundCommandEntity;
import cn.bugstack.domain.trade.model.entity.TradeRefundBehaviorEntity;
import cn.bugstack.domain.trade.model.valobj.AgentRefundResultVO;
import cn.bugstack.domain.trade.model.valobj.RefundPreviewVO;
import cn.bugstack.domain.trade.service.IAgentRefundService;
import cn.bugstack.domain.trade.service.IRefundPreviewService;
import cn.bugstack.domain.trade.service.ITradeRefundOrderService;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Agent-facing refund command coordinator. It owns only idempotency and optimistic precheck;
 * the established refund service remains the sole executor of refund business transitions.
 */
@Service
public class AgentRefundService implements IAgentRefundService {
    private static final String PROCESSING = "PROCESSING";
    private static final String SUCCEEDED = "SUCCEEDED";
    private static final String FAILED = "FAILED";

    private final IAgentRefundRequestRepository requestRepository;
    private final IRefundPreviewService refundPreviewService;
    private final ITradeRefundOrderService tradeRefundOrderService;

    public AgentRefundService(IAgentRefundRequestRepository requestRepository,
                              IRefundPreviewService refundPreviewService,
                              ITradeRefundOrderService tradeRefundOrderService) {
        this.requestRepository = requestRepository;
        this.refundPreviewService = refundPreviewService;
        this.tradeRefundOrderService = tradeRefundOrderService;
    }

    @Override
    public AgentRefundResultVO refund(String authenticatedUserId, String outTradeNo, String idempotencyKey,
                                      String expectedVersion, String expectedRefundType) {
        AgentRefundRequestEntity request = AgentRefundRequestEntity.builder()
                .idempotencyKey(idempotencyKey).userId(authenticatedUserId).outTradeNo(outTradeNo)
                .expectedVersion(expectedVersion).expectedRefundType(expectedRefundType).status(PROCESSING).build();
        try {
            requestRepository.insertProcessing(request);
        } catch (AgentRefundRequestDuplicateException duplicate) {
            return replayOrReject(request);
        }

        RefundPreviewVO preview = refundPreviewService.getRefundPreview(authenticatedUserId, outTradeNo);
        if (null == preview) {
            return complete(idempotencyKey, FAILED, "ORDER_NOT_FOUND_OR_NOT_AUTHORIZED", false);
        }
        // PAID_FORMED is deliberately not an executable request type. It is discovered from
        // the server-side current state and is always handed off for manual review.
        if (Boolean.TRUE.equals(preview.getRequiresManualReview())) {
            return complete(idempotencyKey, FAILED, "MANUAL_REVIEW_REQUIRED", false);
        }
        if (!expectedVersion.equals(versionOf(preview)) || !expectedRefundType.equals(preview.getRefundType())) {
            return complete(idempotencyKey, FAILED, "VERSION_CHANGED", false);
        }
        if (!Boolean.TRUE.equals(preview.getRefundProposalAllowed())) {
            return complete(idempotencyKey, FAILED, "REFUND_NOT_ALLOWED", false);
        }

        try {
            TradeRefundBehaviorEntity behavior = tradeRefundOrderService.refundOrder(TradeRefundCommandEntity.builder()
                    .userId(authenticatedUserId).outTradeNo(outTradeNo)
                    .build());
            if (!TradeRefundBehaviorEntity.TradeRefundBehaviorEnum.SUCCESS.equals(behavior.getTradeRefundBehaviorEnum())) {
                // A concurrent winner may have closed the order after this request's preview.
                // Do not represent the existing executor's repeat result as a second success.
                return complete(idempotencyKey, FAILED, "VERSION_CHANGED", false);
            }
            return complete(idempotencyKey, SUCCEEDED, "REFUND_SUCCEEDED", true);
        } catch (Exception e) {
            return complete(idempotencyKey, FAILED, "REFUND_EXECUTION_FAILED", false);
        }
    }

    private AgentRefundResultVO replayOrReject(AgentRefundRequestEntity incoming) {
        AgentRefundRequestEntity stored = requestRepository.queryByIdempotencyKey(incoming.getIdempotencyKey());
        if (null == stored || !sameRequest(stored, incoming)) {
            return result(FAILED, "IDEMPOTENCY_KEY_REUSED", false, false);
        }
        if (PROCESSING.equals(stored.getStatus())) {
            return result(PROCESSING, "REFUND_PROCESSING", false, true);
        }
        return result(stored.getStatus(), stored.getResultCode(), SUCCEEDED.equals(stored.getStatus()), true);
    }

    private boolean sameRequest(AgentRefundRequestEntity left, AgentRefundRequestEntity right) {
        return equals(left.getUserId(), right.getUserId()) && equals(left.getOutTradeNo(), right.getOutTradeNo())
                && equals(left.getExpectedVersion(), right.getExpectedVersion())
                && equals(left.getExpectedRefundType(), right.getExpectedRefundType());
    }

    private boolean equals(String left, String right) { return null == left ? null == right : left.equals(right); }

    private AgentRefundResultVO complete(String idempotencyKey, String status, String resultCode, boolean executed) {
        requestRepository.updateFinalResult(idempotencyKey, status, resultCode,
                "{\"status\":\"" + status + "\",\"resultCode\":\"" + resultCode + "\"}");
        return result(status, resultCode, executed, false);
    }

    private AgentRefundResultVO result(String status, String resultCode, boolean executed, boolean replay) {
        return AgentRefundResultVO.builder().status(status).resultCode(resultCode)
                .refundExecuted(executed).idempotentReplay(replay).build();
    }

    public static String versionOf(RefundPreviewVO preview) {
        return format(preview.getOrderUpdateTime()) + "|" + format(preview.getTeamUpdateTime());
    }

    private static String format(Date value) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
        formatter.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return formatter.format(value);
    }
}
