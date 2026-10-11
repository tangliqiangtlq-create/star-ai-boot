package org.jeecg.config;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.asymmetric.SM2;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.JwtRs256SecureUtil;
import org.jeecg.common.util.crypto.SmCryptoUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * JWT national-crypto SM2 key loading (same rules for every profile / service).
 *
 * <ol>
 *   <li>{@code jeecg.jwt.sm2PrivateKey} + {@code sm2PublicKey} (or env {@code JEECG_JWT_SM2_*})
 *   <li>{@code jeecg.jwt.sm2Seed} / {@code JEECG_JWT_SM2_SEED} as fallback only
 * </ol>
 */
@Slf4j
@Configuration
public class JWTRSAConfig {

  @Value("${jeecg.jwt.sm2PrivateKey:}")
  private String sm2PrivateKey;

  @Value("${jeecg.jwt.sm2PublicKey:}")
  private String sm2PublicKey;

  @Value("${jeecg.jwt.sm2Seed:}")
  private String sm2Seed;

  /** Compatible legacy RSA placeholders (ignored). */
  @Value("${jeecg.jwt.privateKey:}")
  private String legacyPrivateKey;

  @Value("${jeecg.jwt.publicKey:}")
  private String legacyPublicKey;

  @PostConstruct
  public void initSm2() {
    // Prefer real key material. Unresolved ${ENV} left in Nacos values must not win over env
    // (otherwise first login fails with hutool "unknown byte encoding").
    String privateKey =
        firstRealKey(sm2PrivateKey, propOrEnv("JEECG_JWT_SM2_PRIVATE_KEY"));
    String publicKey =
        firstRealKey(sm2PublicKey, propOrEnv("JEECG_JWT_SM2_PUBLIC_KEY"));

    String source;
    String publicKeyFp;
    if (StrUtil.isNotBlank(privateKey) && StrUtil.isNotBlank(publicKey)) {
      // Keep original Base64: hutool SM2(string,string) often exposes null get*KeyBase64().
      JwtRs256SecureUtil.init(privateKey.trim(), publicKey.trim());
      source = "sm2PrivateKey/sm2PublicKey";
      publicKeyFp = publicKeyFingerprint(publicKey.trim());
    } else {
      String seed = resolveSeed();
      if (StrUtil.isBlank(seed)) {
        throw new IllegalStateException(
            "JWT SM2 not configured. Set jeecg.jwt.sm2PrivateKey + sm2PublicKey "
                + "(or JEECG_JWT_SM2_PRIVATE_KEY / JEECG_JWT_SM2_PUBLIC_KEY), "
                + "or set jeecg.jwt.sm2Seed / JEECG_JWT_SM2_SEED. "
                + "legacyRSAConfigured="
                + (StrUtil.isNotBlank(legacyPrivateKey) && StrUtil.isNotBlank(legacyPublicKey)));
      }
      SM2 sm2 = SmCryptoUtil.createSm2FromSeed(seed);
      source = "sm2Seed";
      log.warn(
          "JWT SM2 using seed fallback; prefer explicit sm2PrivateKey/sm2PublicKey in all environments");
      JwtRs256SecureUtil.init(sm2);
      publicKeyFp = publicKeyFingerprint(sm2);
    }
    log.info("JWT SM2 ready, source={}, publicKeyFp={}", source, publicKeyFp);
  }

  private String resolveSeed() {
    return firstNonBlank(
        sm2Seed,
        propOrEnv("JEECG_JWT_SM2_SEED"),
        System.getProperty("jeecg.jwt.sm2Seed"));
  }

  private static String propOrEnv(String key) {
    String p = System.getProperty(key);
    if (StrUtil.isNotBlank(p)) {
      return p;
    }
    return System.getenv(key);
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String v : values) {
      if (StrUtil.isNotBlank(v)) {
        return v.trim();
      }
    }
    return null;
  }

  /** Skip blank and unresolved {@code ${...}} placeholders that Nacos may leave as literals. */
  private static String firstRealKey(String... values) {
    if (values == null) {
      return null;
    }
    for (String v : values) {
      if (StrUtil.isBlank(v)) {
        continue;
      }
      String t = v.trim();
      if (t.contains("${") && t.contains("}")) {
        continue;
      }
      return t;
    }
    return null;
  }

  private static String publicKeyFingerprint(String publicKeyBase64) {
    try {
      return SmUtil.sm3(publicKeyBase64).substring(0, 16);
    } catch (Exception e) {
      return "unknown";
    }
  }

  private static String publicKeyFingerprint(SM2 sm2) {
    try {
      return SmUtil.sm3(SmCryptoUtil.sm2PublicKeyHex(sm2)).substring(0, 16);
    } catch (Exception e) {
      return "unknown";
    }
  }
}
