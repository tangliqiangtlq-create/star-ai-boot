package org.jeecg.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

/** logback cover: intercept JDBC logs except jdbc.sqlonly. */
public class SqlOnlyJdbcTurboFilter extends TurboFilter {

  @Override
  public FilterReply decide(
      Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
    if (logger == null) {
      return FilterReply.NEUTRAL;
    }
    String name = logger.getName();
    if ("jdbc.sqlonly".equals(name)) {
      return FilterReply.NEUTRAL;
    }
    if (name.startsWith("jdbc.") || name.startsWith("java.sql") || "log4jdbc.debug".equals(name)) {
      return FilterReply.DENY;
    }
    return FilterReply.NEUTRAL;
  }
}
