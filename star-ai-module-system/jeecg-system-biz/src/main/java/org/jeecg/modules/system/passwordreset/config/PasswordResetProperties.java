package org.jeecg.modules.system.passwordreset.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Password-reset external link config.
 *
 * <p>Nacos prefix: {@code jeecg.password-reset}
 *
 * <p>A4-4: {@code secret} / {@code RESET_SECRET} is mandatory and must NOT fall back to
 * {@code EncryptedString.key} (MFA AES key must stay physically separate).
 */
@Data
@Validated
@ConfigurationProperties(prefix = "jeecg.password-reset")
public class PasswordResetProperties {

  /** HMAC primary secret (32 bytes recommended; required) */
  private String secret;

  /** HMAC old secret (7-day transition when rotating) */
  private String secretOld;

  /** Portal page origin (e.g. https://mypay.example.com). Required in prod */
  @NotBlank(message = "[jeecg.password-reset.portal-origin] is required (portal page origin)")
  private String portalOrigin;

  /** Reset path prefix after portalOrigin; default reset-password */
  private String resetPathPrefix = "reset-password";

  @Min(1)
  @Max(20)
  private int maxPendingPerUserSource = 3;

  @Min(10)
  @Max(14)
  private int bcryptLogRounds = 10;

  @Min(5)
  @Max(1440)
  private int defaultTtlMinutes = 30;

  @Min(1)
  @Max(20)
  private int defaultMaxFailAttempts = 5;

  @Min(0)
  @Max(19)
  private int needCaptchaAfterFailures = 2;

  @Min(30)
  @Max(900)
  private int challengeTtlSeconds = 120;

  @Min(5)
  @Max(1440)
  private int lockedTtlMinutes = 60;

  private boolean revokeSameUserPendingOnSuccess = true;

  private boolean kickOutSessionsOnSuccess = true;

  @Min(0)
  @Max(10)
  private int trustedProxyHops = 1;

  private boolean enabled = true;

  @PostConstruct
  public void validate() {
    this.secret = resolveResetSecret(this.secret);
    if (portalOrigin != null) {
      while (portalOrigin.endsWith("/")) {
        portalOrigin = portalOrigin.substring(0, portalOrigin.length() - 1);
      }
    }
    if (resetPathPrefix != null) {
      resetPathPrefix = resetPathPrefix.trim();
      while (resetPathPrefix.startsWith("/")) {
        resetPathPrefix = resetPathPrefix.substring(1);
      }
      while (resetPathPrefix.endsWith("/")) {
        resetPathPrefix = resetPathPrefix.substring(0, resetPathPrefix.length() - 1);
      }
    }
  }

  /**
   * A4-4: RESET_SECRET / jeecg.password-reset.secret only — never EncryptedString.key.
   *
   * <p>Priority: OS env {@code RESET_SECRET} → JVM property {@code RESET_SECRET} (repo-root
   * {@code .env} via {@code RepoRootEnvLoader}) → {@code -Djeecg.password-reset.secret} → Spring
   * bound {@code jeecg.password-reset.secret}.
   */
  private static String resolveResetSecret(String bound) {
    String fromEnv = System.getenv("RESET_SECRET");
    if (fromEnv != null && !fromEnv.trim().isEmpty()) {
      return fromEnv.trim();
    }
    // IDE local: RepoRootEnvLoader loads .env into System properties (not OS env)
    String fromEnvProp = System.getProperty("RESET_SECRET");
    if (fromEnvProp != null && !fromEnvProp.trim().isEmpty()) {
      return fromEnvProp.trim();
    }
    String fromProp = System.getProperty("jeecg.password-reset.secret");
    if (fromProp != null && !fromProp.trim().isEmpty()) {
      return fromProp.trim();
    }
    if (bound != null && !bound.trim().isEmpty()) {
      return bound.trim();
    }
    throw new IllegalStateException(
        "[A4-4] RESET_SECRET / jeecg.password-reset.secret must be set; "
            + "EncryptedString.key fallback is forbidden.");
  }
}
