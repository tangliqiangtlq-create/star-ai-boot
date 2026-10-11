package org.jeecg.common.security.audit;

import static org.junit.Assert.*;

import org.junit.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.annotation.Scheduled;

public class H6SysLogRetentionServiceTest {

  @Test
  public void T2_09_isInitializingBean_failFastContract() {
    Class<?> svc = H6SysLogRetentionService.class;
    assertTrue("Retention service must implement InitializingBean for startup verifier contract",
        InitializingBean.class.isAssignableFrom(svc));
    assertTrue("@Component stereotype present",
        svc.isAnnotationPresent(org.springframework.stereotype.Component.class));
  }

  @Test
  public void T2_07_cronExpression_is_02_10_AM_and_batch500_keep15days_defaults() throws Exception {
    java.lang.reflect.Method purge = H6SysLogRetentionService.class.getMethod("purgeExpiredSysLogs");
    assertTrue("purgeExpiredSysLogs method exists", purge != null);
    Scheduled sched = purge.getAnnotation(Scheduled.class);
    assertNotNull("@Scheduled annotation present", sched);
    assertEquals("cron must be 0 10 2 * * ? (daily 02:10)", "0 10 2 * * ?", sched.cron());

    assertEquals("BATCH_SIZE_DEFAULT=500", 500, H6SysLogRetentionService.BATCH_SIZE_DEFAULT);
    assertEquals("KEEP_DAYS_DEFAULT=15 (GDPR Art.5(1)(e))", 15, H6SysLogRetentionService.KEEP_DAYS_DEFAULT);
  }

  @Test
  public void T2_09_defaults_matchesNFR_keep15_batch500_dryRunFalse() throws Exception {
    H6SysLogRetentionService svc = new H6SysLogRetentionService();
    try {
      svc.afterPropertiesSet();
    } catch (Exception ignore) {
    }
    assertEquals("getKeepDays() default=15", 15, svc.getKeepDays());
    assertEquals("getBatchSize() default=500", 500, svc.getBatchSize());
    assertFalse("dryRun default=false (production must actually purge)", svc.isDryRun());
    assertTrue("enabled default=true", svc.isEnabled());
  }

  @Test
  public void T2_08_scheduledTaskRegistered_afterAfterPropertiesSet() throws Exception {
    H6SysLogRetentionService svc = new H6SysLogRetentionService();
    try {
      svc.afterPropertiesSet();
    } catch (Exception ignore) {
    }
    assertTrue("scheduledTaskRegistered flag must become true after afterPropertiesSet",
        svc.isScheduledTaskRegistered());
  }
}
