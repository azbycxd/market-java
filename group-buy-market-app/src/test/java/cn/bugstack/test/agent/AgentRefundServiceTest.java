package cn.bugstack.test.agent;

import cn.bugstack.domain.trade.adapter.repository.IAgentRefundRequestRepository;
import cn.bugstack.domain.trade.exception.AgentRefundRequestDuplicateException;
import cn.bugstack.domain.trade.model.entity.AgentRefundRequestEntity;
import cn.bugstack.domain.trade.model.entity.TradeRefundBehaviorEntity;
import cn.bugstack.domain.trade.model.valobj.AgentRefundResultVO;
import cn.bugstack.domain.trade.model.valobj.RefundPreviewVO;
import cn.bugstack.domain.trade.service.IRefundPreviewService;
import cn.bugstack.domain.trade.service.ITradeRefundOrderService;
import cn.bugstack.domain.trade.service.refund.AgentRefundService;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Tests the Agent-only command guard; the established refund service remains mocked here. */
public class AgentRefundServiceTest {
    private IAgentRefundRequestRepository requestRepository;
    private IRefundPreviewService previewService;
    private ITradeRefundOrderService refundOrderService;
    private AgentRefundService service;
    private RefundPreviewVO unpaidPreview;
    private String version;

    @Before
    public void setUp() {
        requestRepository = mock(IAgentRefundRequestRepository.class);
        previewService = mock(IRefundPreviewService.class);
        refundOrderService = mock(ITradeRefundOrderService.class);
        service = new AgentRefundService(requestRepository, previewService, refundOrderService);
        unpaidPreview = RefundPreviewVO.builder().refundType("UNPAID").refundProposalAllowed(true)
                .requiresManualReview(false).orderUpdateTime(new Date(1767225661000L))
                .teamUpdateTime(new Date(1767225671000L)).build();
        version = AgentRefundService.versionOf(unpaidPreview);
    }

    @Test
    public void shouldExecuteOnceAfterMatchingPrecheck() throws Exception {
        when(previewService.getRefundPreview("user-a", "trade-a")).thenReturn(unpaidPreview);
        when(refundOrderService.refundOrder(any())).thenReturn(success());

        AgentRefundResultVO result = service.refund("user-a", "trade-a", "key-a", version, "UNPAID");

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals("REFUND_SUCCEEDED", result.getResultCode());
        assertTrue(result.getRefundExecuted());
        verify(refundOrderService, times(1)).refundOrder(any());
        verify(requestRepository).updateFinalResult("key-a", "SUCCEEDED", "REFUND_SUCCEEDED",
                "{\"status\":\"SUCCEEDED\",\"resultCode\":\"REFUND_SUCCEEDED\"}");
    }

    @Test
    public void shouldNotExecuteWhenVersionOrTypeDoesNotMatch() {
        when(previewService.getRefundPreview("user-a", "trade-a")).thenReturn(unpaidPreview);

        AgentRefundResultVO versionChanged = service.refund("user-a", "trade-a", "key-b", "obsolete|version", "UNPAID");
        AgentRefundResultVO typeChanged = service.refund("user-a", "trade-a", "key-c", version, "PAID_UNFORMED");

        assertEquals("VERSION_CHANGED", versionChanged.getResultCode());
        assertEquals("VERSION_CHANGED", typeChanged.getResultCode());
        verifyNoInteractions(refundOrderService);
        verify(requestRepository).updateFinalResult(eq("key-b"), eq("FAILED"), eq("VERSION_CHANGED"), any());
        verify(requestRepository).updateFinalResult(eq("key-c"), eq("FAILED"), eq("VERSION_CHANGED"), any());
    }

    @Test
    public void shouldRequireManualReviewWithoutCallingExecutor() {
        RefundPreviewVO manual = RefundPreviewVO.builder().refundType("PAID_FORMED").refundProposalAllowed(true)
                .requiresManualReview(true).orderUpdateTime(unpaidPreview.getOrderUpdateTime())
                .teamUpdateTime(unpaidPreview.getTeamUpdateTime()).build();
        when(previewService.getRefundPreview("user-a", "trade-a")).thenReturn(manual);

        AgentRefundResultVO result = service.refund("user-a", "trade-a", "key-d", AgentRefundService.versionOf(manual), "PAID_UNFORMED");

        assertEquals("MANUAL_REVIEW_REQUIRED", result.getResultCode());
        verifyNoInteractions(refundOrderService);
    }

    @Test
    public void shouldReplayTerminalResultAndRejectChangedBinding() {
        AgentRefundRequestEntity stored = AgentRefundRequestEntity.builder().idempotencyKey("key-e")
                .userId("user-a").outTradeNo("trade-a").expectedVersion(version).expectedRefundType("UNPAID")
                .status("SUCCEEDED").resultCode("REFUND_SUCCEEDED").build();
        doThrow(new AgentRefundRequestDuplicateException()).when(requestRepository).insertProcessing(any());
        when(requestRepository.queryByIdempotencyKey("key-e")).thenReturn(stored);

        AgentRefundResultVO replay = service.refund("user-a", "trade-a", "key-e", version, "UNPAID");
        AgentRefundResultVO rejected = service.refund("user-b", "trade-a", "key-e", version, "UNPAID");

        assertTrue(replay.getIdempotentReplay());
        assertEquals("SUCCEEDED", replay.getStatus());
        assertEquals("IDEMPOTENCY_KEY_REUSED", rejected.getResultCode());
        verifyNoInteractions(previewService, refundOrderService);
    }

    @Test
    public void shouldReturnProcessingForMatchingInFlightKeyWithoutCallingExecutor() {
        AgentRefundRequestEntity processing = AgentRefundRequestEntity.builder().idempotencyKey("key-processing")
                .userId("user-a").outTradeNo("trade-a").expectedVersion(version).expectedRefundType("UNPAID")
                .status("PROCESSING").build();
        doThrow(new AgentRefundRequestDuplicateException()).when(requestRepository).insertProcessing(any());
        when(requestRepository.queryByIdempotencyKey("key-processing")).thenReturn(processing);

        AgentRefundResultVO result = service.refund("user-a", "trade-a", "key-processing", version, "UNPAID");

        assertEquals("PROCESSING", result.getStatus());
        assertEquals("REFUND_PROCESSING", result.getResultCode());
        assertTrue(result.getIdempotentReplay());
        verifyNoInteractions(previewService, refundOrderService);
    }

    @Test
    public void shouldNotTreatUnderlyingRepeatAsSecondSuccess() throws Exception {
        when(previewService.getRefundPreview("user-a", "trade-a")).thenReturn(unpaidPreview);
        when(refundOrderService.refundOrder(any())).thenReturn(TradeRefundBehaviorEntity.builder()
                .tradeRefundBehaviorEnum(TradeRefundBehaviorEntity.TradeRefundBehaviorEnum.REPEAT).build());

        AgentRefundResultVO result = service.refund("user-a", "trade-a", "key-f", version, "UNPAID");

        assertEquals("FAILED", result.getStatus());
        assertEquals("VERSION_CHANGED", result.getResultCode());
        assertFalse(result.getRefundExecuted());
    }

    @Test
    public void shouldQuerySucceededResultOwnedByCurrentUser() {
        when(requestRepository.queryByIdempotencyKey("result-success")).thenReturn(
                AgentRefundRequestEntity.builder().idempotencyKey("result-success").userId("user-a")
                        .status("SUCCEEDED").resultCode("REFUND_SUCCEEDED").build());

        AgentRefundResultVO result = service.queryResult("user-a", "result-success");

        assertEquals("SUCCEEDED", result.getStatus());
        assertEquals("REFUND_SUCCEEDED", result.getResultCode());
        assertTrue(result.getRefundExecuted());
        verify(requestRepository, never()).insertAbandoned(any());
    }

    @Test
    public void shouldReserveMissingKeyAsAbandonedWithNoRefundParameters() {
        when(requestRepository.queryByIdempotencyKey("result-missing")).thenReturn(null);

        AgentRefundResultVO result = service.queryResult("user-a", "result-missing");

        assertEquals("ABANDONED", result.getStatus());
        assertEquals("NOT_RECEIVED_BEFORE_QUERY", result.getResultCode());
        assertFalse(result.getRefundExecuted());
        verify(requestRepository).insertAbandoned(argThat(request ->
                "result-missing".equals(request.getIdempotencyKey())
                        && "user-a".equals(request.getUserId())
                        && "ABANDONED".equals(request.getStatus())
                        && null == request.getOutTradeNo()
                        && null == request.getExpectedVersion()
                        && null == request.getExpectedRefundType()));
    }

    @Test
    public void shouldHideAnotherUsersKeyWithoutInsertingAnything() {
        when(requestRepository.queryByIdempotencyKey("foreign-key")).thenReturn(
                AgentRefundRequestEntity.builder().idempotencyKey("foreign-key").userId("user-b")
                        .status("SUCCEEDED").resultCode("REFUND_SUCCEEDED").build());

        AgentRefundResultVO result = service.queryResult("user-a", "foreign-key");

        assertNull(result.getStatus());
        assertEquals("NOT_FOUND", result.getResultCode());
        assertFalse(result.getRefundExecuted());
        verify(requestRepository, never()).insertAbandoned(any());
    }

    @Test
    public void shouldNeverExecuteRefundForCallerOwnedAbandonedKey() {
        AgentRefundRequestEntity abandoned = AgentRefundRequestEntity.builder().idempotencyKey("abandoned-key")
                .userId("user-a").status("ABANDONED").resultCode("NOT_RECEIVED_BEFORE_QUERY").build();
        doThrow(new AgentRefundRequestDuplicateException()).when(requestRepository).insertProcessing(any());
        when(requestRepository.queryByIdempotencyKey("abandoned-key")).thenReturn(abandoned);

        AgentRefundResultVO result = service.refund("user-a", "trade-a", "abandoned-key", version, "UNPAID");

        assertEquals("FAILED", result.getStatus());
        assertEquals("ABANDONED", result.getResultCode());
        assertFalse(result.getRefundExecuted());
        verifyNoInteractions(previewService, refundOrderService);
    }

    @Test
    public void shouldReturnRefundRecordWhenRefundWinsInsertRace() {
        AgentRefundRequestEntity processing = AgentRefundRequestEntity.builder().idempotencyKey("race-key")
                .userId("user-a").status("PROCESSING").build();
        when(requestRepository.queryByIdempotencyKey("race-key")).thenReturn(null, processing);
        doThrow(new AgentRefundRequestDuplicateException()).when(requestRepository).insertAbandoned(any());

        AgentRefundResultVO result = service.queryResult("user-a", "race-key");

        assertEquals("PROCESSING", result.getStatus());
        assertNull(result.getResultCode());
        assertFalse(result.getRefundExecuted());
    }

    private TradeRefundBehaviorEntity success() {
        return TradeRefundBehaviorEntity.builder()
                .tradeRefundBehaviorEnum(TradeRefundBehaviorEntity.TradeRefundBehaviorEnum.SUCCESS).build();
    }
}
