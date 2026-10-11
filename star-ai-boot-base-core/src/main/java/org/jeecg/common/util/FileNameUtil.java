package org.jeecg.common.util;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import org.jeecg.common.util.crypto.SmCryptoUtil;

/** Upload object name generation: random + Whitelist extensions to avoid directly using the client's originalFile name。 */
public class FileNameUtil {

  private static final Set<String> ALLOWED_EXT =
      Set.of(
          ".jpg",
          ".jpeg",
          ".png",
          ".gif",
          ".webp",
          ".bmp",
          ".pdf",
          ".txt",
          ".csv",
          ".xlsx",
          ".xls",
          ".doc",
          ".docx",
          ".zip",
          ".rar",
          ".7z",
          ".mp4",
          ".mp3",
          ".wav",
          ".json",
          ".xml");

  public static String generateSecureFileName(String originalFileName) {
    String fileExtension = getSafeExtension(originalFileName);
    SecureRandom secureRandom = new SecureRandom();
    byte[] randomBytes = new byte[16];
    secureRandom.nextBytes(randomBytes);
    String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    String hashSeed =
        (originalFileName == null ? "file" : originalFileName) + System.currentTimeMillis();
    String hash = SmCryptoUtil.sm3Hex(hashSeed);
    return "file_" + hash.substring(0, 8) + "_" + randomPart + fileExtension;
  }

  /**
   * Sanitize client original filename for display / Content-Disposition.
   * Keeps basename only; strips path / control chars; truncates to 255.
   */
  public static String sanitizeOriginalDisplayName(String originalFileName) {
    if (originalFileName == null) {
      return null;
    }
    String name = originalFileName.replace('\\', '/').trim();
    int slash = name.lastIndexOf('/');
    if (slash >= 0) {
      name = name.substring(slash + 1);
    }
    name = name.replaceAll("[\\r\\n\\t\\u0000]", "").trim();
    if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
      return null;
    }
    if (name.length() > 255) {
      String ext = getSafeExtension(name);
      int keep = 255 - ext.length();
      if (keep < 1) {
        return name.substring(0, 255);
      }
      name = name.substring(0, keep) + ext;
    }
    return name;
  }

  private static String getSafeExtension(String fileName) {
    if (fileName == null) {
      return "";
    }
    int lastIndex = fileName.lastIndexOf('.');
    if (lastIndex <= 0 || lastIndex == fileName.length() - 1) {
      return "";
    }
    String ext = fileName.substring(lastIndex).toLowerCase(Locale.ROOT);
    if (ext.contains("/") || ext.contains("\\") || ext.contains("..")) {
      return "";
    }
    return ALLOWED_EXT.contains(ext) ? ext : "";
  }
}
