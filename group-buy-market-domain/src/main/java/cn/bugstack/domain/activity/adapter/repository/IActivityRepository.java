package cn.bugstack.domain.activity.adapter.repository;

import cn.bugstack.domain.activity.model.entity.UserGroupBuyOrderDetailEntity;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityDiscountVO;
import cn.bugstack.domain.activity.model.valobj.GroupBuyActivityFactsSourceVO;
import cn.bugstack.domain.activity.model.valobj.SCSkuActivityVO;
import cn.bugstack.domain.activity.model.valobj.SkuVO;
import cn.bugstack.domain.activity.model.valobj.TeamStatisticVO;

import java.util.List;

/**
 * @author Fuzhengwei bugstack.cn @小傅哥
 * @description 活动仓储
 * @create 2024-12-21 10:06
 */
public interface IActivityRepository {

    GroupBuyActivityDiscountVO queryGroupBuyActivityDiscountVO(Long activityId);

    /**
     * Reads the activity row without applying the marketing-flow EFFECTIVE-status filter.
     * This is intentionally a small, read-only source projection for Agent activity facts.
     */
    GroupBuyActivityFactsSourceVO queryGroupBuyActivityFactsSourceByActivityId(Long activityId);

    /** Counts all order-list records for the current user and activity, matching the lock-rule SQL. */
    Integer queryOrderCountByActivityId(Long activityId, String userId);

    SkuVO querySkuByGoodsId(String goodsId);

    SCSkuActivityVO querySCSkuActivityBySCGoodsId(String source, String channel, String goodsId);

    boolean isTagCrowdRange(String tagId, String userId);

    /** Whether a tag crowd data set currently exists; no member data is exposed. */
    boolean isTagCrowdDataAvailable(String tagId);

    boolean downgradeSwitch();

    boolean cutRange(String userId);

    List<UserGroupBuyOrderDetailEntity> queryInProgressUserGroupBuyOrderDetailListByOwner(Long activityId, String userId, Integer ownerCount);

    List<UserGroupBuyOrderDetailEntity> queryInProgressUserGroupBuyOrderDetailListByRandom(Long activityId, String userId, Integer randomCount);

    TeamStatisticVO queryTeamStatisticByActivityId(Long activityId);

}
