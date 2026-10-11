package org.jeecg.config;

import org.jeecg.common.logging.Log4jdbcSqlOnlySetup;
import org.jeecg.common.util.JdbcLoggingLevels;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.context.ConfigurableApplicationContext;

/** Install log4jdbc SQL-only delegator early and tighten JDBC logger levels after logging is ready. */
public class JdbcLoggingRunListener implements SpringApplicationRunListener {

  static {
    Log4jdbcSqlOnlySetup.install();
  }

  public JdbcLoggingRunListener(SpringApplication application, String[] args) {}

  @Override
  public void contextLoaded(ConfigurableApplicationContext context) {
    JdbcLoggingLevels.applySqlOnly();
  }
}
