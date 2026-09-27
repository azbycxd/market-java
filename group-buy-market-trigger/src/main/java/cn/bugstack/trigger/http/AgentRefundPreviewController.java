package cn.bugstack.trigger.http;

import cn.bugstack.api.dto.AgentRefundPreviewRequestDTO;
import cn.bugstack.api.dto.AgentRefundPreviewResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.trade.model.valobj.RefundPreviewVO;
import cn.bugstack.domain.trade.service.IRefundPreviewService;
import cn.bugstack.trigger.auth.AuthenticatedUserProvider;
import cn.bugstack.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Agent-only, read-only refund precheck endpoint. Internal JWT protection is applied by the
 * existing /api/v1/agent/** filter; this controller receives its identity through that chain.
 */
@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/agent/order/refund")
public class AgentRefundPreviewController {

    private final IRefundPreviewService refundPreviewService;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public AgentRefundPreviewController(IRefundPreviewService refundPreviewService,
                                        AuthenticatedUserProvider authenticatedUserProvider) {
        this.refundPreviewService = refundPreviewService;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    @PostMapping("/preview")
    public Response<AgentRefundPreviewResponseDTO> preview(@RequestBody(required = false) AgentRefundPreviewRequestDTO requestDTO) {
        if (null == requestDTO || StringUtils.isBlank(requestDTO.getOutTradeNo())) {
            return error(ResponseCode.INVALID_ARGUMENT);
        }

        Optional<String> authenticatedUserId = authenticatedUserProvider.getAuthenticatedUserId();
        if (!authenticatedUserId.isPresent()) {
            return error(ResponseCode.AUTH_REQUIRED);
        }

        try {
            RefundPreviewVO preview = refundPreviewService.getRefundPreview(authenticatedUserId.get(), requestDTO.getOutTradeNo());
            if (null == preview) {
                return error(ResponseCode.ORDER_NOT_FOUND_OR_NOT_AUTHORIZED);
            }
            return Response.<AgentRefundPreviewResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(AgentRefundPreviewResponseDTO.builder()
                            .orderStatus(preview.getOrderStatus())
                            .teamStatus(preview.getTeamStatus())
                            .refundType(preview.getRefundType())
                            .refundProposalAllowed(preview.getRefundProposalAllowed())
                            .requiresManualReview(preview.getRequiresManualReview())
                            .orderUpdateTime(preview.getOrderUpdateTime())
                            .teamUpdateTime(preview.getTeamUpdateTime())
                            .build())
                    .build();
        } catch (Exception e) {
            log.error("Agent refund preview query failed for outTradeNo={}", requestDTO.getOutTradeNo(), e);
            return error(ResponseCode.INTERNAL_SERVICE_ERROR);
        }
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public Response<AgentRefundPreviewResponseDTO> handleInvalidRequestBody(Exception ignored) {
        return error(ResponseCode.INVALID_ARGUMENT);
    }

    private Response<AgentRefundPreviewResponseDTO> error(ResponseCode code) {
        return Response.<AgentRefundPreviewResponseDTO>builder()
                .code(code.getCode())
                .info(code.getInfo())
                .build();
    }

}
