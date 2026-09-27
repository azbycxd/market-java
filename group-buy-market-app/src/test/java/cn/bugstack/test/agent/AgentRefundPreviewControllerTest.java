package cn.bugstack.test.agent;

import cn.bugstack.domain.trade.model.valobj.RefundPreviewVO;
import cn.bugstack.domain.trade.service.IRefundPreviewService;
import cn.bugstack.trigger.auth.AuthenticatedUserProvider;
import cn.bugstack.trigger.http.AgentRefundPreviewController;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Date;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AgentRefundPreviewControllerTest {

    private IRefundPreviewService refundPreviewService;
    private AuthenticatedUserProvider authenticatedUserProvider;
    private MockMvc mockMvc;

    @Before
    public void setUp() {
        refundPreviewService = mock(IRefundPreviewService.class);
        authenticatedUserProvider = mock(AuthenticatedUserProvider.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AgentRefundPreviewController(refundPreviewService, authenticatedUserProvider)).build();
    }

    @Test
    public void shouldReturnReadOnlyPreview() throws Exception {
        when(authenticatedUserProvider.getAuthenticatedUserId()).thenReturn(Optional.of("user-1"));
        when(refundPreviewService.getRefundPreview("user-1", "trade-1"))
                .thenReturn(RefundPreviewVO.builder()
                        .orderStatus("COMPLETE")
                        .teamStatus("COMPLETE")
                        .refundType("PAID_FORMED")
                        .refundProposalAllowed(true)
                        .requiresManualReview(true)
                        .orderUpdateTime(new Date(1767225600000L))
                        .build());

        mockMvc.perform(post("/api/v1/agent/order/refund/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outTradeNo\":\"trade-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.info").value("成功"))
                .andExpect(jsonPath("$.data.orderStatus").value("COMPLETE"))
                .andExpect(jsonPath("$.data.teamStatus").value("COMPLETE"))
                .andExpect(jsonPath("$.data.refundType").value("PAID_FORMED"))
                .andExpect(jsonPath("$.data.refundProposalAllowed").value(true))
                .andExpect(jsonPath("$.data.requiresManualReview").value(true));

        verify(refundPreviewService).getRefundPreview("user-1", "trade-1");
    }

    @Test
    public void shouldRejectUserIdAndAnyUnexpectedRequestField() throws Exception {
        mockMvc.perform(post("/api/v1/agent/order/refund/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outTradeNo\":\"trade-1\",\"userId\":\"other-user\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));

        verifyNoInteractions(authenticatedUserProvider, refundPreviewService);
    }

    @Test
    public void shouldRequireAuthenticatedJwtSubjectBeforeLookup() throws Exception {
        when(authenticatedUserProvider.getAuthenticatedUserId()).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/agent/order/refund/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outTradeNo\":\"trade-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

        verifyNoInteractions(refundPreviewService);
    }

    @Test
    public void shouldUseOneExternalResponseForMissingAndUnauthorizedOrders() throws Exception {
        when(authenticatedUserProvider.getAuthenticatedUserId()).thenReturn(Optional.of("user-1"));
        when(refundPreviewService.getRefundPreview("user-1", "unknown-or-other-users-trade")).thenReturn(null);

        mockMvc.perform(post("/api/v1/agent/order/refund/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outTradeNo\":\"unknown-or-other-users-trade\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND_OR_NOT_AUTHORIZED"));
    }

}
