package cn.bugstack.trigger.http;

import cn.bugstack.api.dto.AgentOrderFactsRequestDTO;
import cn.bugstack.api.dto.AgentOrderFactsResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.trade.model.valobj.OrderFactsVO;
import cn.bugstack.domain.trade.service.IOrderFactsService;
import cn.bugstack.trigger.auth.AuthenticatedUserProvider;
import cn.bugstack.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.Optional;

/**
 * Agent-facing, read-only facts API. This controller intentionally does not diagnose the order state.
 */
@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/agent/order")
public class AgentFactsController {

    private final IOrderFactsService orderFactsService;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public AgentFactsController(IOrderFactsService orderFactsService,
                                AuthenticatedUserProvider authenticatedUserProvider) {
        this.orderFactsService = orderFactsService;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    @PostMapping("/facts")
    public Response<AgentOrderFactsResponseDTO> getOrderFacts(@RequestBody(required = false) AgentOrderFactsRequestDTO requestDTO) {
        if (null == requestDTO || StringUtils.isBlank(requestDTO.getOutTradeNo())) {
            return error(ResponseCode.INVALID_ARGUMENT);
        }

        Optional<String> authenticatedUserId = authenticatedUserProvider.getAuthenticatedUserId();
        if (!authenticatedUserId.isPresent()) {
            return error(ResponseCode.AUTH_REQUIRED);
        }

        try {
            OrderFactsVO facts = orderFactsService.getOrderFacts(authenticatedUserId.get(), requestDTO.getOutTradeNo());
            if (null == facts) {
                return error(ResponseCode.ORDER_NOT_FOUND_OR_NOT_AUTHORIZED);
            }
            return Response.<AgentOrderFactsResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(toResponse(facts))
                    .build();
        } catch (Exception e) {
            log.error("Agent order facts query failed for outTradeNo={}", requestDTO.getOutTradeNo(), e);
            return error(ResponseCode.INTERNAL_SERVICE_ERROR);
        }
    }

    private AgentOrderFactsResponseDTO toResponse(OrderFactsVO facts) {
        return AgentOrderFactsResponseDTO.builder()
                .order(AgentOrderFactsResponseDTO.OrderFacts.builder()
                        .status(facts.getOrderStatus())
                        .build())
                .team(AgentOrderFactsResponseDTO.TeamFacts.builder()
                        .status(facts.getTeamStatus())
                        .targetCount(facts.getTargetCount())
                        .lockCount(facts.getLockCount())
                        .completeCount(facts.getCompleteCount())
                        .validEndTime(facts.getValidEndTime())
                        .build())
                .activity(AgentOrderFactsResponseDTO.ActivityFacts.builder()
                        .status(facts.getActivityStatus())
                        .build())
                .references(AgentOrderFactsResponseDTO.References.builder()
                        .teamId(facts.getTeamId())
                        .activityId(facts.getActivityId())
                        .build())
                .build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Response<AgentOrderFactsResponseDTO> handleInvalidRequestBody(HttpMessageNotReadableException ignored) {
        return error(ResponseCode.INVALID_ARGUMENT);
    }

    private Response<AgentOrderFactsResponseDTO> error(ResponseCode code) {
        return Response.<AgentOrderFactsResponseDTO>builder()
                .code(code.getCode())
                .info(code.getInfo())
                .build();
    }

}
