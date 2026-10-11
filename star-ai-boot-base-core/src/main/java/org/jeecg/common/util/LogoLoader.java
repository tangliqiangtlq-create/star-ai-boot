package org.jeecg.common.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.config.JeecgBaseConfig;
import org.springframework.context.ApplicationContext;

/**
 * LOGO loading tool class reads the global Logo URL from the Nacos configuration item {@code jeecg.logo-url}.
 */
@Slf4j
public class LogoLoader {

  /** Global Logo URL default value (used when Nacos is not configured) */
  public static final String DEFAULT_LOGO_URL = "https://mpay.xnow.xyz/logo.png";

  private static final String LOGO_URL_PROPERTY = "jeecg.logo-url";
  private static final int LOGO_DOWNLOAD_TIMEOUT_MS = 8000;
  private static final ConcurrentHashMap<String, String> DATA_URI_CACHE = new ConcurrentHashMap<>();

  public static String getLogo() {
    try {
      ApplicationContext ctx = SpringContextUtils.getApplicationContext();
      if (ctx != null) {
        String url = ctx.getEnvironment().getProperty(LOGO_URL_PROPERTY);
        if (StrUtil.isNotBlank(url)) {
          return url;
        }
        JeecgBaseConfig config = ctx.getBean(JeecgBaseConfig.class);
        if (config != null && StrUtil.isNotBlank(config.getLogoUrl())) {
          return config.getLogoUrl();
        }
      }
    } catch (Exception ignored) {
      // Fallback to default value when Spring context is not ready
    }
    return DEFAULT_LOGO_URL;
  }

  /** Convert the Logo URL to data URI for inline rendering of PDF/OpenHtmlToPdf (the backend cannot access the localhost front-end Address). */
  public static String toDataUri(String logoUrl) {
    if (StrUtil.isBlank(logoUrl)) {
      logoUrl = getLogo();
    }
    if (logoUrl.startsWith("data:")) {
      return logoUrl;
    }
    return DATA_URI_CACHE.computeIfAbsent(logoUrl, LogoLoader::downloadAsDataUri);
  }

  public static String getLogoDataUri() {
    return toDataUri(getLogo());
  }

  private static String downloadAsDataUri(String url) {
    try {
      byte[] bytes = HttpRequest.get(url).timeout(LOGO_DOWNLOAD_TIMEOUT_MS).execute().bodyBytes();
      if (bytes != null && bytes.length > 0) {
        return "data:" + detectMimeType(url, bytes) + ";base64,"
            + Base64.getEncoder().encodeToString(bytes);
      }
    } catch (Exception e) {
      log.warn("Failed to download logo from {}: {}", url);
    }
    if (!DEFAULT_LOGO_URL.equals(url)) {
      return downloadAsDataUri(DEFAULT_LOGO_URL);
    }
    return "";
  }

  private static String detectMimeType(String url, byte[] bytes) {
    if (bytes.length >= 4 && bytes[0] == (byte) 0x89 && bytes[1] == 0x50) {
      return "image/png";
    }
    if (bytes.length >= 3 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) {
      return "image/jpeg";
    }
    if (bytes.length >= 4 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F') {
      return "image/webp";
    }
    String lower = url == null ? "" : url.toLowerCase();
    if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
      return "image/jpeg";
    }
    if (lower.endsWith(".svg")) {
      return "image/svg+xml";
    }
    if (lower.endsWith(".webp")) {
      return "image/webp";
    }
    return "image/png";
  }
}
