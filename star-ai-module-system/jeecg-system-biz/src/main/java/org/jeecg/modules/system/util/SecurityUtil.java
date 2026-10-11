package org.jeecg.modules.system.util;

import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.Mode;
import cn.hutool.crypto.Padding;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import cn.hutool.crypto.symmetric.SymmetricCrypto;
import java.nio.charset.StandardCharsets;
import org.jeecg.common.util.crypto.SmCryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * Datasource password crypto: new ciphertext uses SM4; compatible with AESCBC$ / legacy Hutool AES/ECB.
 */
public class SecurityUtil {
  private static final Logger log = LoggerFactory.getLogger(SecurityUtil.class);

  private static final String CBC_PREFIX = "AESCBC$";
  private static final String KEY_MATERIAL =
      resolveKey("JEECG_DB_AES_KEY", "jeecg.db.aes.key", "JEECGBOOT1423670");

  /**
   * Resolve encryption key from env → sysprop → fallback.
   *
   * <p>When {@code spring.profiles.active} contains {@code prod} / {@code prd} / {@code production},
   * refuse to silently use the built-in fallback key — throw {@link IllegalStateException} at startup.
   * This mirrors the prod fail-fast behaviour in {@code EncryptedString.resolve()}.
   */
  private static String resolveKey(String envKey, String propKey, String fallback) {
    String fromEnv = System.getenv(envKey);
    if (StringUtils.hasText(fromEnv)) {
      return fromEnv.trim();
    }
    String fromProp = System.getProperty(propKey);
    if (StringUtils.hasText(fromProp)) {
      return fromProp.trim();
    }
    if (isProdProfile()) {
      throw new IllegalStateException(
          envKey
              + " / "
              + propKey
              + " must be set when spring.profiles.active contains prod/refusing to use built-in fallback");
    }
    return fallback;
  }

  private static boolean isProdProfile() {
    String active =
        firstNonBlank(
            System.getProperty("spring.profiles.active"),
            System.getenv("SPRING_PROFILES_ACTIVE"));
    if (!StringUtils.hasText(active)) {
      return false;
    }
    for (String p : active.split(",")) {
      String v = p.trim().toLowerCase();
      if (v.contains("prod") || v.contains("prd") || v.contains("production")) {
        return true;
      }
    }
    return false;
  }

  private static String firstNonBlank(String a, String b) {
    if (StringUtils.hasText(a)) {
      return a;
    }
    return b;
  }

  public static String jiami(String content) {
    try {
      String keyHex = SmCryptoUtil.deriveSm4KeyHex(KEY_MATERIAL);
      return SmCryptoUtil.sm4Encrypt(content, keyHex);
    } catch (Exception e) {
      log.error("db password encrypt failed", e);
      throw new IllegalStateException("encrypt failed", e);
    }
  }

  public static String jiemi(String encryptResultStr) {
    if (!StringUtils.hasText(encryptResultStr)) {
      return encryptResultStr;
    }
    if (encryptResultStr.startsWith(SmCryptoUtil.SM4_PREFIX)
        || (encryptResultStr.contains(":") && !encryptResultStr.startsWith(CBC_PREFIX))) {
      try {
        return SmCryptoUtil.sm4Decrypt(
            encryptResultStr, SmCryptoUtil.deriveSm4KeyHex(KEY_MATERIAL));
      } catch (Exception e) {
        log.debug("sm4 decrypt miss, try legacy", e);
      }
    }
    if (encryptResultStr.startsWith(CBC_PREFIX)) {
      String body = encryptResultStr.substring(CBC_PREFIX.length());
      int sep = body.indexOf('$');
      if (sep <= 0) {
        throw new IllegalArgumentException("invalid AESCBC payload");
      }
      byte[] iv = HexUtil.decodeHex(body.substring(0, sep));
      String cipherHex = body.substring(sep + 1);
      byte[] keyBytes = padOrTrimKey(KEY_MATERIAL).getBytes(StandardCharsets.UTF_8);
      AES aes = new AES(Mode.CBC, Padding.PKCS5Padding, keyBytes, iv);
      return aes.decryptStr(cipherHex, CharsetUtil.CHARSET_UTF_8);
    }
    SymmetricCrypto aes =
        new SymmetricCrypto(
            SymmetricAlgorithm.AES, padOrTrimKey(KEY_MATERIAL).getBytes(StandardCharsets.UTF_8));
    return aes.decryptStr(encryptResultStr, CharsetUtil.CHARSET_UTF_8);
  }

  private static String padOrTrimKey(String raw) {
    byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
    byte[] key = new byte[16];
    System.arraycopy(bytes, 0, key, 0, Math.min(bytes.length, 16));
    return new String(key, StandardCharsets.UTF_8);
  }

  public static boolean usingBuiltinKey() {
    return StrUtil.equals("JEECGBOOT1423670", KEY_MATERIAL);
  }
}
