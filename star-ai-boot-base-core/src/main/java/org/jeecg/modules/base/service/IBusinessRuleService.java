package org.jeecg.modules.base.service;

import java.time.Duration;
import jakarta.servlet.http.HttpServletRequest;

public interface IBusinessRuleService {

  /**
   * Check whether account is locked
   *
   * @param username username
   * @return true if account is locked
   */
  boolean isAccountLocked(String username, HttpServletRequest request);

  /**
   * Compute incremental delay (exponential backoff)
   *
   * @param ruleKey business rule key (e.g. "login", "password-reset")
   * @param username username
   * @return computed delay in milliseconds
   */
  long getExponentialBackoffDelay(String ruleKey, String username);

  /**
   * Record failed attempt and check whether account should be locked
   *
   * @param ruleKey business rule key
   * @param username username
   * @param request HTTP request (for IP and User-Agent)
   */
  void registerFailedAttempt(String ruleKey, String username, HttpServletRequest request);

  /**
   * Current failed-attempt count for the rule+account (B5-2 lock key = username/userId).
   *
   * @return attempts, or 0 when none
   */
  int getFailedAttempts(String ruleKey, String username, HttpServletRequest request);

  /**
   * Whether captcha must be supplied — after {@link
   * org.jeecg.config.BusinessRuleConfig#PASSWORD_CAPTCHA_AFTER_FAILURES} consecutive password
   * failures (first login / clean counter must NOT require captcha).
   */
  boolean isCaptchaRequired(String ruleKey, String username, HttpServletRequest request);

  /**
   * Lock account (lock duration from incremental delay rules)
   *
   * @param username username
   * @param lockDuration lock duration
   */
  void lockAccount(String username, HttpServletRequest request, Duration lockDuration,String lockReason);

  /**
   * Unlock account
   *
   * @param username username
   */
  void unlockAccount(String username, HttpServletRequest request);

  /**
   * Reset failure count (does not immediately unlock the account)
   *
   * @param ruleKey business rule key
   * @param username username
   */
  void resetFailedAttempts(String ruleKey, String username, HttpServletRequest request);

  /**
   * Check whether user is allowed to perform an operation (blocked when account is locked)
   *
   * @param ruleKey business rule key
   * @param username username
   * @param request HTTP request (for IP and User-Agent)
   * @return true if operation allowed
   */
  boolean isAllowed(String ruleKey, String username, HttpServletRequest request);

  /**
   * Check whether IP is blocked
   *
   * @param ip IP address
   * @return true if IP is blocked
   */
  boolean isIpBlocked(String ip);
}
