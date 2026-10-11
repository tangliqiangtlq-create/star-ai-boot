package org.jeecg.common.util.log;

import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;

public class H1LogbackTurboConfigurator
    implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

  private static volatile boolean installed;

  @Override
  public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
    installToCurrentContext();
  }

  @Override
  public int getOrder() {
    return LoggingApplicationListener.DEFAULT_ORDER + 1;
  }

  public static void installToCurrentContext() {
    if (installed) {
      return;
    }
    if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
      return;
    }
    synchronized (H1LogbackTurboConfigurator.class) {
      if (installed) {
        return;
      }
      boolean hasH1 =
          context.getTurboFilterList().stream()
              .anyMatch(f -> f instanceof H1SensitiveTurboFilter);
      if (!hasH1) {
        H1SensitiveTurboFilter h1 = new H1SensitiveTurboFilter();
        h1.start();
        context.addTurboFilter(h1);
      }
      boolean hasLegacy =
          context.getTurboFilterList().stream()
              .anyMatch(f -> f instanceof SensitiveDataTurboFilter);
      if (hasLegacy) {
        context.getTurboFilterList().removeIf(f -> f instanceof SensitiveDataTurboFilter);
      }
      installed = true;
    }
  }

  public static boolean isInstalled() {
    return installed;
  }

  public static void resetForTest() {
    installed = false;
    if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context) {
      context.getTurboFilterList().removeIf(f -> f instanceof H1SensitiveTurboFilter);
    }
  }
}
