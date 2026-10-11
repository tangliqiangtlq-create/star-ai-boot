package org.jeecg.modules.quartz.job;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.DateUtils;
import org.quartz.*;

/**
 * @Description: synchronous scheduled task test
 *
 * Synchronous here means when task execution time exceeds the task interval,
 * the next run waits until the first task finishes
 *
 *
 * @author: taoyan
 * @date: June 19, 2020
 */
@PersistJobDataAfterExecution
@DisallowConcurrentExecution
@Slf4j
public class AsyncJob implements Job {

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        log.info(" --- SyncJobScheduleStart --- ");
        try {
            // Simulate task duration 5s; cron configured to run every second: 0/1 * * * * ? *
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            log.error("operation failed", e);
        }
        // Test found: runs every 5 seconds
        log.info(" --- Execution completed, time: "+DateUtils.now()+"---");
    }

}
