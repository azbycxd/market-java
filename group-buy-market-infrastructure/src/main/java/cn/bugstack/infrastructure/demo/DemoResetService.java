package cn.bugstack.infrastructure.demo;

import cn.bugstack.domain.demo.model.valobj.DemoResetResultVO;
import cn.bugstack.domain.demo.service.IDemoResetService;
import cn.bugstack.infrastructure.dao.IDemoResetDao;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Restores the fixed single-user interview data. This bean cannot exist outside the demo profile.
 */
@Service
@Profile("demo")
public class DemoResetService implements IDemoResetService {

    public static final String REDIS_RESET_STRATEGY = "TARGETED_DEMO_KEYS";

    private static final String[] DEMO_REDIS_KEY_PATTERNS = {
            "group_buy_market_cn.bugstack.infrastructure.dao.po.GroupBuyActivity_900001",
            "group_buy_market_cn.bugstack.infrastructure.dao.po.GroupBuyDiscount_900001",
            "group_buy_market_team_stock_key_900001_9100000*",
            "refund_lock_94000000000*",
            "notify_job_lock_key_9100000*"
    };

    private final IDemoResetDao demoResetDao;
    private final RedissonClient redissonClient;

    public DemoResetService(IDemoResetDao demoResetDao, RedissonClient redissonClient) {
        this.demoResetDao = demoResetDao;
        this.redissonClient = redissonClient;
    }

    @Override
    @Transactional(timeout = 30)
    public DemoResetResultVO reset(String authenticatedUserId) {
        if (!IDemoResetService.DEMO_USER_ID.equals(authenticatedUserId)) {
            throw new IllegalArgumentException("Demo reset identity is not allowed");
        }

        List<Long> processingIds = demoResetDao.lockProcessingRefundRequestIds(authenticatedUserId);
        if (null != processingIds && !processingIds.isEmpty()) {
            return DemoResetResultVO.builder()
                    .reset(false)
                    .blocked(true)
                    .redisResetStrategy(REDIS_RESET_STRATEGY)
                    .build();
        }

        demoResetDao.deleteRefundRequests(authenticatedUserId);
        demoResetDao.deleteDemoNotifyTasks();
        demoResetDao.restoreDemoTeams();
        demoResetDao.restoreDemoOrders();

        RKeys keys = redissonClient.getKeys();
        long deletedKeys = 0L;
        for (String pattern : DEMO_REDIS_KEY_PATTERNS) {
            deletedKeys += keys.deleteByPattern(pattern);
        }

        return DemoResetResultVO.builder()
                .reset(true)
                .blocked(false)
                .orderCount(4)
                .teamCount(4)
                .redisKeysDeleted(deletedKeys)
                .redisResetStrategy(REDIS_RESET_STRATEGY)
                .build();
    }

}
