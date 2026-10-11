package org.jeecg.modules.base.service.impl;

import java.time.Duration;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.enem.EmailScene;
import org.jeecg.common.util.IpUtils;
import org.jeecg.common.util.RocketMQUtil;
import org.jeecg.common.util.security.TrustedProxyIpResolver;
import org.jeecg.config.BusinessRuleConfig;
import org.jeecg.modules.base.service.IBusinessRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BusinessRuleServiceImpl implements IBusinessRuleService {

  @Autowired private StringRedisTemplate redisTemplate;

  @Resource private RocketMQUtil rocketMQUtil;

  /**
   * B5-2: lock/attempt counter keyed by account id (username), not IP+UA — otherwise attackers
   * bypass lock by rotating network / User-Agent.
   */
  private String getUserIdentifier(HttpServletRequest request, String username) {
    if (username == null) {
      return "unknown";
    }
    return username.trim().toLowerCase();
  }

  /** Compute increasing delay (exponential backoff) */
  @Override
  public long getExponentialBackoffDelay(String ruleKey, String username) {
    String key = "failures:" + ruleKey + ":" + username;
    String attemptsStr = redisTemplate.opsForValue().get(key);
    int attempts = (attemptsStr == null) ? 0 : Integer.parseInt(attemptsStr);

    BusinessRuleConfig.BusinessRule rule = BusinessRuleConfig.BUSINESS_RULES.get(ruleKey);
    return (long) Math.min(Math.pow(2, attempts), rule.getMaxDelay());
  }

  /** Unlock account */
  @Override
  public void unlockAccount(String username, HttpServletRequest request) {
    String uid = getUserIdentifier(request, username);
    // Unlock account-related info
    redisTemplate.delete("locked:" + username);
    redisTemplate.delete("lock_expiry:" + username);
    redisTemplate.delete("failures:lock:" + uid);
    redisTemplate.delete(BusinessRuleConfig.LOCK_TOO_MANY_LOGIN_KEY_PREFIX + uid);

    // Unlock by IP; clear IP-related lock info
    String ip = IpUtils.getIpAddr(request);
    redisTemplate.delete("blocked_ip:" + ip); // Lift IP ban
  }

  /** Reset failure count (does not unlock account immediately) */
  @Override
  public void resetFailedAttempts(String ruleKey, String username, HttpServletRequest request) {
    // Get user IP and username combination
    String userIdentifier = getUserIdentifier(request, username);

    // Delete user failure attempt records
    redisTemplate.delete("failures:" + ruleKey + ":" + userIdentifier);

    // Unlock account
    unlockAccount(username, request);

    // GetUserIP
    String ip = IpUtils.getIpAddr(request);

    // Delete IP failure attempt records
    redisTemplate.delete("failed_attempts_ip:" + ip);

    // Lift IP ban if present
    redisTemplate.delete("blocked_ip:" + ip);
  }

  /** Check whether the user is allowed to perform an action (blocked when account is locked) */
  @Override
  public boolean isAllowed(String ruleKey, String username, HttpServletRequest request) {
    if (request != null && isIpBlocked(IpUtils.getIpAddr(request))) {
      return false;
    }
    return !isAccountLocked(username, request);
  }

  @Override
  public boolean isAccountLocked(String username, HttpServletRequest request) {
    String uid = getUserIdentifier(request, username);
    // G05: explicit LOCK_TOO_MANY_LOGIN:{uid} (TTL 1800s)
    if (Boolean.TRUE.equals(
        redisTemplate.hasKey(BusinessRuleConfig.LOCK_TOO_MANY_LOGIN_KEY_PREFIX + uid))) {
      return true;
    }
    String key = "failures:" + "lock:" + uid;
    String lockTimeStr = redisTemplate.opsForValue().get(key);
    if (lockTimeStr == null) {
      return false;
    }

    long lockExpiry = Long.parseLong(lockTimeStr);
    if (System.currentTimeMillis() < lockExpiry) {
      return true;
    }

    redisTemplate.delete(key);
    return false;
  }

  @Override
  public void lockAccount(
      String username, HttpServletRequest request, Duration lockDuration, String lockReason) {
    String uid = getUserIdentifier(request, username);
    String key = "failures:" + "lock:" + uid;
    redisTemplate
        .opsForValue()
        .set(
            key,
            String.valueOf(System.currentTimeMillis() + lockDuration.toMillis()),
            lockDuration);

    // G05 gate: LOCK_TOO_MANY_LOGIN:{uid} TTL 1800s (password lock path)
    if (BusinessRuleConfig.PASSWORD.equals(lockReason)) {
      redisTemplate
          .opsForValue()
          .set(
              BusinessRuleConfig.LOCK_TOO_MANY_LOGIN_KEY_PREFIX + uid,
              "1",
              Duration.ofSeconds(BusinessRuleConfig.LOCK_TOO_MANY_LOGIN_TTL_SECONDS));
    }

    cn.hutool.json.JSONObject msg = new cn.hutool.json.JSONObject();

    String lockReasonText = "";

    switch (lockReason) {
      case BusinessRuleConfig.EMAIL_OTP:
        lockReasonText = "Too many incorrect email OTP attempts";
        break;
      case BusinessRuleConfig.PASSWORD:
        lockReasonText = "Too many incorrect password attempts";
        break;
      case BusinessRuleConfig.CAPTCHA:
        lockReasonText = "Too many incorrect CAPTCHA attempts";
        break;
      case BusinessRuleConfig.EMAIL_VERIFICATION:
        lockReasonText = "Too many incorrect email verification attempts";
        break;
    }

    msg.set("email", username);
    msg.set("emailScene", EmailScene.ACCOUNT_LOCKED.name());
    msg.set("lockDuration", lockDuration.toMinutes() + " minutes");
    msg.set("lockReasonText", lockReasonText);

    msg.set("lockedIp", TrustedProxyIpResolver.getStrictClientIp(request));

    // Never fail the login lock path if mail publish breaks (G05 must still 429).
    try {
      log.info("Sending account locked email to {}", username);
      rocketMQUtil.sendRetry("sendEmailTopic_topic", msg, null);
    } catch (Exception e) {
      log.warn("account-lock email publish skipped: {}", e.toString());
    }
  }

  private void trackFailedAttemptsByIp(String ip) {
    String ipKey = "failed_attempts_ip:" + ip;
    redisTemplate.opsForValue().increment(ipKey);
    redisTemplate.expire(ipKey, Duration.ofMinutes(10));

    String attemptsStr = redisTemplate.opsForValue().get(ipKey);
    int attempts = (attemptsStr == null) ? 0 : Integer.parseInt(attemptsStr);

    if (attempts >= 15) {
      blockIp(ip);
    }
  }

  private void blockIp(String ip) {
    redisTemplate.opsForValue().set("blocked_ip:" + ip, "true", Duration.ofHours(1));
  }

  public boolean isIpBlocked(String ip) {
    return Boolean.TRUE.equals(redisTemplate.hasKey("blocked_ip:" + ip));
  }

  @Override
  public void registerFailedAttempt(String ruleKey, String username, HttpServletRequest request) {
    String userIdentifier = getUserIdentifier(request, username);
    String key = "failures:" + ruleKey + ":" + userIdentifier;

    redisTemplate.opsForValue().increment(key, 1);
    // Keep failure window at least as long as the longest dynamic lock (24h)
    redisTemplate.expire(key, Duration.ofHours(24));

    // Get current failure count
    String attemptsStr = redisTemplate.opsForValue().get(key);
    int attempts = (attemptsStr == null) ? 0 : Integer.parseInt(attemptsStr);

    BusinessRuleConfig.BusinessRule rule = BusinessRuleConfig.BUSINESS_RULES.get(ruleKey);
    if (rule != null && rule.isTriggersAccountLock() && attempts >= rule.getMaxAttempts()) {
      // Compute incremental lock duration
      Duration lockDuration = getDynamicLockDuration(attempts);
      if (!lockDuration.isZero()) {
        lockAccount(username, request, lockDuration, ruleKey);
      }
    }

    // Count IP failures to mitigate distributed attacks
    if (request != null) {
      trackFailedAttemptsByIp(IpUtils.getIpAddr(request));
    }
  }

  @Override
  public int getFailedAttempts(String ruleKey, String username, HttpServletRequest request) {
    String userIdentifier = getUserIdentifier(request, username);
    String key = "failures:" + ruleKey + ":" + userIdentifier;
    String attemptsStr = redisTemplate.opsForValue().get(key);
    if (attemptsStr == null) {
      return 0;
    }
    try {
      return Integer.parseInt(attemptsStr);
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  @Override
  public boolean isCaptchaRequired(String ruleKey, String username, HttpServletRequest request) {
    // B5 / 07.1: captcha only after N consecutive failures — not on first login.
    return getFailedAttempts(ruleKey, username, request)
        >= BusinessRuleConfig.PASSWORD_CAPTCHA_AFTER_FAILURES;
  }

  /**
   * G05: hard lock at {@link BusinessRuleConfig#LOCK_TOO_MANY_LOGIN_THRESHOLD} with TTL 1800s.
   * Intermediate soft delays removed so the 11th failure is the first account lock (matches gate).
   */
  private Duration getDynamicLockDuration(int attempts) {
    if (attempts >= BusinessRuleConfig.LOCK_TOO_MANY_LOGIN_THRESHOLD) {
      return Duration.ofSeconds(BusinessRuleConfig.LOCK_TOO_MANY_LOGIN_TTL_SECONDS);
    }
    return Duration.ZERO;
  }
}
