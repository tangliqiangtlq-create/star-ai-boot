package org.jeecg.common.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.util.security.TrustedProxyIpResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IP address helpers.
 *
 * <p>A1-3: {@link #getIpAddr} uses trusted-proxy hop counting (right-to-left on XFF) instead of
 * blindly taking the client-controllable first XFF segment.
 */
public class IpUtils {
  private static final Logger logger = LoggerFactory.getLogger(IpUtils.class);

  /**
   * Resolve client IP via {@link TrustedProxyIpResolver} with configurable trusted hops.
   *
   * <p>Hops: {@code JEECG_TRUSTED_PROXY_HOPS} / {@code jeecg.trusted-proxy.hops} (default {@code 1}).
   * Use {@code 0} to ignore XFF entirely and use {@code remoteAddr} only.
   */
  public static String getIpAddr(HttpServletRequest request) {
    if (request == null) {
      return "";
    }
    try {
      return TrustedProxyIpResolver.getStrictClientIp(request, resolveTrustedHops());
    } catch (Exception e) {
      logger.error("IPUtils ERROR ", e);
      String remote = request.getRemoteAddr();
      return remote == null ? "" : remote.trim();
    }
  }

  private static int resolveTrustedHops() {
    String raw =
        firstNonBlank(
            System.getenv("JEECG_TRUSTED_PROXY_HOPS"),
            System.getProperty("jeecg.trusted-proxy.hops"),
            System.getProperty("jeecg.trustedProxy.hops"));
    if (StringUtils.isBlank(raw)) {
      return TrustedProxyIpResolver.getDefaultTrustedHops();
    }
    try {
      return Math.max(0, Integer.parseInt(raw.trim()));
    } catch (NumberFormatException e) {
      return TrustedProxyIpResolver.getDefaultTrustedHops();
    }
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String v : values) {
      if (StringUtils.isNotBlank(v)) {
        return v;
      }
    }
    return null;
  }

  public static boolean isValidIpAddress(String ipAddress) {
    String ipPattern =
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
    Pattern pattern = Pattern.compile(ipPattern);
    Matcher matcher = pattern.matcher(ipAddress);
    return matcher.matches();
  }

  public static String getServerIp() {
    try {
      InetAddress inetAddress = InetAddress.getLocalHost();
      return inetAddress.getHostAddress();
    } catch (UnknownHostException e) {
      logger.error("Failed to get IP address", e);
    }
    return "";
  }
}
