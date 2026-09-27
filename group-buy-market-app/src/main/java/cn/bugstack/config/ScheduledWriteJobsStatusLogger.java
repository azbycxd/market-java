package cn.bugstack.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * Emits an explicit startup record when both write-capable scheduled jobs are intentionally
 * excluded, so an agent-dev run can be audited without invoking either job.
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = {"group-buy-market.jobs.timeout-refund.enabled", "group-buy-market.jobs.group-buy-notify.enabled"},
        havingValue = "false")
public class ScheduledWriteJobsStatusLogger {

    @PostConstruct
    public void logDisabledJobs() {
        log.info("Scheduled write jobs disabled: TimeoutRefundJob=false, GroupBuyNotifyJob=false");
    }

}
