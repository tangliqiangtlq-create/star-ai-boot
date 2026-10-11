package org.jeecg.common.util;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.config.JeecgBaseConfig;
import org.jeecg.config.vo.Ssrf;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * SSRF check for outbound HTTP URLs: On the basis of only allowing http(s), parse the host and intercept addresses such as private network/loopback/link local, etc.
 * It also supports configuring the release list, allowing the current request to be released to the same host, and automatically releasing the Elasticsearch cluster node host.
 */
@Slf4j
public final class SsrfUrlValidator {

  private SsrfUrlValidator() {}

  /**
   * Check whether {@code url} is allowed as the target of outbound requests initiated by the server.
   *
   * @throws IllegalArgumentException check failed
   */
  public static void validateOutboundUrl(String url) {
    if (oConvertUtils.isEmpty(url)) {
      return;
    }
    Ssrf cfg = resolveSsrfSettings();
    if (!cfg.isEnabled()) {
      return;
    }

    URI uri;
    try {
      uri = new URI(url.trim());
    } catch (URISyntaxException e) {
      throw new RuntimeException("Illegal request: incorrect URL format", e);
    }

    String scheme = uri.getScheme();
    if (scheme == null
        || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
      throw new RuntimeException("Illegal request: URL must start with http:// or https://");
    }

    String host = uri.getHost();
    if (oConvertUtils.isEmpty(host)) {
      throw new RuntimeException("Illegal request: URL missing hostname");
    }

    String hostLower = host.toLowerCase(Locale.ROOT);
    Set<String> allowed = buildAllowedHosts(cfg);

    if (allowed.contains(hostLower)) {
      return;
    }

    if (isSameOriginAsCurrentRequest(hostLower)) {
      return;
    }

    InetAddress[] addresses;
    try {
      addresses = InetAddress.getAllByName(host);
    } catch (Exception e) {
      log.warn("SSRF validation: unable to resolve host: {}", host, e);
      throw new RuntimeException("Illegal request: unable to parse URL host");
    }

    for (InetAddress addr : addresses) {
      if (isBlocked(addr, cfg)) {
        throw new RuntimeException("Illegal request: access to this network address is forbidden (SSRF protection): " + host);
      }
    }
  }

  private static Ssrf resolveSsrfSettings() {
    JeecgBaseConfig base = getJeecgBaseConfig();
    if (base != null && base.getSsrf() != null) {
      return base.getSsrf();
    }
    return new Ssrf();
  }

  private static JeecgBaseConfig getJeecgBaseConfig() {
    try {
      ApplicationContext ctx = SpringContextUtils.getApplicationContext();
      if (ctx != null) {
        return ctx.getBean(JeecgBaseConfig.class);
      }
    } catch (Exception e) {
      log.debug("SSRF: JeecgBaseConfig unavailable: {}");
    }
    return null;
  }

  private static Set<String> buildAllowedHosts(Ssrf cfg) {
    Set<String> out = new HashSet<>();
    if (oConvertUtils.isNotEmpty(cfg.getAllowedHosts())) {
      for (String part : cfg.getAllowedHosts().split(",")) {
        String h = part.trim().toLowerCase(Locale.ROOT);
        if (oConvertUtils.isNotEmpty(h)) {
          out.add(h);
        }
      }
    }
    JeecgBaseConfig base = getJeecgBaseConfig();
    if (base != null && base.getElasticsearch() != null) {
      String nodes = base.getElasticsearch().getClusterNodes();
      if (oConvertUtils.isNotEmpty(nodes)) {
        for (String segment : nodes.split(",")) {
          String hostFromNode = extractHostFromElasticsearchNode(segment);
          if (oConvertUtils.isNotEmpty(hostFromNode)) {
            out.add(hostFromNode.toLowerCase(Locale.ROOT));
          }
        }
      }
    }
    return out;
  }

  /** Parse the ES node string in the form of {@code host:port} or {@code [ipv6]:port} to get the host part. */
  static String extractHostFromElasticsearchNode(String segment) {
    if (segment == null) {
      return null;
    }
    String s = segment.trim();
    if (s.isEmpty()) {
      return null;
    }
    if (s.startsWith("[")) {
      int end = s.indexOf(']');
      if (end > 1) {
        return s.substring(1, end);
      }
    }
    int lastColon = s.lastIndexOf(':');
    if (lastColon > 0) {
      String after = s.substring(lastColon + 1);
      if (after.matches("\\d+")) {
        return s.substring(0, lastColon);
      }
    }
    return s;
  }

  private static boolean isSameOriginAsCurrentRequest(String urlHostLower) {
    try {
      ServletRequestAttributes attrs =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attrs == null) {
        return false;
      }
      HttpServletRequest req = attrs.getRequest();
      if (req.getServerName() != null
          && urlHostLower.equalsIgnoreCase(req.getServerName().toLowerCase(Locale.ROOT))) {
        return true;
      }
      String hostHeader = req.getHeader("Host");
      if (oConvertUtils.isNotEmpty(hostHeader)) {
        String hostOnly = hostHeader.split(":")[0].trim().toLowerCase(Locale.ROOT);
        if (urlHostLower.equals(hostOnly)) {
          return true;
        }
      }
    } catch (Exception e) {
      log.debug("SSRF same-host check skipped: {}");
    }
    return false;
  }

  private static boolean isBlocked(java.net.InetAddress addr, Ssrf cfg) {
    if (addr.isAnyLocalAddress()) {
      return true;
    }
    if (cfg.isBlockLoopback() && addr.isLoopbackAddress()) {
      return true;
    }
    if (cfg.isBlockLinkLocal() && addr.isLinkLocalAddress()) {
      return true;
    }
    if (cfg.isBlockPrivateNetworks() && addr.isSiteLocalAddress()) {
      return true;
    }
    return false;
  }
}
