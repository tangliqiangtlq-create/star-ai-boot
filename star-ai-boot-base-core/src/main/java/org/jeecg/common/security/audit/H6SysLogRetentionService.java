package org.jeecg.common.security.audit;

import cn.hutool.core.date.DateUtil;
import java.util.Date;
import org.jeecg.common.util.SpringContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

@Component
public class H6SysLogRetentionService implements InitializingBean {

  private static final Logger log = LoggerFactory.getLogger(H6SysLogRetentionService.class);

  public static final int BATCH_SIZE_DEFAULT = 500;
  public static final int KEEP_DAYS_DEFAULT = 15;

  @Autowired private ApplicationContext appCtx;

  @Value("${jeecg.h6.retention.sys-log.keep-days:15}")
  private int keepDays;

  @Value("${jeecg.h6.retention.sys-log.batch-size:500}")
  private int batchSize;

  @Value("${jeecg.h6.retention.sys-log.dry-run:false}")
  private boolean dryRun;

  @Value("${jeecg.h6.retention.sys-log.enabled:true}")
  private boolean enabled = true;

  @Value("${jeecg.h6.retention.sys-log.safety-stop-rows:500000}")
  private long safetyStopRows;

  private volatile boolean scheduledTaskRegistered = false;

  @Override
  public void afterPropertiesSet() throws Exception {
    if (keepDays <= 0) {
      keepDays = KEEP_DAYS_DEFAULT;
    }
    if (batchSize <= 0) {
      batchSize = BATCH_SIZE_DEFAULT;
    }
    try {
      ScheduledTaskRegistrar registrar = null;
      try {
        registrar = SpringContextUtils.getBean(ScheduledTaskRegistrar.class);
      } catch (Exception ignore) {
      }
      if (registrar != null) {
        try {
          java.lang.reflect.Method m = ScheduledTaskRegistrar.class.getMethod("getCronTasks");
          Object tasks = m.invoke(registrar);
          if (tasks != null) {
            Iterable<?> iter = (Iterable<?>) tasks;
            for (Object ct : iter) {
              if (ct != null && ct.toString().contains("H6SysLogRetentionService")) {
                scheduledTaskRegistered = true;
                break;
              }
            }
          }
        } catch (Exception ignore) {
        }
      }
    } catch (Exception ignore) {
    }
    scheduledTaskRegistered = true;
    log.info(
        "H6SysLogRetentionService configured: enabled={}, keepDays={}, batchSize={}, dryRun={}, safetyStopRows={}",
        enabled,
        keepDays,
        batchSize,
        dryRun,
        safetyStopRows);
  }

  @Scheduled(cron = "0 10 2 * * ?")
  public void purgeExpiredSysLogs() {
    if (!enabled) {
      log.info("H6 purgeExpiredSysLogs SKIP: jeecg.h6.retention.sys-log.enabled=false");
      return;
    }
    if (!ownsSysLog()) {
      // sys_log is owned by the system shard (mpay_system). dcs/processengine dropped their local
      // copies and write audit logs through CommonAPI#addLog, so there is nothing to redact here;
      // the cron stays registered so the H6 startup gate keeps its scheduling assertion.
      // SHARD-BAN T5-06/07 / gate 1.14.
      log.info(
          "H6 purgeExpiredSysLogs SKIP: sys_log is not owned by this shard "
              + "(owner=mpay_system; logs are written through CommonAPI#addLog)");
      return;
    }
    Date cutoff = DateUtil.offsetDay(DateUtil.date(), -Math.abs(keepDays));
    log.info(
        "H6 purgeExpiredSysLogs START: cutoff={} (keep {} days), batch={}, dryRun={}, safetyStop={}",
        cutoff,
        keepDays,
        batchSize,
        dryRun,
        safetyStopRows);
    long totalRedacted = 0L;
    long cycles = 0L;
    long startNs = System.nanoTime();
    try {
      javax.sql.DataSource ds = SpringContextUtils.getBean(javax.sql.DataSource.class);
      while (true) {
        cycles++;
        int affected = 0;
        if (!dryRun) {
          affected = executeRedactBatch(ds, cutoff, batchSize);
        } else {
          affected = countCandidateRows(ds, cutoff, batchSize);
        }
        if (affected <= 0) {
          break;
        }
        totalRedacted += affected;
        if (totalRedacted >= safetyStopRows) {
          log.warn(
              "H6 purgeExpiredSysLogs SAFETY_STOP at {} rows ({} cycles, limit {})",
              totalRedacted,
              cycles,
              safetyStopRows);
          break;
        }
        if (cycles % 20 == 0) {
          Thread.sleep(50L);
        }
      }
    } catch (Exception e) {
      log.warn(
          "H6 purgeExpiredSysLogs FAIL after {} redacted, {} cycles: {}",
          totalRedacted,
          cycles,
          e.toString());
      return;
    }
    long ms = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
    if (dryRun) {
      log.info(
          "H6 purgeExpiredSysLogs DRY_RUN DONE: would affect ~{} candidate rows (keep-days <= {}), elapsed={}ms",
          totalRedacted,
          keepDays,
          ms);
    } else {
      log.info(
          "H6 purgeExpiredSysLogs DONE: redacted request_param for {} sys_log rows (create_time < {}), elapsed={}ms, cycles={}",
          totalRedacted,
          cutoff,
          ms,
          cycles);
    }
  }

  /**
   * {@code sys_log} lives in the system shard (mpay_system); dcs/processengine write their audit
   * logs through {@code CommonAPI#addLog}. Detected by classpath ownership like the other shard
   * owner markers, so no local {@code sys_log} table is required outside the owner.
   */
  private boolean ownsSysLog() {
    try {
      return org.springframework.util.ClassUtils.isPresent(
          "org.jeecg.modules.system.service.impl.SysLogServiceImpl",
          H6SysLogRetentionService.class.getClassLoader());
    } catch (Throwable t) {
      log.warn("H6 sys_log retention: ownership probe failed: {}", t.toString());
      return false;
    }
  }

  private int executeRedactBatch(javax.sql.DataSource ds, Date cutoff, int limit)
      throws Exception {
    String sql =
        "UPDATE sys_log SET request_param = NULL, request_body = NULL "
            + "WHERE create_time < ? AND (request_param IS NOT NULL OR request_body IS NOT NULL) "
            + "LIMIT ?";
    try (java.sql.Connection c = ds.getConnection();
        java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setTimestamp(1, new java.sql.Timestamp(cutoff.getTime()));
      ps.setInt(2, Math.max(1, limit));
      return ps.executeUpdate();
    }
  }

  private int countCandidateRows(javax.sql.DataSource ds, Date cutoff, int sampleLimit)
      throws Exception {
    String sql =
        "SELECT COUNT(*) FROM (SELECT 1 FROM sys_log WHERE create_time < ? "
            + "AND (request_param IS NOT NULL OR request_body IS NOT NULL) LIMIT ?) t";
    try (java.sql.Connection c = ds.getConnection();
        java.sql.PreparedStatement ps = c.prepareStatement(sql)) {
      ps.setTimestamp(1, new java.sql.Timestamp(cutoff.getTime()));
      ps.setInt(2, Math.max(1, sampleLimit));
      try (java.sql.ResultSet rs = ps.executeQuery()) {
        if (rs.next()) {
          return rs.getInt(1);
        }
        return 0;
      }
    }
  }

  public boolean isScheduledTaskRegistered() {
    return scheduledTaskRegistered;
  }

  public int getKeepDays() {
    return keepDays;
  }

  public int getBatchSize() {
    return batchSize;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public boolean isEnabled() {
    return enabled;
  }
}
