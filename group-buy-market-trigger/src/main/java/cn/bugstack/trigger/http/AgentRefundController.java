package cn.bugstack.trigger.http;

import cn.bugstack.api.dto.AgentRefundRequestDTO;
import cn.bugstack.api.dto.AgentRefundResponseDTO;
import cn.bugstack.api.dto.AgentRefundResultRequestDTO;
import cn.bugstack.api.dto.AgentRefundResultResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.trade.model.valobj.AgentRefundResultVO;
import cn.bugstack.domain.trade.service.IAgentRefundService;
import cn.bugstack.trigger.auth.AuthenticatedUserProvider;
import cn.bugstack.types.enums.ResponseCode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Optional;

@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/agent/order")
public class AgentRefundController {
    private final IAgentRefundService agentRefundService;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public AgentRefundController(IAgentRefundService agentRefundService, AuthenticatedUserProvider authenticatedUserProvider) {
        this.agentRefundService = agentRefundService; this.authenticatedUserProvider = authenticatedUserProvider;
    }

    @PostMapping("/refund")
    public Response<AgentRefundResponseDTO> refund(@RequestBody(required = false) AgentRefundRequestDTO request) {
        if (null == request || StringUtils.isAnyBlank(request.getOutTradeNo(), request.getIdempotencyKey(),
                request.getExpectedVersion(), request.getExpectedRefundType()) || !request.getExpectedVersion().contains("|")) {
            return error(ResponseCode.INVALID_ARGUMENT.getCode(), ResponseCode.INVALID_ARGUMENT.getInfo());
        }
        if (!Arrays.asList("UNPAID", "PAID_UNFORMED").contains(request.getExpectedRefundType())) {
            return error(ResponseCode.INVALID_ARGUMENT.getCode(), ResponseCode.INVALID_ARGUMENT.getInfo());
        }
        Optional<String> user = authenticatedUserProvider.getAuthenticatedUserId();
        if (!user.isPresent()) return error(ResponseCode.AUTH_REQUIRED.getCode(), ResponseCode.AUTH_REQUIRED.getInfo());
        AgentRefundResultVO result;
        try {
            result = agentRefundService.refund(user.get(), request.getOutTradeNo(), request.getIdempotencyKey(),
                    request.getExpectedVersion(), request.getExpectedRefundType());
        } catch (Exception ignored) {
            return error(ResponseCode.INTERNAL_SERVICE_ERROR.getCode(), ResponseCode.INTERNAL_SERVICE_ERROR.getInfo());
        }
        return Response.<AgentRefundResponseDTO>builder().code(ResponseCode.SUCCESS.getCode()).info(ResponseCode.SUCCESS.getInfo())
                .data(AgentRefundResponseDTO.builder().status(result.getStatus()).resultCode(result.getResultCode())
                        .refundExecuted(result.getRefundExecuted()).idempotentReplay(result.getIdempotentReplay()).build()).build();
    }

    @PostMapping("/refund/result")
    public Response<AgentRefundResultResponseDTO> result(@RequestBody(required = false) AgentRefundResultRequestDTO request) {
        if (null == request || StringUtils.isBlank(request.getIdempotencyKey())) {
            return resultError(ResponseCode.INVALID_ARGUMENT);
        }
        Optional<String> user = authenticatedUserProvider.getAuthenticatedUserId();
        if (!user.isPresent()) {
            return resultError(ResponseCode.AUTH_REQUIRED);
        }
        AgentRefundResultVO result;
        try {
            result = agentRefundService.queryResult(user.get(), request.getIdempotencyKey());
        } catch (Exception ignored) {
            return resultError(ResponseCode.INTERNAL_SERVICE_ERROR);
        }
        if ("NOT_FOUND".equals(result.getResultCode())) {
            return resultError(ResponseCode.REFUND_RESULT_NOT_FOUND);
        }
        return Response.<AgentRefundResultResponseDTO>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(AgentRefundResultResponseDTO.builder()
                        .status(result.getStatus())
                        .resultCode(result.getResultCode())
                        .refundExecuted(result.getRefundExecuted())
                        .build())
                .build();
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public Response<AgentRefundResponseDTO> invalid(Exception ignored) { return error(ResponseCode.INVALID_ARGUMENT.getCode(), ResponseCode.INVALID_ARGUMENT.getInfo()); }
    private Response<AgentRefundResponseDTO> error(String code, String info) { return Response.<AgentRefundResponseDTO>builder().code(code).info(info).build(); }
    private Response<AgentRefundResultResponseDTO> resultError(ResponseCode code) {
        return Response.<AgentRefundResultResponseDTO>builder().code(code.getCode()).info(code.getInfo()).build();
    }
}
