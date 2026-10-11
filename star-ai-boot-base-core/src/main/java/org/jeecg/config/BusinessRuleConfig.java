package org.jeecg.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;

public class BusinessRuleConfig {

  // Business rule key (global constant)
  public static final String CAPTCHA = "captcha"; // Verification code
  public static final String EMAIL_OTP = "email-otp"; // Email verification code
  public static final String PASSWORD = "password"; // password
  public static final String EMAIL_VERIFICATION = "email-verification"; // Email verification

  /**
   * B5 / CP-OTP #7: force login captcha after this many consecutive password failures
   * (security remediation doc 07.1: wrong password 3 times -> captcha). First login = 0 -> no captcha.
   */
  public static final int PASSWORD_CAPTCHA_AFTER_FAILURES = 3;

  /**
   * G05 / 07-11: wrong-password attempts before HTTP 429 + Redis key {@code LOCK_TOO_MANY_LOGIN:{uid}}
   * with TTL {@link #LOCK_TOO_MANY_LOGIN_TTL_SECONDS}.
   */
  public static final int LOCK_TOO_MANY_LOGIN_THRESHOLD = 11;

  /** G05 lock TTL (seconds) for {@code LOCK_TOO_MANY_LOGIN:{uid}}. */
  public static final int LOCK_TOO_MANY_LOGIN_TTL_SECONDS = 1800;

  /** Redis key prefix for G05 login lock (exact name required by gate / nonstatic 07-11). */
  public static final String LOCK_TOO_MANY_LOGIN_KEY_PREFIX = "LOCK_TOO_MANY_LOGIN:";

  // Business rule configuration
  public static final Map<String, BusinessRule> BUSINESS_RULES = new HashMap<>();

  static {
    BUSINESS_RULES.put(
        CAPTCHA, new BusinessRule(5, Duration.ofMinutes(5), 10, true)); // Verification code failed 5 times locks for 5 minutes

    BUSINESS_RULES.put(
        EMAIL_OTP, new BusinessRule(3, Duration.ofMinutes(15), 15, true)); // Email verification code failed 3 times locks for 15 minutes

    // G05: 11 failures → LOCK_TOO_MANY_LOGIN TTL 1800s (HTTP 429). Captcha still after 3 (above).
    BUSINESS_RULES.put(
        PASSWORD,
        new BusinessRule(
            LOCK_TOO_MANY_LOGIN_THRESHOLD,
            Duration.ofSeconds(LOCK_TOO_MANY_LOGIN_TTL_SECONDS),
            20,
            true));

    BUSINESS_RULES.put(
        EMAIL_VERIFICATION,
        new BusinessRule(3, Duration.ofMinutes(20), 10, true)); // Email verification failed 3 times locks for 20 minutes
  }

  @Getter
  public static class BusinessRule {

    private final int maxAttempts;
    private final Duration lockDuration;
    private final long maxDelay;
    private final boolean triggersAccountLock;

    public BusinessRule(
        int maxAttempts, Duration lockDuration, long maxDelay, boolean triggersAccountLock) {
      this.maxAttempts = maxAttempts;
      this.lockDuration = lockDuration;
      this.maxDelay = maxDelay;
      this.triggersAccountLock = triggersAccountLock;
    }
  }
}
