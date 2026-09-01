package cn.bugstack.domain.activity.model.valobj;

import cn.bugstack.types.enums.ActivityStatusEnumVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * Minimal raw projection of a group-buy activity for the Agent facts boundary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupBuyActivityFactsSourceVO {

    private Long activityId;
    private ActivityStatusEnumVO status;
    private Date startTime;
    private Date endTime;
    private String tagId;
    private String tagScope;
    private Integer userTakeLimit;

}
