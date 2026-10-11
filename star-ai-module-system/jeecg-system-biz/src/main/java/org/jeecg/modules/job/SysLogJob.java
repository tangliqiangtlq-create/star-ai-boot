package org.jeecg.modules.job;

import cn.hutool.core.date.DateUtil;
import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.handler.annotation.XxlJob;
import java.util.Date;
import org.jeecg.modules.system.mapper.SysLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SysLogJob {
  private static final Logger log = LoggerFactory.getLogger(SysLogJob.class);

  @Autowired private SysLogMapper sysLogMapper;

  @Value("${jeecg.sys-log.cleanup.keep-days:30}")
  private int keepDays;

  @Value("${jeecg.sys-log.cleanup.batch-size:1000}")
  private int batchSize;

  @Value("${jeecg.sys-log.cleanup.safety-stop-rows:200000}")
  private long safetyStopRows;

  @Value("${jeecg.sys-log.cleanup.enabled:true}")
  private boolean cleanupEnabled;

  @XxlJob(value = "deleteSysLog_job")
  public ReturnT<String> deleteSysLogJob(String params) {
    if (!cleanupEnabled) {
      log.info("{} deleteSysLogJob skipped (jeecg.sys-log.cleanup.enabled=false)", DateUtil.now());
      return new ReturnT<>(ReturnT.SUCCESS_CODE, "skipped");
    }

    Date cutoff = DateUtil.offsetDay(DateUtil.date(), -keepDays);
    log.info(
        "{} deleteSysLogJob start, cutoff={} (keep {} days), batch={}, safetyStop={}",
        DateUtil.now(),
        cutoff,
        keepDays,
        batchSize,
        safetyStopRows);

    long total = 0L;
    try {
      while (true) {
        int deleted = sysLogMapper.deleteBatchBefore(cutoff, batchSize);
        if (deleted <= 0) {
          break;
        }
        total += deleted;
        if (total >= safetyStopRows) {
          log.warn("[SysLogCleanup] safety stop after {} rows", total);
          break;
        }
      }
      log.info("[SysLogCleanup] done, total~{}", total);
      return new ReturnT<>(ReturnT.SUCCESS_CODE, "deleted=" + total);
    } catch (Exception e) {
      log.warn("[SysLogCleanup] fail after {} rows: {}", total, e.toString());
      return new ReturnT<>(ReturnT.FAIL_CODE, e.getMessage());
    }
  }
}
