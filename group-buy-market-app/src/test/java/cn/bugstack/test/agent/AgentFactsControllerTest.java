package cn.bugstack.test.agent;

import cn.bugstack.domain.trade.model.valobj.OrderFactsVO;
import cn.bugstack.domain.trade.service.IOrderFactsService;
import cn.bugstack.trigger.auth.AuthenticatedUserProvider;
import cn.bugstack.trigger.http.AgentFactsController;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Date;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AgentFactsControllerTest {

    private IOrderFactsService orderFactsService;
    private AuthenticatedUserProvider authenticatedUserProvider;
    private MockMvc mockMvc;

    @Before
    public void setUp() {
        orderFactsService = mock(IOrderFactsService.class);
        authenticatedUserProvider = mock(AuthenticatedUserProvider.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AgentFactsController(orderFactsService, authenticatedUserProvider)).build();
    }

    @Test
    public void shouldReturnSafeFactsFromTheApi() throws Exception {
        when(authenticatedUserProvider.getAuthenticatedUserId()).thenReturn(Optional.of("user-1"));
        when(orderFactsService.getOrderFacts(eq("user-1"), eq("trade-1"))).thenReturn(OrderFactsVO.builder()
                .orderStatus("COMPLETE")
                .teamStatus("PROGRESS")
                .targetCount(3)
                .lockCount(2)
                .completeCount(2)
                .validEndTime(new Date(1767225600000L))
                .activityStatus("EFFECTIVE")
                .teamId("team-1")
                .activityId(1001L)
                .build());

        MvcResult result = mockMvc.perform(post("/api/v1/agent/order/facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outTradeNo\":\"trade-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.order.status").value("COMPLETE"))
                .andExpect(jsonPath("$.data.team.status").value("PROGRESS"))
                .andExpect(jsonPath("$.data.team.targetCount").value(3))
                .andExpect(jsonPath("$.data.activity.status").value("EFFECTIVE"))
                .andExpect(jsonPath("$.data.references.teamId").value("team-1"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("userId"));
        assertFalse(body.contains("notifyUrl"));
        assertFalse(body.contains("parameterJson"));
        assertFalse(body.contains("reasonCode"));
        verify(orderFactsService).getOrderFacts("user-1", "trade-1");
    }

    @Test
    public void shouldRejectBlankOutTradeNoBeforeAnyLookup() throws Exception {
        mockMvc.perform(post("/api/v1/agent/order/facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outTradeNo\":\"  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

        verifyNoInteractions(authenticatedUserProvider, orderFactsService);
    }

    @Test
    public void shouldReturnInvalidArgumentForMissingOrMalformedBody() throws Exception {
        mockMvc.perform(post("/api/v1/agent/order/facts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

        mockMvc.perform(post("/api/v1/agent/order/facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

        verifyNoInteractions(authenticatedUserProvider, orderFactsService);
    }

    @Test
    public void shouldUseTheSameExternalErrorForMissingAndOtherUsersOrder() throws Exception {
        when(authenticatedUserProvider.getAuthenticatedUserId()).thenReturn(Optional.of("user-1"));
        when(orderFactsService.getOrderFacts("user-1", "unknown-or-other-users-trade")).thenReturn(null);

        mockMvc.perform(post("/api/v1/agent/order/facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outTradeNo\":\"unknown-or-other-users-trade\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND_OR_NOT_AUTHORIZED"));
    }

    @Test
    public void shouldRequireAuthentication() throws Exception {
        when(authenticatedUserProvider.getAuthenticatedUserId()).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/agent/order/facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outTradeNo\":\"trade-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        verifyNoInteractions(orderFactsService);
    }

    @Test
    public void shouldHideUnexpectedExceptionDetails() throws Exception {
        when(authenticatedUserProvider.getAuthenticatedUserId()).thenReturn(Optional.of("user-1"));
        when(orderFactsService.getOrderFacts("user-1", "trade-1"))
                .thenThrow(new IllegalStateException("database implementation detail"));

        MvcResult result = mockMvc.perform(post("/api/v1/agent/order/facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outTradeNo\":\"trade-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVICE_ERROR"))
                .andReturn();

        assertFalse(result.getResponse().getContentAsString().contains("database implementation detail"));
    }

}
