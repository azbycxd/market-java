package cn.bugstack.trigger.http;

import cn.bugstack.api.dto.AgentDemoResetRequestDTO;
import cn.bugstack.api.dto.AgentDemoResetResponseDTO;
import cn.bugstack.api.response.Response;
import cn.bugstack.domain.demo.model.valobj.DemoResetResultVO;
import cn.bugstack.domain.demo.service.IDemoResetService;
import cn.bugstack.trigger.auth.AuthenticatedUserProvider;
import cn.bugstack.types.enums.ResponseCode;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/** Internal, demo-profile-only recovery endpoint for the fixed interview dataset. */
@RestController
@Profile("demo")
@RequestMapping("/api/v1/agent/demo")
public class AgentDemoResetController {

    private final IDemoResetService demoResetService;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public AgentDemoResetController(IDemoResetService demoResetService,
                                    AuthenticatedUserProvider authenticatedUserProvider) {
        this.demoResetService = demoResetService;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    @PostMapping("/reset")
    public Response<AgentDemoResetResponseDTO> reset(
            @RequestBody(required = false) AgentDemoResetRequestDTO ignoredRequest) {
        Optional<String> authenticatedUserId = authenticatedUserProvider.getAuthenticatedUserId();
        if (!authenticatedUserId.isPresent()) {
            return error(ResponseCode.AUTH_REQUIRED);
        }
        if (!IDemoResetService.DEMO_USER_ID.equals(authenticatedUserId.get())) {
            return error(ResponseCode.DEMO_RESET_FORBIDDEN);
        }

        try {
            DemoResetResultVO result = demoResetService.reset(authenticatedUserId.get());
            if (Boolean.TRUE.equals(result.getBlocked())) {
                return error(ResponseCode.DEMO_RESET_BLOCKED);
            }
            return Response.<AgentDemoResetResponseDTO>builder()
                    .code(ResponseCode.SUCCESS.getCode())
                    .info(ResponseCode.SUCCESS.getInfo())
                    .data(AgentDemoResetResponseDTO.builder()
                            .reset(result.getReset())
                            .orderCount(result.getOrderCount())
                            .teamCount(result.getTeamCount())
                            .redisKeysDeleted(result.getRedisKeysDeleted())
                            .redisResetStrategy(result.getRedisResetStrategy())
                            .build())
                    .build();
        } catch (Exception ignored) {
            return error(ResponseCode.INTERNAL_SERVICE_ERROR);
        }
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public Response<AgentDemoResetResponseDTO> invalidRequest(Exception ignored) {
        return error(ResponseCode.INVALID_ARGUMENT);
    }

    private Response<AgentDemoResetResponseDTO> error(ResponseCode responseCode) {
        return Response.<AgentDemoResetResponseDTO>builder()
                .code(responseCode.getCode())
                .info(responseCode.getInfo())
                .build();
    }

}
