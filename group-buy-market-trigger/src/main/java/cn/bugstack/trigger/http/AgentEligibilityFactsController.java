package cn.bugstack.trigger.http;

import cn.bugstack.api.dto.AgentEligibilityFactsRequestDTO;
import cn.bugstack.api.dto.AgentEligibilityFactsResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.activity.model.valobj.EligibilityFactsVO;
import cn.bugstack.domain.activity.service.IAgentEligibilityFactsService;
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

/** Agent-facing endpoint for separate, privacy-safe user eligibility facts. */
@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/agent/activity")
public class AgentEligibilityFactsController {

    private final IAgentEligibilityFactsService eligibilityFactsService;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public AgentEligibilityFactsController(IAgentEligibilityFactsService eligibilityFactsService,
                                           AuthenticatedUserProvider authenticatedUserProvider) {
        this.eligibilityFactsService = eligibilityFactsService;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    @PostMapping("/eligibility-facts")
    public Response<AgentEligibilityFactsResponseDTO> getEligibilityFacts(
            @RequestBody(required = false) AgentEligibilityFactsRequestDTO requestDTO) {
        if (null == requestDTO || null == requestDTO.getActivityId() || requestDTO.getActivityId() <= 0) {
            return error(ResponseCode.INVALID_ARGUMENT);
        }

        Optional<String> authenticatedUserId = authenticatedUserProvider.getAuthenticatedUserId();
        if (!authenticatedUserId.isPresent()) {
            return error(ResponseCode.AUTH_REQUIRED);
        }

        try {
            EligibilityFactsVO facts = eligibilityFactsService
                    .getEligibilityFacts(authenticatedUserId.get(), requestDTO.getActivityId());
            if (null == facts) {
                return error(ResponseCode.ACTIVITY_NOT_FOUND);
            }
            return Response.<AgentEligibilityFactsResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(toResponse(facts))
                    .build();
        } catch (Exception e) {
            log.error("Agent eligibility facts query failed for activityId={}", requestDTO.getActivityId(), e);
            return error(ResponseCode.INTERNAL_SERVICE_ERROR);
        }
    }

    private AgentEligibilityFactsResponseDTO toResponse(EligibilityFactsVO facts) {
        return AgentEligibilityFactsResponseDTO.builder()
                .activityId(facts.getActivityId())
                .tagRuleConfigured(facts.getTagRuleConfigured())
                .tagCrowdDataAvailable(facts.getTagCrowdDataAvailable())
                .tagGatePassed(facts.getTagGatePassed())
                .tagVisibilityAllowed(facts.getTagVisibilityAllowed())
                .tagParticipationAllowed(facts.getTagParticipationAllowed())
                .userTakeCount(facts.getUserTakeCount())
                .userTakeLimit(facts.getUserTakeLimit())
                .participationLimitReached(facts.getParticipationLimitReached())
                .marketDowngraded(facts.getMarketDowngraded())
                .userWithinReleaseRange(facts.getUserWithinReleaseRange())
                .build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Response<AgentEligibilityFactsResponseDTO> handleInvalidRequestBody(HttpMessageNotReadableException ignored) {
        return error(ResponseCode.INVALID_ARGUMENT);
    }

    private Response<AgentEligibilityFactsResponseDTO> error(ResponseCode code) {
        return Response.<AgentEligibilityFactsResponseDTO>builder()
                .code(code.getCode())
                .info(code.getInfo())
                .build();
    }

}
