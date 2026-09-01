package cn.bugstack.test.agent;

import cn.bugstack.domain.activity.adapter.repository.IActivityRepository;
import cn.bugstack.domain.activity.model.valobj.EligibilityFactsVO;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityFactsSourceVO;
import cn.bugstack.domain.activity.service.facts.AgentEligibilityFactsService;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Unit tests for separate raw and deterministic eligibility facts. */
public class AgentEligibilityFactsServiceTest {

    private static final String TRUSTED_USER = "trusted-user";
    private IActivityRepository activityRepository;
    private AgentEligibilityFactsService service;

    @Before
    public void setUp() {
        activityRepository = mock(IActivityRepository.class);
        service = new AgentEligibilityFactsService(activityRepository);
        when(activityRepository.queryOrderCountByActivityId(anyLong(), anyString())).thenReturn(0);
        when(activityRepository.downgradeSwitch()).thenReturn(false);
        when(activityRepository.cutRange(anyString())).thenReturn(true);
    }

    @Test
    public void shouldReturnDefaultsWhenNoTagRuleIsConfigured() {
        when(activityRepository.queryGroupBuyActivityFactsSourceByActivityId(1001L))
                .thenReturn(activity(null, null, 3));

        EligibilityFactsVO facts = service.getEligibilityFacts(TRUSTED_USER, 1001L);

        assertFalse(facts.getTagRuleConfigured());
        assertFalse(facts.getTagCrowdDataAvailable());
        assertTrue(facts.getTagGatePassed());
        assertTrue(facts.getTagVisibilityAllowed());
        assertTrue(facts.getTagParticipationAllowed());
        verify(activityRepository, never()).isTagCrowdDataAvailable(anyString());
        verify(activityRepository, never()).isTagCrowdRange(anyString(), anyString());
    }

    @Test
    public void shouldExposeAvailableCrowdDataAndPassedGateForMatchedUser() {
        configuredTag("1,2", true, true);

        EligibilityFactsVO facts = service.getEligibilityFacts(TRUSTED_USER, 1001L);

        assertTrue(facts.getTagRuleConfigured());
        assertTrue(facts.getTagCrowdDataAvailable());
        assertTrue(facts.getTagGatePassed());
        assertTrue(facts.getTagVisibilityAllowed());
        assertTrue(facts.getTagParticipationAllowed());
    }

    @Test
    public void shouldExposeAvailableCrowdDataAndFailedGateForUnmatchedUser() {
        configuredTag("1,2", true, false);

        EligibilityFactsVO facts = service.getEligibilityFacts(TRUSTED_USER, 1001L);

        assertTrue(facts.getTagCrowdDataAvailable());
        assertFalse(facts.getTagGatePassed());
        assertFalse(facts.getTagVisibilityAllowed());
        assertFalse(facts.getTagParticipationAllowed());
    }

    @Test
    public void shouldPreserveMissingCrowdDataFallbackPass() {
        configuredTag("1,2", false, true);

        EligibilityFactsVO facts = service.getEligibilityFacts(TRUSTED_USER, 1001L);

        assertFalse(facts.getTagCrowdDataAvailable());
        assertTrue(facts.getTagGatePassed());
        assertTrue(facts.getTagVisibilityAllowed());
        assertTrue(facts.getTagParticipationAllowed());
    }

    @Test
    public void shouldPreserveVisibleScopeSemanticsWhenGateFails() {
        configuredTag("1", true, false);

        EligibilityFactsVO facts = service.getEligibilityFacts(TRUSTED_USER, 1001L);

        assertFalse(facts.getTagVisibilityAllowed());
        assertTrue(facts.getTagParticipationAllowed());
    }

    @Test
    public void shouldPreserveParticipationScopeSemanticsWhenGateFails() {
        configuredTag("2", true, false);

        EligibilityFactsVO facts = service.getEligibilityFacts(TRUSTED_USER, 1001L);

        assertTrue(facts.getTagVisibilityAllowed());
        assertFalse(facts.getTagParticipationAllowed());
    }

    @Test
    public void shouldReportLimitNotReachedBelowConfiguredLimit() {
        when(activityRepository.queryGroupBuyActivityFactsSourceByActivityId(1001L))
                .thenReturn(activity(null, null, 3));
        when(activityRepository.queryOrderCountByActivityId(1001L, TRUSTED_USER)).thenReturn(2);

        EligibilityFactsVO facts = service.getEligibilityFacts(TRUSTED_USER, 1001L);

        assertEquals(Integer.valueOf(2), facts.getUserTakeCount());
        assertEquals(Integer.valueOf(3), facts.getUserTakeLimit());
        assertFalse(facts.getParticipationLimitReached());
    }

    @Test
    public void shouldReportLimitReachedAtConfiguredLimit() {
        when(activityRepository.queryGroupBuyActivityFactsSourceByActivityId(1001L))
                .thenReturn(activity(null, null, 3));
        when(activityRepository.queryOrderCountByActivityId(1001L, TRUSTED_USER)).thenReturn(3);

        assertTrue(service.getEligibilityFacts(TRUSTED_USER, 1001L).getParticipationLimitReached());
    }

    @Test
    public void shouldReportLimitReachedAboveConfiguredLimit() {
        when(activityRepository.queryGroupBuyActivityFactsSourceByActivityId(1001L))
                .thenReturn(activity(null, null, 3));
        when(activityRepository.queryOrderCountByActivityId(1001L, TRUSTED_USER)).thenReturn(4);

        assertTrue(service.getEligibilityFacts(TRUSTED_USER, 1001L).getParticipationLimitReached());
    }

    @Test
    public void shouldKeepCurrentUnlimitedRuleWhenTakeLimitIsNull() {
        when(activityRepository.queryGroupBuyActivityFactsSourceByActivityId(1001L))
                .thenReturn(activity(null, null, null));
        when(activityRepository.queryOrderCountByActivityId(1001L, TRUSTED_USER)).thenReturn(99);

        EligibilityFactsVO facts = service.getEligibilityFacts(TRUSTED_USER, 1001L);

        assertNull(facts.getUserTakeLimit());
        assertFalse(facts.getParticipationLimitReached());
    }

    @Test
    public void shouldReturnMarketDowngradedFact() {
        when(activityRepository.queryGroupBuyActivityFactsSourceByActivityId(1001L))
                .thenReturn(activity(null, null, 3));
        when(activityRepository.downgradeSwitch()).thenReturn(true);

        assertTrue(service.getEligibilityFacts(TRUSTED_USER, 1001L).getMarketDowngraded());
    }

    @Test
    public void shouldReturnMarketNotDowngradedFact() {
        when(activityRepository.queryGroupBuyActivityFactsSourceByActivityId(1001L))
                .thenReturn(activity(null, null, 3));

        assertFalse(service.getEligibilityFacts(TRUSTED_USER, 1001L).getMarketDowngraded());
    }

    @Test
    public void shouldReturnUserWithinReleaseRangeFact() {
        when(activityRepository.queryGroupBuyActivityFactsSourceByActivityId(1001L))
                .thenReturn(activity(null, null, 3));
        when(activityRepository.cutRange(TRUSTED_USER)).thenReturn(true);

        assertTrue(service.getEligibilityFacts(TRUSTED_USER, 1001L).getUserWithinReleaseRange());
    }

    @Test
    public void shouldReturnUserOutsideReleaseRangeFact() {
        when(activityRepository.queryGroupBuyActivityFactsSourceByActivityId(1001L))
                .thenReturn(activity(null, null, 3));
        when(activityRepository.cutRange(TRUSTED_USER)).thenReturn(false);

        assertFalse(service.getEligibilityFacts(TRUSTED_USER, 1001L).getUserWithinReleaseRange());
    }

    @Test
    public void shouldReturnNullWhenActivityDoesNotExistBeforeOtherReads() {
        when(activityRepository.queryGroupBuyActivityFactsSourceByActivityId(404L)).thenReturn(null);

        assertNull(service.getEligibilityFacts(TRUSTED_USER, 404L));
        verify(activityRepository).queryGroupBuyActivityFactsSourceByActivityId(404L);
        verifyNoMoreInteractions(activityRepository);
    }

    private void configuredTag(String tagScope, boolean crowdDataAvailable, boolean gatePassed) {
        when(activityRepository.queryGroupBuyActivityFactsSourceByActivityId(1001L))
                .thenReturn(activity("tag-internal-only", tagScope, 3));
        when(activityRepository.isTagCrowdDataAvailable("tag-internal-only")).thenReturn(crowdDataAvailable);
        when(activityRepository.isTagCrowdRange("tag-internal-only", TRUSTED_USER)).thenReturn(gatePassed);
    }

    private GroupBuyActivityFactsSourceVO activity(String tagId, String tagScope, Integer takeLimit) {
        return GroupBuyActivityFactsSourceVO.builder()
                .activityId(1001L)
                .tagId(tagId)
                .tagScope(tagScope)
                .userTakeLimit(takeLimit)
                .build();
    }

}
