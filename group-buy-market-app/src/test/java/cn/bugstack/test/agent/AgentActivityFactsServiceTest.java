package cn.bugstack.test.agent;

import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.valobj.ActivityFactsVO;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityFactsSourceVO;
import cn.bugstack.domain.activity.service.facts.AgentActivityFactsService;
import cn.bugstack.types.enums.ActivityStatusEnumVO;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for raw activity facts and the exact ActivityUsabilityRuleFilter time boundary. */
public class AgentActivityFactsServiceTest {

    private static final Instant EVALUATED_AT = Instant.parse("2026-09-01T10:00:00Z");
    private IActivityRepository activityRepository;
    private AgentActivityFactsService service;

    @Before
    public void setUp() {
        activityRepository = mock(IActivityRepository.class);
        service = new AgentActivityFactsService(activityRepository, Clock.fixed(EVALUATED_AT, ZoneOffset.UTC));
    }

    @Test
    public void shouldReturnEffectiveActivityInsideTimeWindow() {
        when(activityRepository.queryGroupBuyActivityFactsSourceByActivityId(1001L))
                .thenReturn(source(ActivityStatusEnumVO.EFFECTIVE,
                        "2026-09-01T09:59:59Z", "2026-09-01T10:00:01Z"));

        ActivityFactsVO facts = service.getActivityFacts(1001L);

        assertEquals(ActivityStatusEnumVO.EFFECTIVE, facts.getStatus());
        assertTrue(facts.getWithinValidTime());
        assertEquals(Date.from(EVALUATED_AT), facts.getEvaluatedAt());
    }

    @Test
    public void shouldReportFalseBeforeStartTime() {
        when(activityRepository.queryGroupBuyActivityFactsSourceByActivityId(1001L))
                .thenReturn(source(ActivityStatusEnumVO.EFFECTIVE,
                        "2026-09-01T10:00:01Z", "2026-09-01T10:01:00Z"));

        assertFalse(service.getActivityFacts(1001L).getWithinValidTime());
    }

    @Test
    public void shouldReportFalseAfterEndTime() {
        when(activityRepository.queryGroupBuyActivityFactsSourceByActivityId(1001L))
                .thenReturn(source(ActivityStatusEnumVO.EFFECTIVE,
                        "2026-09-01T09:00:00Z", "2026-09-01T09:59:59Z"));

        assertFalse(service.getActivityFacts(1001L).getWithinValidTime());
    }

    @Test
    public void shouldTreatStartTimeEqualityAsWithinValidTime() {
        when(activityRepository.queryGroupBuyActivityFactsSourceByActivityId(1001L))
                .thenReturn(source(ActivityStatusEnumVO.EFFECTIVE,
                        "2026-09-01T10:00:00Z", "2026-09-01T10:01:00Z"));

        assertTrue(service.getActivityFacts(1001L).getWithinValidTime());
    }

    @Test
    public void shouldTreatEndTimeEqualityAsWithinValidTime() {
        when(activityRepository.queryGroupBuyActivityFactsSourceByActivityId(1001L))
                .thenReturn(source(ActivityStatusEnumVO.EFFECTIVE,
                        "2026-09-01T09:00:00Z", "2026-09-01T10:00:00Z"));

        assertTrue(service.getActivityFacts(1001L).getWithinValidTime());
    }

    @Test
    public void shouldReturnCreateStatusWithoutTurningItIntoEligibility() {
        when(activityRepository.queryGroupBuyActivityFactsSourceByActivityId(1001L))
                .thenReturn(source(ActivityStatusEnumVO.CREATE,
                        "2026-09-01T09:00:00Z", "2026-09-01T11:00:00Z"));

        ActivityFactsVO facts = service.getActivityFacts(1001L);

        assertEquals(ActivityStatusEnumVO.CREATE, facts.getStatus());
        assertTrue(facts.getWithinValidTime());
    }

    @Test
    public void shouldReturnOverdueStatusWithoutChangingTimeObservation() {
        when(activityRepository.queryGroupBuyActivityFactsSourceByActivityId(1001L))
                .thenReturn(source(ActivityStatusEnumVO.OVERDUE,
                        "2026-09-01T09:00:00Z", "2026-09-01T11:00:00Z"));

        ActivityFactsVO facts = service.getActivityFacts(1001L);

        assertEquals(ActivityStatusEnumVO.OVERDUE, facts.getStatus());
        assertTrue(facts.getWithinValidTime());
    }

    @Test
    public void shouldReturnAbandonedStatusWithoutChangingTimeObservation() {
        when(activityRepository.queryGroupBuyActivityFactsSourceByActivityId(1001L))
                .thenReturn(source(ActivityStatusEnumVO.ABANDONED,
                        "2026-09-01T09:00:00Z", "2026-09-01T11:00:00Z"));

        ActivityFactsVO facts = service.getActivityFacts(1001L);

        assertEquals(ActivityStatusEnumVO.ABANDONED, facts.getStatus());
        assertTrue(facts.getWithinValidTime());
    }

    @Test
    public void shouldReturnNullWhenRawActivityDoesNotExist() {
        when(activityRepository.queryGroupBuyActivityFactsSourceByActivityId(404L)).thenReturn(null);

        assertNull(service.getActivityFacts(404L));
        verify(activityRepository).queryGroupBuyActivityFactsSourceByActivityId(404L);
    }

    private GroupBuyActivityFactsSourceVO source(ActivityStatusEnumVO status, String start, String end) {
        return GroupBuyActivityFactsSourceVO.builder()
                .activityId(1001L)
                .status(status)
                .startTime(Date.from(Instant.parse(start)))
                .endTime(Date.from(Instant.parse(end)))
                .tagScope("tag-scope")
                .userTakeLimit(2)
                .build();
    }

}
