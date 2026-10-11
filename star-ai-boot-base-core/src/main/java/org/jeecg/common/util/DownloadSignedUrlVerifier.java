package org.jeecg.common.util;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.jeecg.common.exception.JeecgBootException;

/**
 * Short-lived signed URL generator / verifier for file downloads and other one-shot access tokens.
 *
 * <p>Signature = Base64url(SM3-HMAC-like using SHA-256 over {@code objectId | userId | expireAtEpochSec | usageScenarios | nonce | masterSecret})
 *
 * <p>Production requires {@code DOWNLOAD_SIGN_SECRET} env or {@code jeecg.download.sign.secret} sysprop
 * to be 32+ chars; otherwise boot of any controller issuing signed URLs must fail-fast.
 */
public final class DownloadSignedUrlVerifier {

  public static final String QUERY_SIG = "_dsig";
  public static final String QUERY_EXP = "_dexp";
  public static final String QUERY_NONCE = "_dnonce";
  public static final String QUERY_USER = "_duid";
  public static final String QUERY_SCEN = "_dusc";
  private static final int DEFAULT_TTL_SEC = 5 * 60;
  private static final Object LOCK = new Object();
  private static volatile String cachedSecret;

  private DownloadSignedUrlVerifier() {}

  public static int defaultTtlSec() {
    return DEFAULT_TTL_SEC;
  }

  private static String masterSecret() {
    if (cachedSecret != null) {
      return cachedSecret;
    }
    synchronized (LOCK) {
      if (cachedSecret != null) {
        return cachedSecret;
      }
      String s = System.getProperty("jeecg.download.sign.secret");
      if (StrUtil.isBlank(s)) {
        s = System.getenv("DOWNLOAD_SIGN_SECRET");
      }
      if (StrUtil.isBlank(s) || s.length() < 16) {
        throw new JeecgBootException(
            "DOWNLOAD_SIGN_SECRET env / jeecg.download.sign.secret sysprop is blank or < 16 chars; production signing is disabled.");
      }
      cachedSecret = s.intern();
      return cachedSecret;
    }
  }

  /** Generate a signed download token string (appended as query params). Lifetime = 5 minutes. */
  public static String issueSignedQuery(String objectId, String userId, String usageScenarios) {
    return issueSignedQuery(objectId, userId, usageScenarios, DEFAULT_TTL_SEC);
  }

  public static String issueSignedQuery(
      String objectId, String userId, String usageScenarios, int ttlSec) {
    if (StrUtil.isBlank(objectId)) {
      throw new IllegalArgumentException("objectId required");
    }
    long expireAt = System.currentTimeMillis() / 1000L + Math.max(30, ttlSec);
    byte[] nonceBytes = new byte[8];
    new SecureRandom().nextBytes(nonceBytes);
    String nonce = HexUtil.encodeHexStr(nonceBytes);
    String uid = StrUtil.blankToDefault(userId, "anon");
    String usc = StrUtil.blankToDefault(usageScenarios, "ANY");
    String sig =
        computeSigBase64url(objectId, uid, usc, expireAt, nonce, masterSecret());
    return QUERY_SIG
        + "="
        + sig
        + "&"
        + QUERY_EXP
        + "="
        + expireAt
        + "&"
        + QUERY_NONCE
        + "="
        + nonce
        + "&"
        + QUERY_USER
        + "="
        + uid
        + "&"
        + QUERY_SCEN
        + "="
        + usc;
  }

  /** Verifies a signed download request; non-matching userId will still pass if sig matches (public share). */
  public static boolean verifySignedQuery(
      String objectId,
      String providedUserId,
      String usageScenarios,
      String sig,
      String expireAtStr,
      String nonce,
      String sigUserId,
      String sigUsageScenarios) {
    if (StrUtil.hasBlank(sig, expireAtStr, nonce, sigUserId, sigUsageScenarios)) {
      return false;
    }
    long expireAt;
    try {
      expireAt = Long.parseLong(expireAtStr);
    } catch (NumberFormatException nfe) {
      return false;
    }
    if (expireAt <= System.currentTimeMillis() / 1000L) {
      return false;
    }
    if (StrUtil.isNotBlank(providedUserId) && !providedUserId.equals(sigUserId)) {
      return false;
    }
    if (StrUtil.isNotBlank(usageScenarios) && !usageScenarios.equals(sigUsageScenarios)) {
      return false;
    }
    String expected =
        computeSigBase64url(objectId, sigUserId, sigUsageScenarios, expireAt, nonce, masterSecret());
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.US_ASCII), sig.getBytes(StandardCharsets.US_ASCII));
  }

  private static String computeSigBase64url(
      String objectId,
      String userId,
      String usageScenarios,
      long expireAt,
      String nonce,
      String secret) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(objectId.getBytes(StandardCharsets.UTF_8));
      md.update((byte) 0x1f);
      md.update(userId.getBytes(StandardCharsets.UTF_8));
      md.update((byte) 0x1f);
      md.update(usageScenarios.getBytes(StandardCharsets.UTF_8));
      md.update((byte) 0x1f);
      md.update(Long.toString(expireAt).getBytes(StandardCharsets.US_ASCII));
      md.update((byte) 0x1f);
      md.update(nonce.getBytes(StandardCharsets.UTF_8));
      md.update((byte) 0x1f);
      md.update(secret.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(md.digest());
    } catch (Exception e) {
      throw new IllegalStateException("sha256 not available", e);
    }
  }
}
