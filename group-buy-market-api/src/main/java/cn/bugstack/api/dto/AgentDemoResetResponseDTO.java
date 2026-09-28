package cn.bugstack.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDemoResetResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Boolean reset;
    private Integer orderCount;
    private Integer teamCount;
    private Long redisKeysDeleted;
    private String redisResetStrategy;

}
