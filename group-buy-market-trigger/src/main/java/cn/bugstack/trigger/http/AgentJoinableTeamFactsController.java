package cn.bugstack.trigger.http;

import cn.bugstack.api.dto.AgentJoinableTeamFactsRequestDTO;
import cn.bugstack.api.dto.AgentJoinableTeamFactsResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.activity.model.valobj.JoinableTeamFactsVO;
import cn.bugstack.domain.activity.model.valobj.TeamStatisticVO;
import cn.bugstack.domain.activity.service.IAgentJoinableTeamFactsService;
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
import java.util.stream.Collectors;

/**
 * Agent-facing, read-only endpoint for other joinable teams in an activity.
 */
@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/agent/team")
public class AgentJoinableTeamFactsController {

    private final IAgentJoinableTeamFactsService joinableTeamFactsService;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public AgentJoinableTeamFactsController(IAgentJoinableTeamFactsService joinableTeamFactsService,
                                             AuthenticatedUserProvider authenticatedUserProvider) {
        this.joinableTeamFactsService = joinableTeamFactsService;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    @PostMapping("/joinable-facts")
    public Response<AgentJoinableTeamFactsResponseDTO> getJoinableTeamFacts(
            @RequestBody(required = false) AgentJoinableTeamFactsRequestDTO requestDTO) {
        if (null == requestDTO || null == requestDTO.getActivityId() || requestDTO.getActivityId() <= 0) {
            return error(ResponseCode.INVALID_ARGUMENT);
        }

        Optional<String> authenticatedUserId = authenticatedUserProvider.getAuthenticatedUserId();
        if (!authenticatedUserId.isPresent()) {
            return error(ResponseCode.AUTH_REQUIRED);
        }

        try {
            JoinableTeamFactsVO facts = joinableTeamFactsService
                    .getJoinableTeamFacts(authenticatedUserId.get(), requestDTO.getActivityId());
            return Response.<AgentJoinableTeamFactsResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(toResponse(facts))
                    .build();
        } catch (Exception e) {
            log.error("Agent joinable team facts query failed for activityId={}", requestDTO.getActivityId(), e);
            return error(ResponseCode.INTERNAL_SERVICE_ERROR);
        }
    }

    private AgentJoinableTeamFactsResponseDTO toResponse(JoinableTeamFactsVO facts) {
        TeamStatisticVO statistics = facts.getStatistics();
        return AgentJoinableTeamFactsResponseDTO.builder()
                .activityId(facts.getActivityId())
                .candidateTeams(facts.getCandidateTeams().stream()
                        .map(candidate -> AgentJoinableTeamFactsResponseDTO.CandidateTeam.builder()
                                .teamId(candidate.getTeamId())
                                .targetCount(candidate.getTargetCount())
                                .completeCount(candidate.getCompleteCount())
                                .lockCount(candidate.getLockCount())
                                .validEndTime(candidate.getValidEndTime())
                                .build())
                        .collect(Collectors.toList()))
                .statistics(AgentJoinableTeamFactsResponseDTO.Statistics.builder()
                        .allTeamCount(statistics.getAllTeamCount())
                        .allTeamCompleteCount(statistics.getAllTeamCompleteCount())
                        .allTeamUserCount(statistics.getAllTeamUserCount())
                        .build())
                .build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Response<AgentJoinableTeamFactsResponseDTO> handleInvalidRequestBody(HttpMessageNotReadableException ignored) {
        return error(ResponseCode.INVALID_ARGUMENT);
    }

    private Response<AgentJoinableTeamFactsResponseDTO> error(ResponseCode code) {
        return Response.<AgentJoinableTeamFactsResponseDTO>builder()
                .code(code.getCode())
                .info(code.getInfo())
                .build();
    }

}
