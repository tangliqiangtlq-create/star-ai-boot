package org.jeecg.config.scheduling;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * C1-4 / P4-1: avoid single-thread {@code @Scheduled} pool contention (security remediation doc 10, section 4).
 *
 * <p>Pool size from Nacos {@code spring.task.scheduling.pool.size} or env
 * {@code SPRING_TASK_SCHEDULING_POOL_SIZE} (default 8).
 */
@Configuration
@ConditionalOnProperty(
    name = "spring.task.scheduling.pool.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class SchedulingPoolConfiguration implements SchedulingConfigurer {

  @Value("${spring.task.scheduling.pool.size:8}")
  private int poolSize;

  @Override
  public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
    ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    scheduler.setPoolSize(Math.max(2, poolSize));
    scheduler.setThreadNamePrefix("mp-sched-");
    scheduler.setWaitForTasksToCompleteOnShutdown(true);
    scheduler.setAwaitTerminationSeconds(30);
    scheduler.initialize();
    taskRegistrar.setTaskScheduler(scheduler);
  }
}
