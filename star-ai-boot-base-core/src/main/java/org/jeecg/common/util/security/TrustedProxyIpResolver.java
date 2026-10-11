package org.jeecg.common.util.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.constant.CommonConstant;
import org.jeecg.common.util.IpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TrustedProxyIpResolver {

  private static final Logger log = LoggerFactory.getLogger(TrustedProxyIpResolver.class);

  /** Default hops when env/property unset (1 = typical single reverse proxy). */
  private static volatile int defaultTrustedHops = 1;

  private static final String[] PROXY_HEADERS =
      new String[] {
        "x-forwarded-for",
        "X-Forwarded-For",
        "Proxy-Client-IP",
        "WL-Proxy-Client-IP",
        "HTTP_CLIENT_IP",
        "HTTP_X_FORWARDED_FOR",
        "X-Real-IP"
      };

  private TrustedProxyIpResolver() {}

  public static void setDefaultTrustedHops(int hops) {
    defaultTrustedHops = Math.max(0, hops);
  }

  public static int getDefaultTrustedHops() {
    return defaultTrustedHops;
  }

  public static String getStrictClientIp(HttpServletRequest request) {
    return getStrictClientIp(request, defaultTrustedHops);
  }

  public static String getStrictClientIp(HttpServletRequest request, int numberOfTrustedProxies) {
    if (numberOfTrustedProxies < 0) {
      numberOfTrustedProxies = 0;
    }
    String rawChain = null;
    for (String h : PROXY_HEADERS) {
      String v = request.getHeader(h);
      if (StringUtils.isNotEmpty(v) && !CommonConstant.UNKNOWN.equalsIgnoreCase(v.trim())) {
        rawChain = v;
        break;
      }
    }
    List<String> chain = parseChain(rawChain);
    if (chain.isEmpty()) {
      String remote = request.getRemoteAddr();
      return (remote == null) ? "" : remote.trim();
    }
    if (numberOfTrustedProxies == 0) {
      String remote = request.getRemoteAddr();
      if (StringUtils.isEmpty(remote)) {
        return chain.get(chain.size() - 1);
      }
      return remote.trim();
    }
    if (chain.size() <= numberOfTrustedProxies) {
      return chain.get(0);
    }
    return chain.get(chain.size() - 1 - numberOfTrustedProxies);
  }

  public static String getLastProxyNode(HttpServletRequest request) {
    String raw = request.getHeader("x-forwarded-for");
    List<String> chain = parseChain(raw);
    if (chain.isEmpty()) {
      String remote = request.getRemoteAddr();
      return (remote == null) ? "" : remote.trim();
    }
    return chain.get(chain.size() - 1);
  }

  private static List<String> parseChain(String raw) {
    if (raw == null || raw.isEmpty()) {
      return Collections.emptyList();
    }
    String[] parts = raw.split(",");
    List<String> out = new ArrayList<>(parts.length);
    for (String p : parts) {
      if (p == null) {
        continue;
      }
      String t = p.trim();
      if (t.isEmpty()) {
        continue;
      }
      if (CommonConstant.UNKNOWN.equalsIgnoreCase(t)) {
        continue;
      }
      if (!IpUtils.isValidIpAddress(t)) {
        continue;
      }
      out.add(t);
    }
    return Collections.unmodifiableList(out);
  }
}
