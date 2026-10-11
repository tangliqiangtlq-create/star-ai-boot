package org.jeecg.common.util.security;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import org.jeecg.common.util.SecureHashUtil;
import org.jeecg.common.util.SpringContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * I1: remove exact Redis keys from MQ payload; use scene + opaqueRef + HMAC instead.
 *
 * <p>Producers must never send otpTmpPlainKey plaintext keys; after a successful consumer decode,
 * the Redis key is deleted automatically—callers must not call redisUtil.del(tmpPlainKey) manually.
 *
 * <h3>Two-week backward compatibility</h3>
 * New consumers handle both legacy otpTmpPlainKey (via backwardCompatReadIfOldFormat) and the new
 * triple format, avoiding OTP loss when producers upgrade before consumers. Rollout order: upgrade
 * all consumers with compat layer -> canary one instance -> upgrade producers to the new format.
 */
public final class OpaqueMqCodec {
  private static final Logger log = LoggerFactory.getLogger(OpaqueMqCodec.class);

  /**
   * SCENE_WHITELIST: only scenes in this set may use the codec.
   * Any non-whitelisted scene throws IllegalArgumentException to block abuse.
   */
  public static final Set<String> SCENE_WHITELIST =
      Collections.unmodifiableSet(
          new HashSet<>(
              Arrays.asList(
                  "TWO_FACTOR_AUTHENTICATION",
                  "LOGIN_OTP",
                  "PASSWORD_RESET_OTP",
                  "EMAIL_VERIFY_OTP")));

  /**
   * Hard TTL cap: producer TTL above MAX_TTL_SECONDS is clamped; long-lived OTP in Redis is
   * forbidden. 2026-09-30: raised 10s → 300s. A 10s transport window could not survive normal
   * async email delivery (MQ backlog / service restart / retry backoff) — the consumer reads and
   * DELETES the key on success, so the plaintext exposure is bounded by min(ttl, until-consumed)
   * anyway, exactly like the legacy otpTmpPlainKey flow.
   */
  public static final int MAX_TTL_SECONDS = 300;

  /**
   * Default transport TTL when the producer does not request one (ttlSeconds &lt;= 0), overridable
   * via property {@code jeecg.mq.otp.transport.ttl.seconds} or env {@code
   * OTP_MQ_TRANSPORT_TTL_SECONDS}.
   */
  public static final int DEFAULT_TTL_SECONDS = 120;

  /**
   * Compat switch for the legacy {@code otpTmpPlainKey} read path.
   *
   * <p>V2.14-E / S6-4: permanently OFF. The compat window is over (SLA ended 2026-10-11 and the
   * new triple format has been in production), and leaving it on let any MQ publisher name an
   * arbitrary Redis key in the payload, which the consumer then read AND deleted.
   */
  public static final boolean BACKWARD_COMPAT_READ_OLD_FORMAT = false;

  static final String REDIS_KEY_PREFIX = "OTP:";
  static final String REDIS_KEY_SEP = ":";
  static final String PAYLOAD_SEP = "|";
  static final String SIGN_SEP = "|";

  private static final SecureRandom RANDOM = new SecureRandom();

  /**
   * V2.14-E / S6-4: ephemeral per-JVM secret used ONLY when there is no Spring context (plain unit
   * tests). Production code always runs inside Spring, where a missing secret is a hard failure —
   * see {@link #effectiveSignSecret()}.
   */
  private static final String NON_SPRING_EPHEMERAL_SECRET =
      generateOpaqueRef(32) + generateOpaqueRef(32);

  private OpaqueMqCodec() {}

  /**
   * Producer encode: scene + opaqueRef + expireTs + sign, and stores plaintext captcha at
   * {@code OTP:{scene}:{opaqueRef}} without exposing the exact key on MQ.
   *
   * @param scene one of SCENE_WHITELIST
   * @param plainTextValue captcha or other secret (e.g. 6-digit code)
   * @param ttlSeconds desired TTL; non-positive uses the configured default (see {@link
   *     #DEFAULT_TTL_SECONDS}), values above MAX_TTL_SECONDS are clamped
   * @return four fields for the producer to set on the MQ JSONObject
   * @throws IllegalStateException when the Redis write fails — the producer must NOT publish an MQ
   *     message whose OTP payload can never be decoded (fail-fast instead of fire-and-forget)
   */
  public static OpaqueResult encodeForProducer(String scene, String plainTextValue, int ttlSeconds) {
    if (StrUtil.isBlank(scene)) throw new IllegalArgumentException("scene blank");
    if (!SCENE_WHITELIST.contains(scene)) throw new IllegalArgumentException("scene not in whitelist: " + scene);
    if (StrUtil.isBlank(plainTextValue)) throw new IllegalArgumentException("plainTextValue blank");
    if (ttlSeconds <= 0) ttlSeconds = configuredTransportTtlSeconds();
    if (ttlSeconds > MAX_TTL_SECONDS) ttlSeconds = MAX_TTL_SECONDS;

    String opaqueRef = generateOpaqueRef(32);
    long expireTs = System.currentTimeMillis() + ttlSeconds * 1000L;
    String redisKey = REDIS_KEY_PREFIX + scene + REDIS_KEY_SEP + opaqueRef;

    writeOtpToRedisOrFail(redisKey, plainTextValue, ttlSeconds, scene);

    String payloadPlain = scene + SIGN_SEP + opaqueRef + SIGN_SEP + expireTs;
    String sign = hmac(payloadPlain);
    return new OpaqueResult(scene, opaqueRef, expireTs, sign);
  }

  /**
   * For consumers such as SendEmailTopic: read MQ JSONObject, try new triple format first, then
   * backwardCompatReadIfOldFormat for legacy payloads. On success, <b>deletes the Redis key</b>;
   * callers must not call redisUtil.del(tmpKey) manually.
   *
   * @param data MQ payload
   * @return plaintext captcha, or null if unavailable (caller should treat as expired OTP)
   */
  public static String decodeForConsumerOrNull(JSONObject data) {
    if (data == null) return null;
    String scene = data.getStr("scene");
    String opaqueRef = data.getStr("otpOpaqueRef");
    String expireTsStr = data.getStr("otpExpireTs");
    String sign = data.getStr("otpSign");
    String value =
        (scene != null && opaqueRef != null && expireTsStr != null && sign != null)
            ? tryDecodeNewFormat(scene, opaqueRef, expireTsStr, sign)
            : null;
    if (value != null) return value;
    return BACKWARD_COMPAT_READ_OLD_FORMAT ? backwardCompatReadIfOldFormat(data) : null;
  }

  private static String tryDecodeNewFormat(
      String scene, String opaqueRef, String expireTsStr, String sign) {
    if (!SCENE_WHITELIST.contains(scene)) {
      log.warn("I1 decode scene not in whitelist: scene={}", scene);
      return null;
    }
    long expireTs;
    try {
      expireTs = Long.parseLong(expireTsStr);
    } catch (NumberFormatException e) {
      log.warn("I1 decode expireTs parse fail: {}", expireTsStr);
      return null;
    }
    long now = System.currentTimeMillis();
    if (now > expireTs) {
      // 2026-09-30: was log.debug — silent expiry made decode failures undiagnosable.
      log.warn(
          "I1 decode EXPIRED: scene={}, expiredMsAgo={}; payload is permanently undecodable (transport TTL {}s)",
          scene,
          now - expireTs,
          MAX_TTL_SECONDS);
      return null;
    }
    String calcPayload = scene + SIGN_SEP + opaqueRef + SIGN_SEP + expireTs;
    String calcSign = hmac(calcPayload);
    if (calcSign == null || !constantTimeEquals(calcSign, sign)) {
      log.warn("I1 decode HMAC mismatched scene={}", scene);
      return null;
    }
    String redisKey = REDIS_KEY_PREFIX + scene + REDIS_KEY_SEP + opaqueRef;
    String value = (String) getFromRedis(redisKey);
    if (value != null) {
      deleteFromRedis(redisKey);
    } else {
      // 2026-09-30: was silent — distinguishes "producer redis write failed / entry already
      // consumed / redis blip" from the EXPIRED branch above for incident triage.
      log.warn(
          "I1 decode REDIS MISS after HMAC ok: scene={}, refPrefix={}",
          scene,
          StrUtil.isBlank(opaqueRef) ? "null" : opaqueRef.substring(0, Math.min(8, opaqueRef.length())));
    }
    return value;
  }

  /**
   * Legacy read path, retained but no longer reachable from {@link #decodeForConsumerOrNull}
   * ({@link #BACKWARD_COMPAT_READ_OLD_FORMAT} is false). Keys are validated against the OTP key
   * grammar (see {@link #isWellFormedOtpKey}) before any Redis access, and the key is deleted
   * after a successful read to prevent reuse.
   */
  public static String backwardCompatReadIfOldFormat(JSONObject data) {
    if (data == null) return null;
    String oldKey = data.getStr("otpTmpPlainKey");
    if (StrUtil.isBlank(oldKey)) return null;
    // V2.14-E / S6-4: the payload is attacker-controllable, so never hand an arbitrary string to
    // Redis. Only well-formed OTP keys (OTP:{whitelisted scene}:{hex ref}) may be read/deleted.
    if (!isWellFormedOtpKey(oldKey)) {
      log.warn(
          "I1 backward compat rejected malformed legacy key: keyPrefix={}",
          oldKey.length() >= 16 ? oldKey.substring(0, 16) : oldKey);
      return null;
    }
    String value = (String) getFromRedis(oldKey);
    if (value != null) deleteFromRedis(oldKey);
    if (value == null) {
      log.warn(
          "I1 backward compat old-format key miss: keyPrefix={}",
          oldKey.length() >= 16 ? oldKey.substring(0, 16) : "null");
    }
    return value;
  }

  /**
   * V2.14-E / S6-4: grammar check for OTP Redis keys — {@code OTP:{scene}:{hex ref}} with a
   * whitelisted scene and a reference of at least 16 hex characters ({@link
   * #generateOpaqueRef} produces 64). Anything else is rejected before touching Redis.
   */
  static boolean isWellFormedOtpKey(String key) {
    if (key == null || !key.startsWith(REDIS_KEY_PREFIX)) return false;
    String rest = key.substring(REDIS_KEY_PREFIX.length());
    int sep = rest.indexOf(REDIS_KEY_SEP);
    if (sep <= 0 || sep == rest.length() - 1) return false;
    if (!SCENE_WHITELIST.contains(rest.substring(0, sep))) return false;
    String ref = rest.substring(sep + 1);
    if (ref.length() < 16) return false;
    for (int i = 0; i < ref.length(); i++) {
      char c = ref.charAt(i);
      boolean hex =
          (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
      if (!hex) return false;
    }
    return true;
  }

  /**
   * Producer-side write. Prefers the StringRedisTemplate bean (resolved BY TYPE — the jeecg
   * bean literally named "redisTemplate" is a RedisTemplate, not a StringRedisTemplate) and
   * falls back to the jeecg RedisUtil whose real signatures are
   * {@code boolean set(String, Object, long)} / {@code Object get(String)} /
   * {@code void del(String...)} (jeecg-boot-common 3.7.0).
   *
   * <p>2026-10-01: the previous reflection used Object.class for the first parameter and
   * therefore always threw NoSuchMethodException via Class#getMethod (exact signature match),
   * so the OTP key was silently never written while the MQ message was still published.
   *
   * @throws IllegalStateException on write failure or when neither redis access path is usable
   *     inside a Spring context (caller must not publish the MQ message)
   */
  private static void writeOtpToRedisOrFail(String redisKey, String value, int ttlSeconds,
      String scene) {
    StringRedisTemplate tpl = resolveStringRedisTemplate();
    if (tpl != null) {
      try {
        tpl.opsForValue().set(redisKey, value, ttlSeconds, TimeUnit.SECONDS);
        return;
      } catch (Exception e) {
        throw new IllegalStateException(
            "I1 encode: StringRedisTemplate write failed for " + REDIS_KEY_PREFIX + scene
                + "/*; aborting MQ publish (message would be permanently undecodable)",
            e);
      }
    }
    Object ru = resolveRedisUtilOrNull();
    if (ru == null) {
      // Non-Spring context (unit tests): no persistence available; encode still returns fields.
      log.debug("I1 encode: no redis bean available, skip OTP persistence (non-Spring env)");
      return;
    }
    try {
      ru.getClass()
          .getMethod("set", String.class, Object.class, long.class)
          .invoke(ru, redisKey, value, (long) ttlSeconds);
    } catch (Exception e) {
      throw new IllegalStateException(
          "I1 encode: redisUtil write failed for " + REDIS_KEY_PREFIX + scene
              + "/*; aborting MQ publish (message would be permanently undecodable)",
          e);
    }
  }

  private static StringRedisTemplate resolveStringRedisTemplate() {
    if (SpringContextUtils.getApplicationContext() == null) return null;
    try {
      return SpringContextUtils.getBean(StringRedisTemplate.class);
    } catch (Exception ignored) {
    }
    try {
      Object byName = SpringContextUtils.getBean("stringRedisTemplate");
      return byName instanceof StringRedisTemplate ? (StringRedisTemplate) byName : null;
    } catch (Exception ignored) {
    }
    return null;
  }

  private static Object resolveRedisUtilOrNull() {
    try {
      return SpringContextUtils.getBean("redisUtil");
    } catch (Exception e) {
      return null;
    }
  }

  private static Object getFromRedis(String key) {
    // Must use the SAME access path as writeOtpToRedisOrFail so serializers stay symmetric.
    StringRedisTemplate tpl = resolveStringRedisTemplate();
    if (tpl != null) {
      try {
        return tpl.opsForValue().get(key);
      } catch (Exception ignored) {
        return null;
      }
    }
    Object ru = resolveRedisUtilOrNull();
    if (ru == null) return null;
    try {
      // RedisUtil.get(java.lang.String) — NOT get(Object); exact signature match required.
      return ru.getClass().getMethod("get", String.class).invoke(ru, key);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static void deleteFromRedis(String key) {
    StringRedisTemplate tpl = resolveStringRedisTemplate();
    if (tpl != null) {
      try {
        tpl.delete(key);
      } catch (Exception ignored) {
      }
      return;
    }
    Object ru = resolveRedisUtilOrNull();
    if (ru == null) return;
    try {
      // RedisUtil.del(java.lang.String...) — varargs = String[].class, invoke with a single
      // Object arg holding the array.
      ru.getClass().getMethod("del", String[].class).invoke(ru, (Object) new String[] {key});
    } catch (Exception ignored) {
    }
  }

  private static String configuredSignSecret() {
    try {
      if (SpringContextUtils.getApplicationContext() == null) return "";
      Environment env = SpringContextUtils.getApplicationContext().getEnvironment();
      String s = env.getProperty("jeecg.mq.message.sign.secret", "");
      if (StrUtil.isBlank(s)) {
        s = env.getProperty("MQ_MESSAGE_SIGN_SECRET", "");
      }
      return StrUtil.isBlank(s) ? "" : s.trim();
    } catch (Exception e) {
      return "";
    }
  }

  /**
   * Transport TTL when the producer does not request one: property {@code
   * jeecg.mq.otp.transport.ttl.seconds} or env {@code OTP_MQ_TRANSPORT_TTL_SECONDS}; falls back to
   * {@link #DEFAULT_TTL_SECONDS}. encodeForProducer still clamps the result to {@link
   * #MAX_TTL_SECONDS}.
   */
  static int configuredTransportTtlSeconds() {
    try {
      if (SpringContextUtils.getApplicationContext() != null) {
        Environment env = SpringContextUtils.getApplicationContext().getEnvironment();
        String v =
            env.getProperty(
                "jeecg.mq.otp.transport.ttl.seconds",
                env.getProperty("OTP_MQ_TRANSPORT_TTL_SECONDS", ""));
        if (StrUtil.isNotBlank(v)) {
          int parsed = Integer.parseInt(v.trim());
          if (parsed > 0) return parsed;
        }
      }
    } catch (Exception ignored) {
    }
    return DEFAULT_TTL_SECONDS;
  }

  static String hmac(String payload) {
    return SecureHashUtil.hmacSha256Sign(payload, effectiveSignSecret(), "UTF-8");
  }

  /**
   * V2.14-E / S6-4: the secret used to sign/verify OTP payloads. There is deliberately NO
   * hardcoded default — a static fallback in an open repository makes every signature forgeable.
   * Inside Spring the value MUST come from {@code jeecg.mq.message.sign.secret} / {@code
   * MQ_MESSAGE_SIGN_SECRET}; if it is absent we fail fast instead of signing with a known constant.
   * The ephemeral branch is only for unit tests, which never run a Spring context.
   */
  static String effectiveSignSecret() {
    String sec = configuredSignSecret();
    if (StrUtil.isNotBlank(sec)) {
      return sec;
    }
    if (SpringContextUtils.getApplicationContext() != null) {
      throw new IllegalStateException(
          "MQ message sign secret is not configured: set jeecg.mq.message.sign.secret or"
              + " MQ_MESSAGE_SIGN_SECRET (refusing to sign/verify OTP payloads with a default)");
    }
    return NON_SPRING_EPHEMERAL_SECRET;
  }

  static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) return false;
    byte[] xa = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] xb = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    return MessageDigest.isEqual(xa, xb);
  }

  /** Producer helper: writes the four fields to JSONObject with consistent names. */
  public static void applyToJson(JSONObject msg, OpaqueResult r) {
    if (msg == null || r == null) return;
    msg.set("scene", r.scene);
    msg.set("otpOpaqueRef", r.opaqueRef);
    msg.set("otpExpireTs", String.valueOf(r.expireTs));
    msg.set("otpSign", r.sign);
  }

  /** 128-bit entropy random string (Base16, length 64). */
  public static String generateOpaqueRef(int bytes) {
    if (bytes <= 0) bytes = 16;
    byte[] buf = new byte[bytes];
    RANDOM.nextBytes(buf);
    StringBuilder sb = new StringBuilder(buf.length * 2);
    for (byte b : buf) sb.append(String.format("%02x", b));
    return sb.toString();
  }

  /** Immutable producer encode result (four fields). */
  public static final class OpaqueResult {
    public final String scene;
    public final String opaqueRef;
    public final long expireTs;
    public final String sign;

    OpaqueResult(String scene, String opaqueRef, long expireTs, String sign) {
      this.scene = scene;
      this.opaqueRef = opaqueRef;
      this.expireTs = expireTs;
      this.sign = sign;
    }
  }
}
