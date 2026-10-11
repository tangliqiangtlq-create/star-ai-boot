package org.jeecg.common.util;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.asymmetric.SM2;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.crypto.SmCryptoUtil;

/**
 * JWT issuance/check (national secret SM2). Class names are retained for compatibility with existing injection points.
 *
 * <p>Header alg = {@code SM2}; signed as SM2withSM3.
 */
@Slf4j
public class JwtRs256SecureUtil {

  private static String PRIVATE_KEY_B64;
  private static String PUBLIC_KEY_B64;

  /**
   * Prefer {@link #init(String, String)} for configured Base64 keys. Hutool {@code new SM2(pri, pub)}
   * often leaves {@code getPrivateKeyBase64()}/{@code getPublicKeyBase64()} null; signing with null
   * would mint an ephemeral keypair and break verify.
   */
  public static void init(SM2 sm2) {
    Objects.requireNonNull(sm2, "sm2");
    String pri = sm2.getPrivateKeyBase64();
    String pub = sm2.getPublicKeyBase64();
    if (StrUtil.isBlank(pri) && sm2.getPrivateKey() != null) {
      pri = Base64.encode(sm2.getPrivateKey().getEncoded());
    }
    if (StrUtil.isBlank(pub) && sm2.getPublicKey() != null) {
      pub = Base64.encode(sm2.getPublicKey().getEncoded());
    }
    if (StrUtil.isBlank(pri) || StrUtil.isBlank(pub)) {
      throw new IllegalStateException(
          "SM2 JWT keys unavailable from SM2 instance; call init(privateKeyBase64, publicKeyBase64)");
    }
    init(pri, pub);
  }

  public static void init(String privateKeyBase64, String publicKeyBase64) {
    if (StrUtil.isBlank(privateKeyBase64) || StrUtil.isBlank(publicKeyBase64)) {
      throw new IllegalArgumentException("JWT SM2 privateKey/publicKey required");
    }
    PRIVATE_KEY_B64 = privateKeyBase64.trim();
    PUBLIC_KEY_B64 = publicKeyBase64.trim();
  }

  public static String generateToken(
      String loginId, long expireSeconds, String issuer, String audience) {
    return generateToken(loginId, expireSeconds, issuer, audience, null);
  }

  public static String generateToken(
      String loginId,
      long expireSeconds,
      String issuer,
      String audience,
      Map<String, Object> extraClaims) {
    long now = System.currentTimeMillis();
    JSONObject header = new JSONObject();
    header.set("alg", "SM2");
    header.set("typ", "JWT");

    JSONObject payload = new JSONObject();
    if (StrUtil.isNotBlank(issuer)) {
      payload.set("iss", issuer);
    }
    if (StrUtil.isNotBlank(audience)) {
      payload.set("aud", audience);
    }
    payload.set("sub", loginId);
    payload.set("jti", UUID.randomUUID().toString());
    payload.set("iat", now / 1000);
    payload.set("nbf", now / 1000);
    payload.set("exp", (now + expireSeconds * 1000) / 1000);

    if (extraClaims != null) {
      for (Map.Entry<String, Object> e : extraClaims.entrySet()) {
        if (e.getValue() == null) continue;
        payload.set(e.getKey(), e.getValue());
      }
    }

    String h = Base64.encodeUrlSafe(header.toString().getBytes(StandardCharsets.UTF_8));
    String p = Base64.encodeUrlSafe(payload.toString().getBytes(StandardCharsets.UTF_8));
    String signingInput = h + "." + p;
    byte[] sig =
        SmCryptoUtil.sm2Sign(signingInput.getBytes(StandardCharsets.UTF_8), PRIVATE_KEY_B64);
    return signingInput + "." + Base64.encodeUrlSafe(sig);
  }

  /** SM3 fingerprint of the active verify public key (for cross-service diagnostics). */
  public static String publicKeyFingerprint() {
    if (StrUtil.isBlank(PUBLIC_KEY_B64)) {
      return "unset";
    }
    try {
      return cn.hutool.crypto.SmUtil.sm3(PUBLIC_KEY_B64).substring(0, 16);
    } catch (Exception e) {
      return "unknown";
    }
  }

  public static DecodedJWT verifyToken(String token, String issuer) {
    if (StrUtil.isBlank(PRIVATE_KEY_B64) || StrUtil.isBlank(PUBLIC_KEY_B64)) {
      throw new IllegalStateException("JWT SM2 keys not initialized");
    }
    if (StrUtil.isBlank(token) || token.chars().filter(c -> c == '.').count() != 2) {
      throw new IllegalArgumentException("invalid jwt");
    }
    int lastDot = token.lastIndexOf('.');
    String signingInput = token.substring(0, lastDot);
    String sigB64 = token.substring(lastDot + 1);
    byte[] sig = decodeJwtPart(sigB64);
    if (!SmCryptoUtil.sm2Verify(
        signingInput.getBytes(StandardCharsets.UTF_8), sig, PUBLIC_KEY_B64)) {
      throw new IllegalArgumentException(
          "sm2 signature invalid (publicKeyFp=" + publicKeyFingerprint() + ")");
    }
    DecodedJWT decoded = JWT.decode(token);
    if (StrUtil.isNotBlank(issuer)) {
      String tokenIss = decoded.getIssuer();
      if (!issuer.equals(tokenIss)) {
        throw new IllegalArgumentException("issuer mismatch");
      }
    }
    Date exp = decoded.getExpiresAt();
    if (exp != null && exp.before(new Date())) {
      throw new IllegalArgumentException("token expired");
    }
    return decoded;
  }

  /** JWT signatures are URL-safe Base64; accept standard Base64 as well. */
  private static byte[] decodeJwtPart(String b64) {
    try {
      return java.util.Base64.getUrlDecoder().decode(b64);
    } catch (IllegalArgumentException ignored) {
      return Base64.decode(b64);
    }
  }
}
