package org.jeecg.common.util.log;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;

/**
 * Legacy entry-point retained for binary compatibility. Delegates to {@link
 * H1LogbackTurboConfigurator} which installs {@link H1SensitiveTurboFilter} (covers String +
 * Throwable + gateway + xxljob 4 contexts).
 */
public class SensitiveDataLogbackInstaller
    implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

  @Override
  public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
    H1LogbackTurboConfigurator.installToCurrentContext();
  }

  @Override
  public int getOrder() {
    return LoggingApplicationListener.DEFAULT_ORDER + 1;
  }

  public static void install() {
    H1LogbackTurboConfigurator.installToCurrentContext();
  }
}
