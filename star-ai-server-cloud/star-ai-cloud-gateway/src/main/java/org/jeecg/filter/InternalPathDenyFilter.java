package org.jeecg.filter;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * PR-CL-B1: Hard deny-list for internal-only paths on the gateway edge (order = HIGHEST_PRECEDENCE -
 * 100, so it runs BEFORE auth, rate limiting, WAF and any business filters).
 *
 * <p>Scope: any path that, by design, is only reachable via internal Feign / local network
 * invocations and must never terminate on external ingress.
 *
 * <p>Also denies product-decommissioned paths (e.g. {@code /dcs/storedValueCard/**}) so leftover
 * clients cannot hit old ingress even if a stale JAR is deployed.
 */
@Component
public class InternalPathDenyFilter implements WebFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(InternalPathDenyFilter.class);

  private static final byte[] FORBIDDEN_BODY =
      "{\"success\":false,\"code\":40301,\"message\":\"access denied: internal endpoint\"}"
          .getBytes(StandardCharsets.UTF_8);

  private static final Set<String> PATH_PREFIXES;
  private static final Set<String> PATH_CONTAINS_AFTER_JEECG;
  private static final Set<String> PATH_SUFFIXES;
  private static final Set<String> PATH_EXACT_MATCHES;

  static {
    Set<String> p = new LinkedHashSet<>();
    p.add("/sys/api/");
    p.add("/actuator/");
    p.add("/inner/");
    p.add("/sys/inner/");
    p.add("/doc.html");
    p.add("/v3/api-docs/");
    p.add("/v2/api-docs/");
    p.add("/swagger-ui/");
    p.add("/swagger-resources/");
    p.add("/druid/");
    p.add("/hystrix.stream");
    p.add("/admin/");
    // H6: StoredValueCard decommissioned (Visa leftover; no product usage)
    p.add("/dcs/storedValueCard");
    PATH_PREFIXES = Collections.unmodifiableSet(p);

    Set<String> c = new LinkedHashSet<>();
    c.add("/sys/api/");
    c.add("/processengine/api/");
    c.add("/actuator/");
    c.add("/sys/getEncryptedString");
    c.add("/dcs/storedValueCard");
    c.add("/v3/api-docs");
    c.add("/v2/api-docs");
    c.add("/swagger-resources");
    PATH_CONTAINS_AFTER_JEECG = Collections.unmodifiableSet(c);

    Set<String> s = new LinkedHashSet<>();
    s.add("/sys/getEncryptedString");
    s.add("/actuator");
    PATH_SUFFIXES = Collections.unmodifiableSet(s);

    Set<String> e = new LinkedHashSet<>();
    e.add("/v3/api-docs");
    e.add("/v2/api-docs");
    e.add("/swagger-ui.html");
    e.add("/swagger-ui/index.html");
    e.add("/doc.html");
    PATH_EXACT_MATCHES = Collections.unmodifiableSet(e);
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    ServerHttpRequest req = exchange.getRequest();
    String rawPath = req.getURI().getRawPath();
    if (rawPath == null || rawPath.isEmpty()) {
      return chain.filter(exchange);
    }
    String normalized = normalize(rawPath);
    if (isBlocked(normalized)) {
      String remote =
          req.getRemoteAddress() == null ? "" : req.getRemoteAddress().getAddress().getHostAddress();
      log.error(
          "GATEWAY_DENIED_PATH path={} method={} remote={} xff={}",
          normalized,
          req.getMethod(),
          remote,
          req.getHeaders().getFirst("x-forwarded-for"));
      ServerHttpResponse resp = exchange.getResponse();
      resp.setStatusCode(HttpStatus.FORBIDDEN);
      resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);
      DataBuffer buf = resp.bufferFactory().wrap(FORBIDDEN_BODY);
      return resp.writeWith(Mono.just(buf));
    }
    return chain.filter(exchange);
  }

  static boolean isBlocked(String normalized) {
    // M-1: allow only the exact health-probe path /actuator/health (status only); all other /actuator/** stay 403
    if (isHealthProbe(normalized)) {
      return false;
    }
    if (PATH_EXACT_MATCHES.contains(normalized)) {
      return true;
    }
    for (String prefix : PATH_PREFIXES) {
      if (normalized.startsWith(prefix)) {
        return true;
      }
    }
    for (String suffix : PATH_SUFFIXES) {
      if (normalized.endsWith(suffix)) {
        return true;
      }
    }
    boolean startsWithJeecg = normalized.startsWith("/jeecg-");
    if (startsWithJeecg) {
      for (String contained : PATH_CONTAINS_AFTER_JEECG) {
        if (normalized.contains(contained)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * M-1: the gateway allows only the exact {@code /actuator/health} (up/down for K8s/docker probes).
   *
   * <p>Exact match is required: do not allow a prefix, otherwise {@code /actuator/health/../../env},
   * {@code /actuator/env} and similar paths would slip through.
   */
  static boolean isHealthProbe(String normalized) {
    return "/actuator/health".equals(normalized);
  }

  static String normalize(String raw) {
    String n = raw;
    if (n.startsWith("/api/")) {
      n = n.substring(4);
      if (n.isEmpty()) {
        n = "/";
      }
    }
    int semi = n.indexOf(';');
    if (semi > 0) {
      n = n.substring(0, semi);
    }
    return n;
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE - 100;
  }
}
