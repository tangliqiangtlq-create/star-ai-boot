package org.jeecg.common.util.encryption;

import lombok.Data;
import org.springframework.util.StringUtils;

/**
 * AES key/IV parse.
 *
 * <p>The production environment must be overridden through environment variables or system properties, and reliance on the warehouse's built-in defaults is prohibited:
 *
 * <ul>
 *   <li>{@code JEECG_AES_KEY} / {@code jeecg.aes.key}
 *   <li>{@code JEECG_AES_IV} / {@code jeecg.aes.iv}
 * </ul>
 *
 * <p>when {@code spring.profiles.active}/{@code SPRING_PROFILES_ACTIVE} Contains {@code prod} and not configured,
 * Refuse to silently use built-in defaults at startup (throws IllegalStateException）。
 */
@Data
public class EncryptedString {

  private static final String BUILTIN_KEY = "1234567890adbcde";
  private static final String BUILTIN_IV = "1234567890hjlkew";

  /** The length is16characters */
  public static String key = resolve("JEECG_AES_KEY", "jeecg.aes.key", BUILTIN_KEY);

  /** The length is16characters */
  public static String iv = resolve("JEECG_AES_IV", "jeecg.aes.iv", BUILTIN_IV);

  private static String resolve(String envKey, String propKey, String fallback) {
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
          envKey + " / " + propKey + " must be set when spring.profiles.active contains prod");
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
      // V2.15-4: same prod-like detection as GatewayCorsConfiguration — an exact "prod" match
      // missed "production"/"prd" and let the warehouse AES key survive into a production boot.
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

  /** Whether the warehouse default key is still being used (production should return false）。 */
  public static boolean usingBuiltinDefaults() {
    return BUILTIN_KEY.equals(key) && BUILTIN_IV.equals(iv);
  }
}
