package org.jeecg.common.util;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.IdUtil;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author cVzhanshi
 * @create 2021-08-19 0:06
 */
@Slf4j
@Component
public class IdGeneratorSnowflake {

  private long workerId = 0; // Which computer room is the number?
  private long datacenterId = 1; // Machine number
  private Snowflake snowflake = IdUtil.createSnowflake(workerId, datacenterId);

  @PostConstruct // Start execution after construction and load initialization work
  public void init() {
    try {
      // Get the localipAddresscoding
      workerId = NetUtil.ipv4ToLong(NetUtil.getLocalhostStr());
      log.info("Current machine workerId: " + workerId);
    } catch (Exception e) {
      log.error("operation failed", e);
      log.warn("Failed to obtain current machine workerId ----> " + e);
      workerId = NetUtil.getLocalhostStr().hashCode();
    }
  }

  public synchronized long snowflakeId() {
    return snowflake.nextId();
  }

  public synchronized long snowflakeId(long workerId, long datacenterId) {
    Snowflake snowflake = IdUtil.createSnowflake(workerId, datacenterId);
    return snowflake.nextId();
  }

  /** business freeze/String snowflakes used in idempotent keys and other scenarios ID.  */
  public synchronized String nextIdStr() {
    return String.valueOf(snowflakeId());
  }

  // test

}
