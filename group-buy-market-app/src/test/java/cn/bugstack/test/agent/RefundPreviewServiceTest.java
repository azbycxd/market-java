package cn.bugstack.test.agent;

import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.domain.trade.model.entity.GroupBuyTeamEntity;
import cn.bugstack.domain.trade.model.entity.MarketPayOrderEntity;
import cn.bugstack.domain.trade.model.valobj.RefundPreviewVO;
import cn.bugstack.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import cn.bugstack.domain.trade.service.facts.RefundPreviewService;
import cn.bugstack.types.enums.GroupBuyOrderEnumVO;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

/** Unit tests only. They prove the preview service uses only the two query methods on its repository. */
public class RefundPreviewServiceTest {

    private ITradeRepository repository;
    private RefundPreviewService service;

    @Before
    public void setUp() {
        repository = mock(ITradeRepository.class);
        service = new RefundPreviewService(repository);
    }

    @Test
    public void shouldPreviewUnpaidOrderWithoutAnyWrite() {
        Date updateTime = new Date(1767225600000L);
        stubOwnedOrder(TradeOrderStatusEnumVO.CREATE, updateTime);
        stubTeam(GroupBuyOrderEnumVO.PROGRESS);

        RefundPreviewVO preview = service.getRefundPreview("user-1", "trade-1");

        assertEquals("CREATE", preview.getOrderStatus());
        assertEquals("PROGRESS", preview.getTeamStatus());
        assertEquals("UNPAID", preview.getRefundType());
        assertTrue(preview.getRefundProposalAllowed());
        assertFalse(preview.getRequiresManualReview());
        assertEquals(updateTime, preview.getOrderUpdateTime());
        verifyReadOnlyLookups();
    }

    @Test
    public void shouldPreviewPaidUnformedOrderWithoutAnyWrite() {
        stubOwnedOrder(TradeOrderStatusEnumVO.COMPLETE, new Date());
        stubTeam(GroupBuyOrderEnumVO.PROGRESS);

        RefundPreviewVO preview = service.getRefundPreview("user-1", "trade-1");

        assertEquals("PAID_UNFORMED", preview.getRefundType());
        assertTrue(preview.getRefundProposalAllowed());
        assertFalse(preview.getRequiresManualReview());
        verifyReadOnlyLookups();
    }

    @Test
    public void shouldRequireManualReviewForPaidFormedOrder() {
        stubOwnedOrder(TradeOrderStatusEnumVO.COMPLETE, new Date());
        stubTeam(GroupBuyOrderEnumVO.COMPLETE);

        RefundPreviewVO preview = service.getRefundPreview("user-1", "trade-1");

        assertEquals("PAID_FORMED", preview.getRefundType());
        assertTrue(preview.getRefundProposalAllowed());
        assertTrue(preview.getRequiresManualReview());
        verifyReadOnlyLookups();
    }

    @Test
    public void shouldNotAllowClosedOrderToBeRefunded() {
        stubOwnedOrder(TradeOrderStatusEnumVO.CLOSE, new Date());
        stubTeam(GroupBuyOrderEnumVO.PROGRESS);

        RefundPreviewVO preview = service.getRefundPreview("user-1", "trade-1");

        assertEquals("CLOSE", preview.getOrderStatus());
        assertNull(preview.getRefundType());
        assertFalse(preview.getRefundProposalAllowed());
        assertFalse(preview.getRequiresManualReview());
        verifyReadOnlyLookups();
    }

    @Test
    public void shouldNotRevealOtherUsersOrderAndShouldNotLoadItsTeam() {
        when(repository.queryMarketPayOrderEntityByOutTradeNo("user-1", "other-users-trade")).thenReturn(null);

        assertNull(service.getRefundPreview("user-1", "other-users-trade"));
        verify(repository).queryMarketPayOrderEntityByOutTradeNo("user-1", "other-users-trade");
        verifyNoMoreInteractions(repository);
    }

    private void stubOwnedOrder(TradeOrderStatusEnumVO status, Date updateTime) {
        when(repository.queryMarketPayOrderEntityByOutTradeNo("user-1", "trade-1"))
                .thenReturn(MarketPayOrderEntity.builder()
                        .teamId("team-1")
                        .tradeOrderStatusEnumVO(status)
                        .updateTime(updateTime)
                        .build());
    }

    private void stubTeam(GroupBuyOrderEnumVO status) {
        when(repository.queryGroupBuyTeamByTeamId("team-1"))
                .thenReturn(GroupBuyTeamEntity.builder().teamId("team-1").status(status).build());
    }

    private void verifyReadOnlyLookups() {
        verify(repository).queryMarketPayOrderEntityByOutTradeNo("user-1", "trade-1");
        verify(repository).queryGroupBuyTeamByTeamId("team-1");
        verifyNoMoreInteractions(repository);
    }

}
