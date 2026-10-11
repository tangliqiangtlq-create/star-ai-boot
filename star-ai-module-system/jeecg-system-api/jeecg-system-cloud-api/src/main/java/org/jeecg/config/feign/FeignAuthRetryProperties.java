package org.jeecg.config.feign;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "jeecg.feign.auth-retry")
public class FeignAuthRetryProperties {

  private boolean enabled = true;
  private int maxAttempts = 10;
  private long period = 200;
  private long maxPeriod = 2000;
}
