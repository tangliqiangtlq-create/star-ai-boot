package org.jeecg.common.env;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import cn.hutool.core.util.IdUtil;
import java.net.InetAddress;
import java.util.Date;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.api.dto.DenyAuditDTO;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class H5JdbcSqlOnlyLevelGuard
    implements EnvironmentPostProcessor,
        ApplicationListener<EnvironmentChangeEvent>,
        BeanFactoryPostProcessor,
        ApplicationRunner,
        ApplicationContextAware {

  private static final org.slf4j.Logger log =
      LoggerFactory.getLogger(H5JdbcSqlOnlyLevelGuard.class);

  public static final String JDBC_SQLONLY_LOGGER = "jdbc.sqlonly";
  public static final String LOGGING_LEVEL_JDBC_SQLONLY = "logging.level.jdbc.sqlonly";
  public static final String SAFE_LEVEL = "WARN";
  public static final String H5_REASON_CODE = "H5_LEVEL_VIOLATION";

  private static volatile boolean guardArmed = false;
  private static volatile ApplicationContext appCtx;
  private static final Object MUTEX = new Object();

  private static final java.util.concurrent.atomic.AtomicLong violationCount =
      new java.util.concurrent.atomic.AtomicLong(0L);

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    synchronized (MUTEX) {
      forceLoggingLevelProperty(environment);
      applyLogbackLoggerLevel();
      guardArmed = true;
    }
    log.info(
        "H5JdbcSqlOnlyLevelGuard ARM (EnvironmentPostProcessor): {}={} via MutablePropertySources override",
        LOGGING_LEVEL_JDBC_SQLONLY,
        SAFE_LEVEL);
  }

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    synchronized (MUTEX) {
      ConfigurableEnvironment env =
          beanFactory.getBean(ConfigurableEnvironment.class);
      forceLoggingLevelProperty(env);
      applyLogbackLoggerLevel();
    }
    log.debug("H5JdbcSqlOnlyLevelGuard re-armed via BeanFactoryPostProcessor");
  }

  @Override
  public void onApplicationEvent(EnvironmentChangeEvent event) {
    if (event == null || event.getKeys() == null) {
      return;
    }
    boolean touched = false;
    for (String key : event.getKeys()) {
      if (key != null
          && (key.equals(LOGGING_LEVEL_JDBC_SQLONLY)
              || key.startsWith("logging.level.")
              || key.equals("jdbc.sqlonly")
              || key.contains("logback"))) {
        touched = true;
        break;
      }
    }
    if (!touched) {
      return;
    }
    synchronized (MUTEX) {
      ApplicationContext ctx = appCtx;
      if (ctx != null && ctx.getEnvironment() instanceof ConfigurableEnvironment) {
        forceLoggingLevelProperty((ConfigurableEnvironment) ctx.getEnvironment());
      }
      String current = readResolvedLevel();
      if (!SAFE_LEVEL.equalsIgnoreCase(current)) {
        recordViolation("RefreshScope/Nacos dynamic drift", current);
      }
      applyLogbackLoggerLevel();
    }
  }

  @Override
  public void run(ApplicationArguments args) {
    synchronized (MUTEX) {
      ApplicationContext ctx = appCtx;
      if (ctx != null && ctx.getEnvironment() instanceof ConfigurableEnvironment) {
        forceLoggingLevelProperty((ConfigurableEnvironment) ctx.getEnvironment());
      }
      applyLogbackLoggerLevel();
      String effective = readResolvedLevel();
      if (!SAFE_LEVEL.equalsIgnoreCase(effective)) {
        recordViolation("Startup application.yml/nacos baseline not WARN", effective);
      }
      log.info(
          "H5JdbcSqlOnlyLevelGuard POST-RUN: {} resolved='{}' (expected WARN), jdbc.sqlonly Logger level={}",
          LOGGING_LEVEL_JDBC_SQLONLY,
          effective,
          getLogbackLoggerLevelName());
    }
  }

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    appCtx = applicationContext;
  }

  public static String getSafeLevel() {
    return SAFE_LEVEL;
  }

  public static long getViolationCount() {
    return violationCount.get();
  }

  public static boolean isGuardArmed() {
    return guardArmed;
  }

  private static void forceLoggingLevelProperty(ConfigurableEnvironment env) {
    MutablePropertySources sources = env.getPropertySources();
    String sourceName = "h5JdbcLevelGuardOverride";
    java.util.Map<String, Object> map = new java.util.HashMap<>(2);
    map.put(LOGGING_LEVEL_JDBC_SQLONLY, SAFE_LEVEL);
    MapPropertySource override = new MapPropertySource(sourceName, map);
    if (sources.contains(sourceName)) {
      sources.remove(sourceName);
    }
    if (sources.contains("systemProperties")) {
      sources.addBefore("systemProperties", override);
    } else {
      sources.addFirst(override);
    }
    String sysprop = System.getProperty(LOGGING_LEVEL_JDBC_SQLONLY);
    if (sysprop != null && !SAFE_LEVEL.equalsIgnoreCase(sysprop)) {
      System.setProperty(LOGGING_LEVEL_JDBC_SQLONLY, SAFE_LEVEL);
    }
  }

  private static String readResolvedLevel() {
    try {
      ApplicationContext ctx = appCtx;
      if (ctx != null) {
        String v = ctx.getEnvironment().getProperty(LOGGING_LEVEL_JDBC_SQLONLY);
        if (v != null && !v.isBlank()) {
          return v.trim();
        }
      }
    } catch (Exception ignore) {
    }
    String s = System.getProperty(LOGGING_LEVEL_JDBC_SQLONLY);
    if (s != null && !s.isBlank()) {
      return s.trim();
    }
    return getLogbackLoggerLevelName();
  }

  private static String getLogbackLoggerLevelName() {
    try {
      if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext ctx)) {
        return "UNKNOWN";
      }
      Logger l = ctx.getLogger(JDBC_SQLONLY_LOGGER);
      Level lv = l.getLevel();
      return lv == null ? "INHERITED" : lv.toString();
    } catch (Exception e) {
      return "ERR:" + e;
    }
  }

  public static void applyLogbackLoggerLevel() {
    try {
      if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext ctx)) {
        return;
      }
      Level target;
      try {
        target = Level.valueOf(SAFE_LEVEL);
      } catch (Exception e) {
        target = Level.WARN;
      }
      ctx.getLogger(JDBC_SQLONLY_LOGGER).setLevel(target);
    } catch (Exception e) {
      log.warn("H5 applyLogbackLoggerLevel fail: {}", e.toString());
    }
  }

  private static void recordViolation(String source, String actual) {
    long n = violationCount.incrementAndGet();
    String eventId = "h5-" + IdUtil.fastSimpleUUID().substring(0, 10);
    try {
      writeDenyAuditRow(source, actual, eventId);
    } catch (Exception e) {
      log.warn(
          "H5 violation #{} record deny_audit fail (non-blocking): source={}, actual={}, eventId={}, err={}",
          n,
          source,
          actual,
          eventId,
          e.toString());
    }
    log.warn(
        "[H5_VIOLATION #{}] source='{}', {} actual='{}', expected='{}', eventId={}",
        n,
        source,
        LOGGING_LEVEL_JDBC_SQLONLY,
        actual,
        SAFE_LEVEL,
        eventId);
  }

  private static void writeDenyAuditRow(String source, String actual, String eventId)
      throws Exception {
    ApplicationContext ctx = appCtx;
    if (ctx == null) {
      return;
    }
    CommonAPI commonAPI;
    try {
      commonAPI = ctx.getBean(CommonAPI.class);
    } catch (Exception noApi) {
      log.debug("H5 violation skip persist: CommonAPI unavailable, eventId={}", eventId);
      return;
    }
    String host = "unknown";
    try {
      host = InetAddress.getLocalHost().getHostName();
    } catch (Exception ignore) {
    }
    String ip = "127.0.0.1";
    try {
      ip = InetAddress.getLocalHost().getHostAddress();
    } catch (Exception ignore) {
    }
    // sys_deny_audit is owned by the system shard (mpay_system). Route through CommonAPI so the
    // owner performs the write: in-process in the system service, over Feign from dcs /
    // processengine (SHARD-BAN T5-06/07, gate 1.14) — a local mirror is deliberately not required.
    DenyAuditDTO dto = new DenyAuditDTO();
    dto.setId(IdUtil.getSnowflakeNextIdStr());
    dto.setRequestIp(ip);
    dto.setUserIdOpt(null);
    dto.setHttpMethod("INTERNAL");
    dto.setRequestUri("/_h5/jdbc-sqlonly-level-guard");
    dto.setReasonCode(H5_REASON_CODE);
    dto.setReasonDetailRedacted(
        "source="
            + source
            + "; property="
            + LOGGING_LEVEL_JDBC_SQLONLY
            + "; actual="
            + truncate(actual, 40)
            + "; expected="
            + SAFE_LEVEL
            + "; host="
            + host
            + "; eventId="
            + eventId);
    dto.setTraceId(eventId);
    dto.setCreateTime(new Date());
    commonAPI.recordDenyAudit(dto);
  }

  private static String truncate(String s, int max) {
    if (s == null) {
      return "";
    }
    return s.length() <= max ? s : s.substring(0, max) + "...";
  }
}
