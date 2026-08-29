package cn.bugstack.test.agent;

import cn.bugstack.domain.trade.adapter.repository.ITradeRepository;
import cn.bugstack.domain.trade.model.entity.GroupBuyActivityEntity;
import cn.bugstack.domain.trade.model.entity.GroupBuyTeamEntity;
import cn.bugstack.domain.trade.model.entity.MarketPayOrderEntity;
import cn.bugstack.domain.trade.model.valobj.OrderFactsVO;
import cn.bugstack.domain.trade.model.valobj.TradeOrderStatusEnumVO;
import cn.bugstack.domain.trade.service.facts.OrderFactsService;
import cn.bugstack.types.enums.ActivityStatusEnumVO;
import cn.bugstack.types.enums.GroupBuyOrderEnumVO;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

/** Unit tests only: no database, Redis, MQ, or order/team/activity write operation is involved. */
public class OrderFactsServiceTest {

    private ITradeRepository repository;
    private OrderFactsService service;

    @Before
    public void setUp() {
        repository = mock(ITradeRepository.class);
        service = new OrderFactsService(repository);
    }

    @Test
    public void shouldReturnFactsForAuthenticatedUsersOwnOrder() {
        Date validEndTime = new Date(1767225600000L);
        when(repository.queryMarketPayOrderEntityByOutTradeNo("user-1", "trade-1"))
                .thenReturn(MarketPayOrderEntity.builder()
                        .teamId("team-1")
                        .tradeOrderStatusEnumVO(TradeOrderStatusEnumVO.COMPLETE)
                        .build());
        when(repository.queryGroupBuyTeamByTeamId("team-1"))
                .thenReturn(GroupBuyTeamEntity.builder()
                        .teamId("team-1")
                        .activityId(1001L)
                        .status(GroupBuyOrderEnumVO.PROGRESS)
                        .targetCount(3)
                        .lockCount(2)
                        .completeCount(2)
                        .validEndTime(validEndTime)
                        .build());
        when(repository.queryGroupBuyActivityEntityByActivityId(1001L))
                .thenReturn(GroupBuyActivityEntity.builder()
                        .activityId(1001L)
                        .status(ActivityStatusEnumVO.EFFECTIVE)
                        .build());

        OrderFactsVO facts = service.getOrderFacts("user-1", "trade-1");

        assertEquals("COMPLETE", facts.getOrderStatus());
        assertEquals("PROGRESS", facts.getTeamStatus());
        assertEquals(Integer.valueOf(3), facts.getTargetCount());
        assertEquals(Integer.valueOf(2), facts.getLockCount());
        assertEquals(Integer.valueOf(2), facts.getCompleteCount());
        assertEquals(validEndTime, facts.getValidEndTime());
        assertEquals("EFFECTIVE", facts.getActivityStatus());
        assertEquals("team-1", facts.getTeamId());
        assertEquals(Long.valueOf(1001L), facts.getActivityId());
        verify(repository).queryMarketPayOrderEntityByOutTradeNo("user-1", "trade-1");
        verify(repository).queryGroupBuyTeamByTeamId("team-1");
        verify(repository).queryGroupBuyActivityEntityByActivityId(1001L);
        verifyNoMoreInteractions(repository);
    }

    @Test
    public void shouldNotRevealWhetherTradeNumberBelongsToAnotherUser() {
        when(repository.queryMarketPayOrderEntityByOutTradeNo("user-1", "another-users-trade"))
                .thenReturn(null);

        assertNull(service.getOrderFacts("user-1", "another-users-trade"));
        verify(repository).queryMarketPayOrderEntityByOutTradeNo("user-1", "another-users-trade");
        verifyNoMoreInteractions(repository);
    }

}
