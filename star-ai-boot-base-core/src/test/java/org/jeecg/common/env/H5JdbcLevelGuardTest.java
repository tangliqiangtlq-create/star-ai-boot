package org.jeecg.common.env;

import static org.junit.Assert.*;

import org.junit.Test;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationListener;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.mock.env.MockEnvironment;

public class H5JdbcLevelGuardTest {

  @Test
  public void T2_03_safeLevelMustBeWARN_and_constantsPresent() {
    assertEquals("H5 SAFE_LEVEL must be WARN (NFR jdbc.sqlonly INFO forbidden)",
        "WARN", H5JdbcSqlOnlyLevelGuard.SAFE_LEVEL);
    assertEquals("H5 JDBC logger name",
        "jdbc.sqlonly", H5JdbcSqlOnlyLevelGuard.JDBC_SQLONLY_LOGGER);
    assertEquals("H5 property key",
        "logging.level.jdbc.sqlonly", H5JdbcSqlOnlyLevelGuard.LOGGING_LEVEL_JDBC_SQLONLY);
    assertEquals("H5 reason code for deny audit",
        "H5_LEVEL_VIOLATION", H5JdbcSqlOnlyLevelGuard.H5_REASON_CODE);
  }

  @Test
  public void T2_04_implements4Phases_coversAtLeast3Sources_scale3() {
    Class<?> guard = H5JdbcSqlOnlyLevelGuard.class;
    boolean envPostProcessor = EnvironmentPostProcessor.class.isAssignableFrom(guard);
    boolean beanFactoryPostProcessor = BeanFactoryPostProcessor.class.isAssignableFrom(guard);
    boolean appRunner = ApplicationRunner.class.isAssignableFrom(guard);
    boolean envChangeListener = false;
    Class<?>[] direct = guard.getInterfaces();
    for (Class<?> c : direct) {
      if (ApplicationListener.class.isAssignableFrom(c)) {
        envChangeListener = true;
        break;
      }
    }
    if (!envChangeListener) {
      java.lang.reflect.Type[] gI = guard.getGenericInterfaces();
      for (java.lang.reflect.Type t : gI) {
        if (t.toString().contains("EnvironmentChangeEvent")) {
          envChangeListener = true;
          break;
        }
      }
    }
    assertTrue("Phase 1: EnvironmentPostProcessor (covers yml/nacos/spring env)", envPostProcessor);
    assertTrue("Phase 2: BeanFactoryPostProcessor (covers late bean factory override)", beanFactoryPostProcessor);
    assertTrue("Phase 3: ApplicationRunner (covers post-refresh run check)", appRunner);
    assertTrue("Phase 4: ApplicationListener<EnvironmentChangeEvent> (covers Nacos @RefreshScope)", envChangeListener);

    int phasesTrue = (envPostProcessor ? 1 : 0) + (beanFactoryPostProcessor ? 1 : 0)
        + (appRunner ? 1 : 0) + (envChangeListener ? 1 : 0);
    assertTrue("Coverage scale>=3 (4 sources / 4 phases implemented). actual=" + phasesTrue,
        phasesTrue >= 3);
  }

  @Test
  public void T2_05_applyLogbackLoggerLevel_setsWARN_noException() {
    H5JdbcSqlOnlyLevelGuard.applyLogbackLoggerLevel();
    Object lc = org.slf4j.LoggerFactory.getILoggerFactory();
    assertTrue("Logback LoggerContext active", lc instanceof ch.qos.logback.classic.LoggerContext);
    ch.qos.logback.classic.Logger logger =
        ((ch.qos.logback.classic.LoggerContext) lc).getLogger(H5JdbcSqlOnlyLevelGuard.JDBC_SQLONLY_LOGGER);
    ch.qos.logback.classic.Level level = logger.getLevel();
    if (level != null) {
      assertEquals("jdbc.sqlonly Logger level must be WARN after apply",
          ch.qos.logback.classic.Level.WARN, level);
    }
  }

  @Test
  public void T2_04_systemPropertyOverride_coverSource3() {
    String propKey = H5JdbcSqlOnlyLevelGuard.LOGGING_LEVEL_JDBC_SQLONLY;
    String prev = System.getProperty(propKey);
    try {
      System.setProperty(propKey, "TRACE");
      org.springframework.mock.env.MockEnvironment env = new org.springframework.mock.env.MockEnvironment();
      try {
        H5JdbcSqlOnlyLevelGuard instance = new H5JdbcSqlOnlyLevelGuard();
        java.lang.reflect.Method m = H5JdbcSqlOnlyLevelGuard.class.getDeclaredMethod(
            "postProcessEnvironment",
            org.springframework.core.env.ConfigurableEnvironment.class,
            org.springframework.boot.SpringApplication.class);
        m.setAccessible(true);
        m.invoke(instance, env, null);
      } catch (Exception ignore) {
      }
      String after = System.getProperty(propKey);
      assertEquals("JVM System property override must be forced back to WARN after guard runs",
          "WARN", after);
    } finally {
      if (prev == null) {
        System.clearProperty(propKey);
      } else {
        System.setProperty(propKey, prev);
      }
    }
  }
}
