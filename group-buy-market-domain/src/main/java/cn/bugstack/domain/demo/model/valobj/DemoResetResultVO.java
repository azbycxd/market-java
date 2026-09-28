package cn.bugstack.domain.demo.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoResetResultVO {

    private Boolean reset;
    private Boolean blocked;
    private Integer orderCount;
    private Integer teamCount;
    private Long redisKeysDeleted;
    private String redisResetStrategy;

}
