package org.jeecg.config.xxljob;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * XXL-JOB executor configuration.
 *
 * <p>Docker / multi-NIC: must explicitly set externally reachable IP (or full address); auto-detect
 * may pick docker0 (e.g. 172.18.0.1) bridge address and block admin callback. Recommended env vars:
 *
 * <ul>
 *   <li>{@code JEECG_XXLJOB_IP} → {@code jeecg.xxljob.ip}
 *   <li>{@code JEECG_XXLJOB_PORT} → {@code jeecg.xxljob.port} (fixed port per service recommended)
 *   <li>{@code JEECG_XXLJOB_ADDRESS} → {@code jeecg.xxljob.address} (preferred, full URL)
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(XxlJobExecutorConfig.XxlJobProperties.class)
@ConditionalOnProperty(prefix = "jeecg.xxljob", name = "enabled", havingValue = "true")
public class XxlJobExecutorConfig {

  private static final Logger log = LoggerFactory.getLogger(XxlJobExecutorConfig.class);

  /**
   * Do not set {@code initMethod = "start"}: {@link XxlJobSpringExecutor} implements {@code
   * SmartInitializingSingleton} and calls {@code start()} in {@code afterSingletonsInstantiated()}.
   * Setting initMethod again binds EmbedServer to the same port twice, causing {@code
   * java.net.BindException: Address in use}. Destruction is handled by {@code DisposableBean#destroy}.
   */
  @Bean
  public XxlJobSpringExecutor xxlJobSpringExecutor(XxlJobProperties properties) {
    String address = firstNonBlank(properties.getAddress(), System.getenv("JEECG_XXLJOB_ADDRESS"));
    String ip = firstNonBlank(properties.getIp(), System.getenv("JEECG_XXLJOB_IP"));
    String accessToken = firstNonBlank(
        properties.getAccessToken(),
        System.getenv("XXL_JOB_ACCESS_TOKEN"),
        System.getenv("JEECG_XXLJOB_ACCESS_TOKEN"));
    Integer port = properties.getPort();
    if (port == null) {
      String portEnv = System.getenv("JEECG_XXLJOB_PORT");
      if (StringUtils.hasText(portEnv)) {
        try {
          port = Integer.parseInt(portEnv.trim());
        } catch (NumberFormatException ignored) {
          log.warn("Invalid JEECG_XXLJOB_PORT={}, ignore", portEnv);
        }
      }
    }

    // B8-1: fail-fast if accessToken is empty.  XXL-JOB uses HMAC of accessToken + timestamp as
    // admin->executor handshake; empty token → unauthenticated Groovy RCE on every executor
    // endpoint ("/run", "/log", etc.).
    boolean prod = org.springframework.util.StringUtils.hasText(System.getenv("SPRING_PROFILES_ACTIVE"))
        ? System.getenv("SPRING_PROFILES_ACTIVE").contains("prod")
        : org.springframework.util.StringUtils.hasText(System.getProperty("spring.profiles.active"))
            && System.getProperty("spring.profiles.active").contains("prod");
    if (!StringUtils.hasText(accessToken)) {
      if (prod) {
        throw new IllegalStateException(
            "[B8-1] XXL_JOB_ACCESS_TOKEN / jeecg.xxljob.access-token MUST be set in "
                + "production; empty token allows remote Groovy RCE on executor endpoints.");
      }
      log.error(
          "[B8-1] jeecg.xxljob.access-token / XXL_JOB_ACCESS_TOKEN is empty; fallback generated "
              + "a single-use random token only for this JVM.  Admin MUST have the same token "
              + "configured or callbacks will 401!");
      java.security.SecureRandom sr = new java.security.SecureRandom();
      byte[] buf = new byte[24];
      sr.nextBytes(buf);
      accessToken = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    } else if (accessToken.trim().length() < 16) {
      if (prod) {
        throw new IllegalStateException(
            "[B8-1] XXL_JOB_ACCESS_TOKEN / jeecg.xxljob.access-token too short (<16 chars) in production.");
      }
      log.warn("[B8-1] XXL_JOB access-token short (<16 chars); consider rotating to >=32 entropy.");
    }

    // B8-1 (part 2): admin must be RFC1918 or loopback (NOT assertNoSsrfTarget — that blocks private).
    String adminAddresses =
        firstNonBlank(
            properties.getAdminAddresses(),
            System.getenv("JEECG_XXLJOB_ADMIN_ADDRESSES"),
            System.getenv("XXL_JOB_ADMIN_ADDRESSES"));
    if (!StringUtils.hasText(adminAddresses)) {
      if (prod) {
        throw new IllegalStateException(
            "[B8-1] jeecg.xxljob.admin-addresses / JEECG_XXLJOB_ADMIN_ADDRESSES MUST be set "
                + "(RFC1918 or loopback scheduler URL).");
      }
      log.error(
          "[B8-1] jeecg.xxljob.admin-addresses is empty; set JEECG_XXLJOB_ADMIN_ADDRESSES="
              + "http://127.0.0.1:9000/xxl-job-admin for local SIT");
    } else {
      // Keep resolved value on properties so executor bean uses the same URL
      properties.setAdminAddresses(adminAddresses.trim());
      for (String raw : adminAddresses.split(",")) {
        String a = raw.trim();
        if (!StringUtils.hasText(a)) {
          continue;
        }
        try {
          java.net.URL u = new java.net.URL(a.startsWith("http") ? a : "http://" + a);
          String host = u.getHost();
          org.jeecg.common.util.security.IpAccessGuard.assertPrivateSchedulerTarget(a, !prod);
          log.info(
              "[B8-1] xxl-job admin address host {} passed allowlist (allowLoopback={})",
              host,
              !prod);
        } catch (java.net.MalformedURLException mue) {
          throw new IllegalStateException("[B8-1] invalid xxl admin address: " + a, mue);
        } catch (Exception se) {
          // Local and prod: no bypass — configure a valid private/loopback Admin URL
          throw new IllegalStateException(
              "[B8-1] xxl admin address "
                  + a
                  + " failed private/loopback allowlist: "
                  + se.getMessage()
                  + " (use http://127.0.0.1:9000/xxl-job-admin locally, or RFC1918 in SIT/prod)",
              se);
        }
      }
    }

    log.info(
        "init xxl-job executor, adminAddresses={}, appname={}, address={}, ip={}, port={}",
        properties.getAdminAddresses(),
        properties.getAppname(),
        address,
        ip,
        port);
    if (!StringUtils.hasText(address) && !StringUtils.hasText(ip)) {
      log.warn(
          "jeecg.xxljob.ip/address is empty; auto-detect may register docker bridge IP "
              + "(e.g. 172.18.0.1). Set JEECG_XXLJOB_IP to a host-reachable address.");
    }

    XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
    executor.setAdminAddresses(properties.getAdminAddresses());
    executor.setAppname(properties.getAppname());
    executor.setAccessToken(accessToken);
    executor.setLogPath(properties.getLogPath());
    if (properties.getLogRetentionDays() != null) {
      executor.setLogRetentionDays(properties.getLogRetentionDays());
    }
    if (StringUtils.hasText(ip)) {
      executor.setIp(ip.trim());
    }
    if (port != null && port > 0) {
      executor.setPort(port);
    }
    if (StringUtils.hasText(address)) {
      executor.setAddress(address.trim());
    }
    return executor;
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (StringUtils.hasText(value)) {
        return value.trim();
      }
    }
    return null;
  }

  @ConfigurationProperties(prefix = "jeecg.xxljob")
  public static class XxlJobProperties {
    private boolean enabled;
    private String adminAddresses;
    private String appname;
    private String accessToken;
    private String address;
    private String ip;
    private Integer port;
    private String logPath;
    private Integer logRetentionDays;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getAdminAddresses() {
      return adminAddresses;
    }

    public void setAdminAddresses(String adminAddresses) {
      this.adminAddresses = adminAddresses;
    }

    public String getAppname() {
      return appname;
    }

    public void setAppname(String appname) {
      this.appname = appname;
    }

    public String getAccessToken() {
      return accessToken;
    }

    public void setAccessToken(String accessToken) {
      this.accessToken = accessToken;
    }

    public String getAddress() {
      return address;
    }

    public void setAddress(String address) {
      this.address = address;
    }

    public String getIp() {
      return ip;
    }

    public void setIp(String ip) {
      this.ip = ip;
    }

    public Integer getPort() {
      return port;
    }

    public void setPort(Integer port) {
      this.port = port;
    }

    public String getLogPath() {
      return logPath;
    }

    public void setLogPath(String logPath) {
      this.logPath = logPath;
    }

    public Integer getLogRetentionDays() {
      return logRetentionDays;
    }

    public void setLogRetentionDays(Integer logRetentionDays) {
      this.logRetentionDays = logRetentionDays;
    }
  }
}
