package org.jeecg.common.util;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;

/** unified JDBC Log level: output only SQL（jdbc.sqlonly），shield audit/connection Wait for noise. */
public final class JdbcLoggingLevels {

  private JdbcLoggingLevels() {}

  public static void applySqlOnly() {
    if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext context)) {
      return;
    }
    setLevel(context, "jdbc", Level.OFF);
    setLevel(context, "jdbc.sqlonly", Level.WARN);
    setLevel(context, "jdbc.audit", Level.OFF);
    setLevel(context, "jdbc.connection", Level.OFF);
    setLevel(context, "jdbc.sqltiming", Level.OFF);
    setLevel(context, "jdbc.resultset", Level.OFF);
    setLevel(context, "jdbc.resultsettable", Level.OFF);
    setLevel(context, "java.sql", Level.OFF);
    setLevel(context, "com.apache.ibatis", Level.WARN);
    org.jeecg.common.env.H5JdbcSqlOnlyLevelGuard.applyLogbackLoggerLevel();
  }

  private static void setLevel(LoggerContext context, String name, Level level) {
    Logger logger = context.getLogger(name);
    logger.setLevel(level);
  }
}
