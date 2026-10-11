package org.jeecg.common.util.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

/**
 * Legacy wrapper — delegates to {@link H1SensitiveTurboFilter#redactAll(String)} for the
 * single-param redact() contract used by existing callers. The TurboFilter hook itself is
 * superseded by {@link H1SensitiveTurboFilter} installed via {@link H1LogbackTurboConfigurator}.
 */
public class SensitiveDataTurboFilter extends TurboFilter {

  private final H1SensitiveTurboFilter delegate = new H1SensitiveTurboFilter();

  @Override
  public FilterReply decide(
      Marker marker,
      Logger logger,
      Level level,
      String format,
      Object[] params,
      Throwable t) {
    return delegate.decide(marker, logger, level, format, params, t);
  }

  public static String redact(String input) {
    return H1SensitiveTurboFilter.redactAll(input);
  }
}
