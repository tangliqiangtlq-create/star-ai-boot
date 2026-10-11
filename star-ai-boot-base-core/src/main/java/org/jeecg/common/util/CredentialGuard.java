package org.jeecg.common.util;

import java.util.Arrays;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * Credential null value protection: It is forbidden to use null/empty string as a valid password to participate in checking or protecting resources (preventing Null Password bypass, SECZ-SSCSP-CODE-012 extension).
 *
 * <p>B5-3 weak-password blocklist: Password strings that match {@link #isGenericWeakPassword(String)} are rejected at every entry point (import, reset, change, registration). Production profile additionally blocks the infamous {@code 123456} bootstrap password used by Jeecg's legacy Excel import flow.
 */
public final class CredentialGuard {

  private static final String[] WEAK_PREFIX_PATTERNS = {
      "123456", "password", "qwerty", "abc123", "letmein", "admin",
      "iloveyou", "welcome", "monkey", "dragon", "master", "111111",
      "654321", "superman", "trustno1", "sunshine", "princess",
      "football", "baseball", "shadow", "pass@123", "welcome1", "qazwsx"
  };

  private CredentialGuard() {}

  public static boolean hasText(String value) {
    return StringUtils.hasText(value);
  }

  /** The login Password of user submit must be non-empty. */
  public static boolean isPresentLoginPassword(String loginPassword) {
    return StringUtils.hasText(loginPassword);
  }

  /** The login Password (char[]) of user submit must be non-empty. */
  public static boolean isPresentLoginPassword(char[] loginPassword) {
    return loginPassword != null && loginPassword.length > 0;
  }

  /** The persistently stored Password hash must exist. */
  public static boolean hasStoredPasswordHash(String storedPassword) {
    return StringUtils.hasText(storedPassword);
  }

  /**
   * Compare with the hash in the library: if either side is empty, false is returned to avoid mismatching {@code null.equals(...)} or empty hash.
   */
  public static boolean matchesStoredPassword(String encodedInput, String storedHash) {
    if (!StringUtils.hasText(encodedInput) || !StringUtils.hasText(storedHash)) {
      return false;
    }
    return encodedInput.equals(storedHash);
  }

  /**
   * Clear sensitive {@code char[]} in memory (such as RSA decryption results).
   * Only do {@link Arrays#fill} and <strong>do not</strong> assign the variable to {@code null} to avoid static scanning misidentification as Null Password bypass.
   */
  public static void wipeSensitiveChars(char[] sensitive) {
    if (sensitive != null && sensitive.length > 0) {
      Arrays.fill(sensitive, '\0');
    }
  }

  /**
   * Returns true when the candidate plaintext password matches the generic weak password blocklist (case insensitive,
   * leading/trailing digits/symbols).
   */
  public static boolean isGenericWeakPassword(String candidate) {
    if (!StringUtils.hasText(candidate)) {
      return true;
    }
    String s = candidate.trim().toLowerCase(Locale.ROOT);
    if (s.length() < 8) {
      return true;
    }
    for (String p : WEAK_PREFIX_PATTERNS) {
      if (s.equals(p) || s.startsWith(p) || s.endsWith(p)) {
        return true;
      }
    }
    return false;
  }
}
