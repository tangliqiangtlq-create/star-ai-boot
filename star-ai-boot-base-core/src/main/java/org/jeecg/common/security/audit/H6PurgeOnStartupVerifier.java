package org.jeecg.common.security.audit;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.jeecg.common.api.CommonAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile({"prod", "staging", "uat"})
public class H6PurgeOnStartupVerifier
    implements InitializingBean, ApplicationListener<ApplicationReadyEvent> {

  private static final Logger log = LoggerFactory.getLogger(H6PurgeOnStartupVerifier.class);

  public static final String FLYWAY_TABLE = "flyway_schema_history";
  public static final String DENY_AUDIT_TABLE = "sys_deny_audit";
  public static final String H6_REQUIRED_VERSION_PREFIX = "1.99.025";

  /** Overrides the fail-fast behaviour of the non-owner S2S deny-audit probe (default {@code true}). */
  public static final String REMOTE_FAIL_FAST_PROP = "jeecg.h6.deny-audit.remote-fail-fast";

  @Autowired private ApplicationContext appCtx;

  @Autowired(required = false)
  private DataSource dataSource;

  @Autowired(required = false)
  private H6SysLogRetentionService retentionService;

  /**
   * True when this service is not the {@code sys_deny_audit} owner: the deny-audit write path then
   * goes out through S2S and is verified at {@link #onApplicationEvent(ApplicationReadyEvent)}.
   */
  private volatile boolean deferredRemoteProbe = false;

  @Override
  public void afterPropertiesSet() throws Exception {
    StringBuilder errs = new StringBuilder();

    boolean cronOk = false;
    try {
      if (retentionService != null) {
        cronOk = retentionService.isScheduledTaskRegistered();
        java.lang.reflect.Method m =
            H6SysLogRetentionService.class.getDeclaredMethod("purgeExpiredSysLogs");
        Scheduled ann = m.getAnnotation(Scheduled.class);
        if (ann != null && "0 10 2 * * ?".equals(ann.cron())) {
          cronOk = cronOk || true;
        } else if (ann != null) {
          log.warn(
              "H6PurgeOnStartupVerifier: H6SysLogRetentionService.purgeExpiredSysLogs cron={}, expected '0 10 2 * * ?'",
              ann.cron());
        }
      }
      if (!cronOk) {
        errs.append(
            " - H6 CRON MISSING: H6SysLogRetentionService @Scheduled(cron=\"0 10 2 * * ?\") bean not registered or cron mismatch\n");
      }
    } catch (Exception e) {
      errs.append(" - H6 CRON INSPECT FAIL: ").append(e.toString()).append('\n');
    }

    boolean flywayOk = false;
    boolean denyTableOk = false;
    try {
      // Prefer injected DataSource — SpringContextUtils is often still null during
      // InitializingBean (would NPE and falsely report DENY TABLE / FLYWAY missing).
      DataSource ds = dataSource;
      if (ds == null && appCtx != null) {
        ds = appCtx.getBean(DataSource.class);
      }
      if (ds == null) {
        throw new IllegalStateException("DataSource bean unavailable during H6 probe");
      }
      try (java.sql.Connection c = ds.getConnection()) {
        DatabaseMetaData md = c.getMetaData();
        denyTableOk = tableExists(md, DENY_AUDIT_TABLE);
        boolean flywayTblExists = tableExists(md, FLYWAY_TABLE);
        if (flywayTblExists) {
          try (java.sql.PreparedStatement ps =
              c.prepareStatement(
                  "SELECT COUNT(*) FROM "
                      + FLYWAY_TABLE
                      + " WHERE version LIKE ? AND success = 1")) {
            ps.setString(1, H6_REQUIRED_VERSION_PREFIX + "%");
            try (ResultSet rs = ps.executeQuery()) {
              if (rs.next() && rs.getInt(1) >= 1) {
                flywayOk = true;
              }
            }
          } catch (Exception e) {
            log.debug(
                "H6 verifier: flyway version query failed (non-blocking if DDL applied manually): {}",
                e.toString());
            flywayOk = denyTableOk;
          }
        } else {
          flywayOk = denyTableOk;
        }
      }
    } catch (Exception e) {
      log.warn("H6PurgeOnStartupVerifier: DB schema probe error: {}", e.toString());
    }

    // ---- shard ownership ----------------------------------------------------------------
    // sys_deny_audit (and its V1_99_025 retention DDL) is owned by the system shard (mpay_system).
    // dcs / processengine dropped their local mirrors during the shard cleanup, so they are NOT
    // expected to hold the table; for them this gate validates the S2S deny-audit write path
    // instead, once the context is fully up (so a mid-refresh Feign call cannot destabilise
    // startup). SHARD-BAN T5-06/07 / gate 1.14.
    boolean owner = isDenyAuditOwner();
    if (!owner && !denyTableOk) {
      deferredRemoteProbe = true;
      log.info(
          "H6 verifier: sys_deny_audit is owned by the system shard; "
              + "this service is not the owner, deferring deny-audit verification to the S2S path");
    }

    if (!flywayOk && !deferredRemoteProbe) {
      errs.append(
          " - H6 FLYWAY MISSING: Flyway V1_99_025 (sys_deny_audit) NOT successfully applied. version LIKE '"
              + H6_REQUIRED_VERSION_PREFIX
              + "%' row missing and DENY table absent.\n");
    }
    if (!denyTableOk && !deferredRemoteProbe) {
      errs.append(
          " - H6 DENY TABLE MISSING: TABLE sys_deny_audit does not exist in current schema (H6/H8 deny audit writes will fail).\n");
    }

    if (errs.length() > 0) {
      String msg =
          "H6-PURGE-GATE FAIL (prod/stage/uat fail-fast):\n"
              + errs
              + "\nRemediation: 1) execute Flyway V1_99_025__H6_deny_audit_log_retention.sql; "
              + "2) ensure @EnableScheduling present on main App class for H6SysLogRetentionService cron.";
      log.error(msg);
      throw new H6StartupVerificationFailedException(msg);
    }

    log.info(
        "H6PurgeOnStartupVerifier PASS: cronRegistered={}, flywayV1_99_025Applied={}, denyAuditTableExists={}, denyAuditRemoteDeferred={}",
        cronOk,
        flywayOk,
        denyTableOk,
        deferredRemoteProbe);
  }

  /**
   * Non-owner shards verify the deny-audit write path here, once every bean (including the Feign
   * client behind {@link CommonAPI}) is ready.
   *
   * <p>Three outcomes:
   *
   * <ul>
   *   <li>owner reports the table → PASS;
   *   <li>owner reachable but table missing → FAIL (fail-fast, same semantics as the owner probe);
   *   <li>owner unreachable (registry/network during boot) → FAIL by default, so a broken
   *       deny-audit write path cannot pass unnoticed. Set {@code
   *       jeecg.h6.deny-audit.remote-fail-fast=false} to degrade to WARN and keep the shard up with
   *       the (by-design) fail-soft audit writes instead.
   * </ul>
   */
  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    if (!deferredRemoteProbe) {
      return;
    }
    boolean available = false;
    Throwable infraError = null;
    try {
      CommonAPI api = appCtx.getBean(CommonAPI.class);
      available = api.isDenyAuditOwnerAvailable();
    } catch (Throwable t) {
      infraError = t;
    }
    if (available) {
      log.info(
          "H6 verifier PASS (S2S): deny-audit write path verified against the owner shard "
              + "(sys_deny_audit is reachable through CommonAPI#recordDenyAudit)");
      return;
    }
    if (infraError != null) {
      String detail =
          "H6 verifier: S2S deny-audit probe could not reach the owner ("
              + infraError
              + "); owner = system shard";
      if (isRemoteFailFast()) {
        String msg =
            "H6-PURGE-GATE FAIL: "
                + detail
                + ". Set "
                + REMOTE_FAIL_FAST_PROP
                + "=false to keep the shard up with fail-soft audit writes.";
        log.error(msg);
        throw new H6StartupVerificationFailedException(msg);
      }
      log.warn("{}; continuing with fail-soft audit writes ({} = false)", detail, REMOTE_FAIL_FAST_PROP);
      return;
    }
    String msg =
        "H6-PURGE-GATE FAIL: sys_deny_audit is NOT available through the S2S deny-audit path "
            + "(owner = system shard). Execute Flyway "
            + "V1_99_025__H6_deny_audit_log_retention.sql in mpay_system.";
    log.error(msg);
    throw new H6StartupVerificationFailedException(msg);
  }

  /**
   * Whether this service owns {@code sys_deny_audit}, detected exactly like the shard's other
   * owner markers: the presence of the system-module service implementation on the classpath.
   */
  private boolean isDenyAuditOwner() {
    try {
      return org.springframework.util.ClassUtils.isPresent(
          "org.jeecg.modules.system.service.impl.SysDenyAuditServiceImpl",
          H6PurgeOnStartupVerifier.class.getClassLoader());
    } catch (Throwable t) {
      log.warn("H6 verifier: deny-audit ownership probe failed: {}", t.toString());
      return false;
    }
  }

  /** {@code true} unless the operator explicitly opts out via {@link #REMOTE_FAIL_FAST_PROP}. */
  private boolean isRemoteFailFast() {
    try {
      return appCtx
          .getEnvironment()
          .getProperty(REMOTE_FAIL_FAST_PROP, Boolean.class, Boolean.TRUE);
    } catch (Throwable t) {
      log.warn("H6 verifier: could not read {}: {}", REMOTE_FAIL_FAST_PROP, t.toString());
      return true;
    }
  }

  private static boolean tableExists(DatabaseMetaData md, String name) throws java.sql.SQLException {
    try (ResultSet rs = md.getTables(null, null, name, new String[] {"TABLE"})) {
      if (rs.next()) {
        return true;
      }
    }
    try (ResultSet rs = md.getTables(null, null, name.toUpperCase(), new String[] {"TABLE"})) {
      if (rs.next()) {
        return true;
      }
    }
    try (ResultSet rs = md.getTables(null, null, name.toLowerCase(), new String[] {"TABLE"})) {
      return rs.next();
    }
  }

  public static class H6StartupVerificationFailedException extends RuntimeException {
    public H6StartupVerificationFailedException(String msg) {
      super(msg);
    }
  }
}
