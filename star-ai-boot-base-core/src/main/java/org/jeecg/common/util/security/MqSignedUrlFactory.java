package org.jeecg.common.util.security;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import org.jeecg.common.system.passwordreset.PasswordResetLinkFacade;
import org.jeecg.common.system.passwordreset.vo.PasswordResetLinkVO;
import org.jeecg.common.util.SecureHashUtil;
import org.jeecg.common.util.SpringContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * I2 + I5 shared signing factory using {@code MQ_MESSAGE_SIGN_SECRET}.
 *
 * <ul>
 *   <li>I2 producer: do not send full setPasswordUrl on MQ; write linkId+expire+sign only. Consumer
 *       loads token via PasswordResetLinkFacade, whitelisted domain prefix, HMAC verify, full URL.
 *       Never trust inviteLinkEnabled from payload; enabled when link lookup succeeds.
 *   <li>I5 producer: sign(source|target|ratePlain|nonce|ts) plus owner+nonce+ts+sign. Consumer
 *       verifyRatePayload gates: HMAC, skew +/-5min, nonce SETNX, owner whitelist—failure throws
 *       SecurityException and permanent DLQ (no retry).
 * </ul>
 *
 * <p>Rollout (2-week compat SLA): <b>upgrade consumers for dual format -> canary -> upgrade producers</b>.
 * Producers-first with old consumers causes NPE and is an incident.
 */
public final class MqSignedUrlFactory {
  private static final Logger log = LoggerFactory.getLogger(MqSignedUrlFactory.class);

  /** I5 owner whitelist: only the fixed producer identity may write rates; forged owner -> DLQ. */
  public static final String RATE_ORACLE_OWNER = "MIGHTY_OPS_RATE_ORACLE";
  /** I5 skew tolerance: up to 5 minutes NTP drift (300_000 ms). */
  public static final long RATE_SIGN_SKEW_MS = 300_000L;
  /** I5 nonce replay window: same nonce once per 10 minutes (Redis TTL drops expired nonces). */
  public static final int RATE_NONCE_TTL_SECONDS = 600;
  /** I2 link expiry format for email templates (human-readable LocalDateTime). */
  public static final DateTimeFormatter LINK_EXPIRE_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

  /** I2 domain whitelist: portalOrigin or publicDomain; other domains fail. */
  static final Set<String> WHITELIST_PREFIX_CONFIG_KEYS =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(
                  "jeecg.password-reset.portal-origin",
                  "jeecg.password-reset.public-domain",
                  "jeecg.path.portalOrigin",
                  "jeecg.app.public-domain")));

  static final String SIGN_SEP = "|";
  private static final SecureRandom RANDOM = new SecureRandom();

  /**
   * V2.14-E / S6-4: ephemeral per-JVM secret used ONLY when there is no Spring context (plain unit
   * tests) — see {@link #effectiveSignSecret()}.
   */
  private static final String NON_SPRING_EPHEMERAL_SECRET = generateNonce() + generateNonce();

  private MqSignedUrlFactory() {}

  /* ================================================================ */
  /*                           I2 setPasswordUrl                       */
  /* ================================================================ */

  /**
   * I2 producer: from PasswordResetLinkVO, returns linkId / expireStr / linkSign; use applyLinkToJson
   * instead of setPasswordUrl on MQ.
   */
  public static LinkSigned createSignedLinkMeta(PasswordResetLinkVO linkVO) {
    if (linkVO == null || StrUtil.isBlank(linkVO.getToken())) {
      return new LinkSigned(null, null, null, null);
    }
    String expireStr =
        linkVO.getExpireTime() == null ? "" : linkVO.getExpireTime().format(LINK_EXPIRE_FMT);
    String payloadPlain = linkVO.getToken() + SIGN_SEP + expireStr;
    String sign = hmac(payloadPlain);
    return new LinkSigned(linkVO.getToken(), expireStr, sign, linkVO.getResetUrl());
  }

  /**
   * I2 producer helper: sets three fields on JSONObject; no full URL or inviteLinkEnabled (backend
   * decides enabled from link lookup).
   */
  public static void applyLinkToJson(JSONObject mq, PasswordResetLinkVO linkVO) {
    if (mq == null) return;
    if (linkVO == null || StrUtil.isBlank(linkVO.getToken())) {
      mq.set("pwResetLinkId", "");
      mq.set("pwResetExpire", "");
      mq.set("pwResetSign", "");
      return;
    }
    LinkSigned s = createSignedLinkMeta(linkVO);
    mq.set("pwResetLinkId", s.linkId);
    mq.set("pwResetExpire", s.expireStr);
    mq.set("pwResetSign", s.sign);
    mq.remove("setPasswordUrl");
    mq.remove("inviteLinkEnabled");
  }

  /**
   * I2 consumer: parse JSONObject; prefer new triple format, else legacy setPasswordUrl.
   *
   * <p>New format:
   * <ol>
   *   <li>PasswordResetLinkFacade lookup by linkId (token) for resetUrl from DB</li>
   *   <li>HMAC verify pwResetLinkId|pwResetExpire</li>
   *   <li>resetUrl prefix must match portalOrigin or publicDomain whitelist</li>
   *   <li>Returns ResolvedLink: url + expireDisplayStr + linkEnabled</li>
   * </ol>
   * Legacy setPasswordUrl accepted during 2-week SLA (prefix whitelist); remove backward compat after 2026-10-11.
   */
  public static ResolvedLink resolveAndVerifyLink(JSONObject data) {
    if (data == null) return new ResolvedLink(null, null, false);
    String linkId = data.getStr("pwResetLinkId");
    String expireStr = data.getStr("pwResetExpire");
    String sign = data.getStr("pwResetSign");

    if (StrUtil.isNotBlank(linkId) && StrUtil.isNotBlank(sign)) {
      String payload = linkId + SIGN_SEP + StrUtil.nullToDefault(expireStr, "");
      if (constantTimeEquals(hmac(payload), sign)) {
        PasswordResetLinkFacade facade = null;
        try {
          facade = SpringContextUtils.getBean(PasswordResetLinkFacade.class);
        } catch (Exception e) {
          log.warn("I2 resolve PasswordResetLinkFacade missing");
        }
        if (facade != null) {
          String url = lookupResetUrlByLinkId(facade, linkId);
          if (StrUtil.isNotBlank(url) && isWhitelistedResetUrl(url)) {
            return new ResolvedLink(url, expireStr, true);
          }
        }
      } else {
        log.warn("I2 resolve verify HMAC FAIL linkId={}", linkId.length() >= 8 ? linkId.substring(0, 8) : "***");
      }
    }

    String legacyUrl = data.getStr("setPasswordUrl");
    if (StrUtil.isNotBlank(legacyUrl) && isWhitelistedResetUrl(legacyUrl)) {
      String legacyExpire = data.getStr("linkExpireTime");
      return new ResolvedLink(legacyUrl, legacyExpire, true);
    }
    return new ResolvedLink(null, expireStr, false);
  }

  static boolean isWhitelistedResetUrl(String url) {
    if (StrUtil.isBlank(url)) return false;
    Environment env;
    try {
      if (SpringContextUtils.getApplicationContext() == null) return true;
      env = SpringContextUtils.getApplicationContext().getEnvironment();
    } catch (Exception e) {
      return true;
    }
    for (String key : WHITELIST_PREFIX_CONFIG_KEYS) {
      String prefix = env.getProperty(key, "");
      if (StrUtil.isNotBlank(prefix)) {
        if (!prefix.endsWith("/")) prefix = prefix + "/";
        String u = url.startsWith("http") ? url : "http://" + url;
        if (u.startsWith(prefix) || url.startsWith(prefix)) return true;
      }
    }
    // Fail CLOSED: inside a Spring runtime a URL that matches no configured portal prefix must be
    // rejected (I2 anti-phishing). Only the non-Spring unit-test branch below stays permissive.
    return false;
  }

  private static String lookupResetUrlByLinkId(PasswordResetLinkFacade facade, String token) {
    try {
      PasswordResetLinkVO vo = facade.getLinkByTokenSafe(token);
      return vo == null ? null : vo.getResetUrl();
    } catch (Exception e) {
      log.warn("I2 resolve facade lookup failed tokenPresent={}", StrUtil.isNotBlank(token));
    }
    return null;
  }

  /* ================================================================ */
  /*                         I5 ReverseCurrency rate signing                 */
  /* ================================================================ */

  /**
   * I5 producer: before MQ publish, attach owner/nonce/ts/sign; message must already have
   * source/target/rate.
   */
  public static void signRateMessage(JSONObject message) {
    if (message == null) return;
    String src = message.getStr("sourceCurrency");
    String tgt = message.getStr("targetCurrency");
    BigDecimal rate = message.getBigDecimal("rate", null);
    if (StrUtil.isBlank(src) || StrUtil.isBlank(tgt) || rate == null) return;
    String nonce = generateNonce();
    String ts = String.valueOf(System.currentTimeMillis());
    String ratePlain = rate.stripTrailingZeros().toPlainString();
    String payload = src + SIGN_SEP + tgt + SIGN_SEP + ratePlain + SIGN_SEP + nonce + SIGN_SEP + ts;
    String sign = hmac(payload);
    message.set("owner", RATE_ORACLE_OWNER);
    message.set("nonce", nonce);
    message.set("ts", ts);
    message.set("sign", sign);
  }

  /**
   * I5 consumer: call first in onMessage—HMAC, skew +/-5min, nonce anti-replay, owner whitelist.
   *
   * @throws SecurityException any gate failure -> listener -> permanent DLQ (no retry)
   */
  public static RateVerified verifyRatePayload(JSONObject msg) throws SecurityException {
    if (msg == null) throw new SecurityException("I5-SIGN: null message");
    String src = msg.getStr("sourceCurrency");
    String tgt = msg.getStr("targetCurrency");
    BigDecimal rate = msg.getBigDecimal("rate", null);
    String nonce = msg.getStr("nonce");
    String tsStr = msg.getStr("ts");
    String sign = msg.getStr("sign");
    String owner = msg.getStr("owner");
    if (StrUtil.hasBlank(src, tgt, nonce, tsStr, sign, owner) || rate == null) {
      throw new SecurityException("I5-SIGN: missing required field");
    }
    String ratePlain = rate.stripTrailingZeros().toPlainString();
    String payload = src + SIGN_SEP + tgt + SIGN_SEP + ratePlain + SIGN_SEP + nonce + SIGN_SEP + tsStr;
    String calcSign = hmac(payload);
    if (!constantTimeEquals(calcSign, sign)) throw new SecurityException("I5-SIGN: hmac mismatch");
    long ts;
    try {
      ts = Long.parseLong(tsStr);
    } catch (NumberFormatException e) {
      throw new SecurityException("I5-SKEW: ts parse");
    }
    if (Math.abs(System.currentTimeMillis() - ts) > RATE_SIGN_SKEW_MS) {
      throw new SecurityException("I5-SKEW: ts outside ±5m window");
    }
    if (!RATE_ORACLE_OWNER.equals(owner)) throw new SecurityException("I5-OWNER: owner deny");
    boolean accepted = tryAcquireNonce(nonce);
    if (!accepted) throw new SecurityException("I5-NONCE: duplicate nonce");
    return new RateVerified(src, tgt, rate);
  }

  private static boolean tryAcquireNonce(String nonce) {
    String k = "rate_nonce:" + nonce;
    // Prefer StringRedisTemplate BY TYPE (the bean literally named "redisTemplate" is a
    // RedisTemplate, not a StringRedisTemplate). setIfAbsent is atomic SETNX-with-TTL.
    if (SpringContextUtils.getApplicationContext() != null) {
      try {
        org.springframework.data.redis.core.StringRedisTemplate srt =
            SpringContextUtils.getBean(
                org.springframework.data.redis.core.StringRedisTemplate.class);
        Boolean set =
            srt.opsForValue()
                .setIfAbsent(k, "1", RATE_NONCE_TTL_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        return Boolean.TRUE.equals(set);
      } catch (Exception e) {
        // Security control: fail CLOSED when redis is unavailable in a Spring runtime.
        return false;
      }
    }
    // Non-Spring context (unit tests): no persistence available; keep legacy accept behavior.
    return true;
  }

  private static String generateNonce() {
    byte[] buf = new byte[16];
    RANDOM.nextBytes(buf);
    StringBuilder sb = new StringBuilder(32);
    for (byte b : buf) sb.append(String.format("%02x", b));
    return sb.toString();
  }

  /* ================================================================ */
  /*                              shared utilities                               */
  /* ================================================================ */

  static String hmac(String payload) {
    return SecureHashUtil.hmacSha256Sign(payload, effectiveSignSecret(), "UTF-8");
  }

  /**
   * V2.14-E / S6-4: shared-secret resolution with no hardcoded default. Inside Spring the value
   * MUST be configured via {@code jeecg.mq.message.sign.secret} / {@code MQ_MESSAGE_SIGN_SECRET};
   * absent → fail fast rather than sign with a constant that is public in this repository. The
   * ephemeral branch exists only for unit tests (no Spring context).
   */
  static String effectiveSignSecret() {
    String sec = configuredSignSecret();
    if (StrUtil.isNotBlank(sec)) {
      return sec;
    }
    if (SpringContextUtils.getApplicationContext() != null) {
      throw new IllegalStateException(
          "MQ message sign secret is not configured: set jeecg.mq.message.sign.secret or"
              + " MQ_MESSAGE_SIGN_SECRET (refusing to sign/verify MQ payloads with a default)");
    }
    return NON_SPRING_EPHEMERAL_SECRET;
  }

  static String configuredSignSecret() {
    try {
      if (SpringContextUtils.getApplicationContext() == null) return "";
      Environment env = SpringContextUtils.getApplicationContext().getEnvironment();
      String s = env.getProperty("jeecg.mq.message.sign.secret", "");
      if (StrUtil.isBlank(s)) s = env.getProperty("MQ_MESSAGE_SIGN_SECRET", "");
      return StrUtil.isBlank(s) ? "" : s.trim();
    } catch (Exception e) {
      return "";
    }
  }

  static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) return false;
    byte[] xa = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] xb = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    return MessageDigest.isEqual(xa, xb);
  }

  /* ================================================================ */
  /*                              result DTOs                             */
  /* ================================================================ */

  public static final class LinkSigned {
    public final String linkId;
    public final String expireStr;
    public final String sign;
    public final String debugResetUrlOnlyForWriter;

    LinkSigned(String linkId, String expireStr, String sign, String debugOnlyUrl) {
      this.linkId = linkId;
      this.expireStr = expireStr;
      this.sign = sign;
      this.debugResetUrlOnlyForWriter = debugOnlyUrl;
    }
  }

  public static final class ResolvedLink {
    public final String setPasswordUrl;
    public final String linkExpireTime;
    public final boolean inviteLinkEnabled;

    ResolvedLink(String url, String exp, boolean ok) {
      this.setPasswordUrl = url;
      this.linkExpireTime = exp;
      this.inviteLinkEnabled = ok;
    }
  }

  public static final class RateVerified {
    public final String sourceCurrency;
    public final String targetCurrency;
    public final BigDecimal rate;

    RateVerified(String s, String t, BigDecimal r) {
      this.sourceCurrency = s;
      this.targetCurrency = t;
      this.rate = r;
    }
  }
}
