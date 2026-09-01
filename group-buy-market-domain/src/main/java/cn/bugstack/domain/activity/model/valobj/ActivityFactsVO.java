package cn.bugstack.domain.activity.model.valobj;

import cn.bugstack.types.enums.ActivityStatusEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Read-only activity facts. It contains raw state and one time-window observation only.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityFactsVO {

    private Long activityId;
    private ActivityStatusEnumVO status;
    private Date startTime;
    private Date endTime;
    private String tagScope;
    private Integer userTakeLimit;
    private Date evaluatedAt;
    private Boolean withinValidTime;

}
