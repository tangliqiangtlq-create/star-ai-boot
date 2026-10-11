package com.xxl.job.admin;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.jeecg.common.env.RepoRootEnvLoader;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * @author xuxueli 2018-10-28 00:38:13
 */
@SpringBootApplication
public class XxlJobAdminApplication {

  public static void main(String[] args) {
    // Boot 2.7 logging system expects SLF4J 1.7 StaticLoggerBinder.
    // Keep startup compatible when project uses SLF4J 2.x + Logback 1.3.x.
    System.setProperty("org.springframework.boot.logging.LoggingSystem", "none");
    // IDE does not load repo-root .env; populate MYSQL_* before Spring binds datasource.
    // Shared implementation comes from jeecg-boot-starter-secrets (also used by the gateway).
    RepoRootEnvLoader.load();
    forceInfoLogLevel();
    SpringApplication.run(XxlJobAdminApplication.class, args);
  }

  private static void forceInfoLogLevel() {
    if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext)) {
      return;
    }
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(Level.INFO);
    context.getLogger("org.springframework").setLevel(Level.INFO);
    context.getLogger("com.xxl").setLevel(Level.INFO);
    context.getLogger("io.grpc").setLevel(Level.INFO);
  }
}
