package org.jeecg.config.secrets;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Startup validation of sensitive config: forbid null/empty passwords for DB or protected resources in production
 * (SECZ-SSCSP-CODE-012 / Null password).
 *
 * <p>Disable via {@code jeecg.config.secrets.startup-validate=false} (Nacos / env vars).
 */
@Slf4j
@Component
@ConditionalOnProperty(
    name = "jeecg.config.secrets.startup-validate",
    havingValue = "true",
    matchIfMissing = true)
public class ConfigSecretsStartupValidator implements ApplicationListener<ApplicationReadyEvent> {

  private static final Set<String> WEAK_PASSWORDS =
      Set.of("root", "123456", "admin", "password", "guest", "nacos", "test", "12345678", "null");

  /** Password entries required in production via env vars or config (must not be blank after resolution) */
  private static final List<SecretBinding> PROD_REQUIRED_SECRETS =
      Arrays.asList(
          new SecretBinding(
              "spring.datasource.dynamic.datasource.master.password",
              "DB_PASSWORD",
              "APP_PASSWORD"),
          new SecretBinding("spring.redis.password", "REDIS_PASSWORD", "APP_PASSWORD"),
          new SecretBinding(
              "spring.datasource.druid.stat-view-servlet.login-password",
              "LOGIN_PASSWORD",
              "DRUID_LOGIN_PASSWORD"),
          new SecretBinding("spring.security.user.password", "MONITOR_ADMIN_PASSWORD", "APP_PASSWORD"),
          new SecretBinding("OTP_PEPPER", "OTP_PEPPER"),
          new SecretBinding("GATEWAY_SECRET", "GATEWAY_SECRET"),
          new SecretBinding("INTERNAL_CALL_SECRET", "INTERNAL_CALL_SECRET"),
          new SecretBinding("SA_TOKEN_JWT_SECRET_KEY", "SA_TOKEN_JWT_SECRET_KEY"));

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    Environment env = event.getApplicationContext().getEnvironment();
    if (!isProdProfile(env)) {
      return;
    }
    log.info("Production sensitive configuration validation: startup prohibited with null/empty passwords");
    for (SecretBinding binding : PROD_REQUIRED_SECRETS) {
      if (shouldSkipBinding(env, binding)) {
        continue;
      }
      String value = resolveSecret(env, binding);
      if (!StringUtils.hasText(value)) {
        throw new IllegalStateException(
            "Production environment ["
                + binding.propertyKey
                + "] must not be null or empty string; please configure "
                + String.join(" / ", binding.envKeys)
                + "（SECZ-SSCSP-CODE-012）");
      }
      assertNotWeak(binding.propertyKey, value);
      assertMinLength(binding.propertyKey, value);
    }
    validateXxlJobAdminPassword(env);
  }

  /** §6.1 length Fail-Fast for critical secrets. */
  private static void assertMinLength(String key, String value) {
    int min = 32;
    String k = key.toUpperCase(Locale.ROOT);
    if (k.contains("JWT") || k.contains("INTERNAL_CALL") || k.contains("GATEWAY_SECRET")
        || k.contains("SIGNATURE")) {
      min = 64;
    }
    if (k.contains("OTP_PEPPER")) {
      min = 32;
    }
    if (value != null && value.length() < min) {
      throw new IllegalStateException(
          "Configuration item ["
              + key
              + "] length="
              + value.length()
              + " < "
              + min
              + " (§6 Fail-Fast)");
    }
  }

  private static boolean isProdProfile(Environment env) {
    // V2.15-4: prod-like detection aligned with GatewayCorsConfiguration — the previous exact
    // match missed profile "prd" and silently skipped the §6 secret fail-fast bindings.
    return Arrays.stream(env.getActiveProfiles())
        .anyMatch(
            p -> {
              String v = p.toLowerCase();
              return v.contains("prod") || v.contains("prd") || v.contains("production");
            });
  }

  /** Skip loginPassword check when Druid console disabled; skip mail password when host not configured. */
  private static boolean shouldSkipBinding(Environment env, SecretBinding binding) {
    if ("spring.datasource.druid.stat-view-servlet.login-password".equals(binding.propertyKey)) {
      return !Boolean.parseBoolean(
          env.getProperty("spring.datasource.druid.stat-view-servlet.enabled", "false"));
    }
    return false;
  }

  private static String resolveSecret(Environment env, SecretBinding binding) {
    String fromProperty = env.getProperty(binding.propertyKey);
    if (StringUtils.hasText(fromProperty)) {
      return fromProperty;
    }
    for (String envKey : binding.envKeys) {
      String v = env.getProperty(envKey);
      if (StringUtils.hasText(v)) {
        return v;
      }
    }
    return null;
  }

  private static void validateXxlJobAdminPassword(Environment env) {
    String xxlPwd = firstNonBlank(env, "XXL_JOB_ADMIN_PASSWORD", "XXL_JOB_ADMIN_PWD");
    if (StringUtils.hasText(xxlPwd)) {
      assertNotWeak("XXL_JOB_ADMIN_PASSWORD", xxlPwd);
    }
  }

  private static String firstNonBlank(Environment env, String... keys) {
    for (String key : keys) {
      String v = env.getProperty(key);
      if (StringUtils.hasText(v)) {
        return v;
      }
    }
    return null;
  }

  private static void assertNotWeak(String key, String value) {
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    if (WEAK_PASSWORDS.contains(normalized)) {
      throw new IllegalStateException(
          "Configuration item ["
              + key
              + "] uses a common weak password or illegal literal; inject a strong password via key management service (SECZ-SSCSP-CODE-012)");
    }
  }

  private static final class SecretBinding {
    final String propertyKey;
    final String[] envKeys;

    SecretBinding(String propertyKey, String... envKeys) {
      this.propertyKey = propertyKey;
      this.envKeys = envKeys;
    }
  }
}
