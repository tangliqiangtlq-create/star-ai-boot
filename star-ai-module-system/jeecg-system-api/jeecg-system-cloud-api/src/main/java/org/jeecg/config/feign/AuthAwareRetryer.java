package org.jeecg.config.feign;

import feign.RetryableException;
import feign.Retryer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AuthAwareRetryer extends Retryer.Default {

  private final FeignAuthRetryProperties properties;

  public AuthAwareRetryer(FeignAuthRetryProperties properties) {
    super(properties.getPeriod(), properties.getMaxPeriod(), properties.getMaxAttempts());
    this.properties = properties;
  }

  @Override
  public void continueOrPropagate(RetryableException e) {
    if (properties.isEnabled()) {
      log.warn(
          "Feign got {} @ {} — will backoff retry (check secret + S2S permission matrix)",
          e.status(),
          e.request() == null ? "<null-req>" : e.request().url());
    }
    super.continueOrPropagate(e);
  }
}
