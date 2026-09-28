package cn.bugstack.test.agent;

import cn.bugstack.domain.demo.model.valobj.DemoResetResultVO;
import cn.bugstack.infrastructure.dao.IDemoResetDao;
import cn.bugstack.infrastructure.demo.DemoResetService;
import org.junit.Before;
import org.junit.Test;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;

import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class DemoResetServiceTest {

    private IDemoResetDao demoResetDao;
    private RedissonClient redissonClient;
    private RKeys keys;
    private DemoResetService service;

    @Before
    public void setUp() {
        demoResetDao = mock(IDemoResetDao.class);
        redissonClient = mock(RedissonClient.class);
        keys = mock(RKeys.class);
        service = new DemoResetService(demoResetDao, redissonClient);
        when(demoResetDao.lockProcessingRefundRequestIds("demo_user")).thenReturn(Collections.emptyList());
        when(redissonClient.getKeys()).thenReturn(keys);
        when(keys.deleteByPattern(anyString())).thenReturn(1L);
    }

    @Test
    public void shouldRestoreDatabaseAndOnlyTargetedRedisKeys() {
        DemoResetResultVO result = service.reset("demo_user");

        assertTrue(result.getReset());
        assertFalse(result.getBlocked());
        assertEquals(Integer.valueOf(4), result.getOrderCount());
        assertEquals(Integer.valueOf(4), result.getTeamCount());
        assertEquals(Long.valueOf(5), result.getRedisKeysDeleted());
        assertEquals("TARGETED_DEMO_KEYS", result.getRedisResetStrategy());
        verify(demoResetDao).deleteRefundRequests("demo_user");
        verify(demoResetDao).deleteDemoNotifyTasks();
        verify(demoResetDao).restoreDemoTeams();
        verify(demoResetDao).restoreDemoOrders();
        verify(keys, times(5)).deleteByPattern(anyString());
        verify(keys, never()).flushdb();
    }

    @Test
    public void shouldBeIdempotentAcrossConsecutiveResets() {
        DemoResetResultVO first = service.reset("demo_user");
        DemoResetResultVO second = service.reset("demo_user");

        assertTrue(first.getReset());
        assertTrue(second.getReset());
        assertEquals(first.getOrderCount(), second.getOrderCount());
        assertEquals(first.getTeamCount(), second.getTeamCount());
        verify(demoResetDao, times(2)).restoreDemoTeams();
        verify(demoResetDao, times(2)).restoreDemoOrders();
    }

    @Test
    public void shouldBlockWithoutMutationWhenRefundIsProcessing() {
        when(demoResetDao.lockProcessingRefundRequestIds("demo_user"))
                .thenReturn(Collections.singletonList(1L));

        DemoResetResultVO result = service.reset("demo_user");

        assertFalse(result.getReset());
        assertTrue(result.getBlocked());
        verify(demoResetDao, never()).deleteRefundRequests(anyString());
        verify(demoResetDao, never()).deleteDemoNotifyTasks();
        verify(demoResetDao, never()).restoreDemoTeams();
        verify(demoResetDao, never()).restoreDemoOrders();
        verifyNoInteractions(redissonClient);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectNonDemoIdentityInsideServiceBoundary() {
        service.reset("other_user");
    }

}
