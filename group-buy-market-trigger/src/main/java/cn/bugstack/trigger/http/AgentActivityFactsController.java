package cn.bugstack.trigger.http;

import cn.bugstack.api.dto.AgentActivityFactsRequestDTO;
import cn.bugstack.api.dto.AgentActivityFactsResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.activity.model.valobj.ActivityFactsVO;
import cn.bugstack.domain.activity.service.IAgentActivityFactsService;
import cn.bugstack.trigger.auth.AuthenticatedUserProvider;
import cn.bugstack.types.enums.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Agent-facing endpoint for raw activity state and a time-window observation.
 */
@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/agent/activity")
public class AgentActivityFactsController {

    private final IAgentActivityFactsService activityFactsService;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public AgentActivityFactsController(IAgentActivityFactsService activityFactsService,
                                        AuthenticatedUserProvider authenticatedUserProvider) {
        this.activityFactsService = activityFactsService;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    @PostMapping("/facts")
    public Response<AgentActivityFactsResponseDTO> getActivityFacts(
            @RequestBody(required = false) AgentActivityFactsRequestDTO requestDTO) {
        if (null == requestDTO || null == requestDTO.getActivityId() || requestDTO.getActivityId() <= 0) {
            return error(ResponseCode.INVALID_ARGUMENT);
        }

        Optional<String> authenticatedUserId = authenticatedUserProvider.getAuthenticatedUserId();
        if (!authenticatedUserId.isPresent()) {
            return error(ResponseCode.AUTH_REQUIRED);
        }

        try {
            ActivityFactsVO facts = activityFactsService.getActivityFacts(requestDTO.getActivityId());
            if (null == facts) {
                return error(ResponseCode.ACTIVITY_NOT_FOUND);
            }
            return Response.<AgentActivityFactsResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(toResponse(facts))
                    .build();
        } catch (Exception e) {
            log.error("Agent activity facts query failed for activityId={}", requestDTO.getActivityId(), e);
            return error(ResponseCode.INTERNAL_SERVICE_ERROR);
        }
    }

    private AgentActivityFactsResponseDTO toResponse(ActivityFactsVO facts) {
        return AgentActivityFactsResponseDTO.builder()
                .activity(AgentActivityFactsResponseDTO.Activity.builder()
                        .activityId(facts.getActivityId())
                        .status(facts.getStatus().name())
                        .startTime(facts.getStartTime())
                        .endTime(facts.getEndTime())
                        .tagScope(facts.getTagScope())
                        .userTakeLimit(facts.getUserTakeLimit())
                        .evaluatedAt(facts.getEvaluatedAt())
                        .withinValidTime(facts.getWithinValidTime())
                        .build())
                .build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Response<AgentActivityFactsResponseDTO> handleInvalidRequestBody(HttpMessageNotReadableException ignored) {
        return error(ResponseCode.INVALID_ARGUMENT);
    }

    private Response<AgentActivityFactsResponseDTO> error(ResponseCode code) {
        return Response.<AgentActivityFactsResponseDTO>builder()
                .code(code.getCode())
                .info(code.getInfo())
                .build();
    }

}
