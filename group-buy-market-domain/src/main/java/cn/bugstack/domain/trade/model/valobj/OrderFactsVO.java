package cn.bugstack.domain.trade.model.valobj;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Read-only facts in the authenticated user's order scope. It intentionally carries no diagnosis.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderFactsVO {

    private String orderStatus;
    private String teamStatus;
    private Integer targetCount;
    private Integer lockCount;
    private Integer completeCount;
    private Date validEndTime;
    private String activityStatus;
    private String teamId;
    private Long activityId;

}
