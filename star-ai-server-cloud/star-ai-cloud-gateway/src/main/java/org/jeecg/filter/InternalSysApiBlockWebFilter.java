package org.jeecg.filter;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
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
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

/**
 * Blocks external callers from reaching internal-only {@code /sys/api/**} endpoints through the
 * gateway.
 *
 * <p>The {@code /sys/api/*} namespace is reserved for Feign microservice calls (see {@code
 * ISysBaseAPI} on the cloud side) and is never meant to be exposed to browsers / mobile clients.
 * Without this filter, {@code spring.cloud.gateway.discovery.locator.enabled=true} routes every
 * discovered service and allows anonymous access to every internal endpoint via
 * {@code /jeecg-system/sys/api/getDictItems?dictCode=...} style paths.
 *
 * <p>Internal Feign traffic does not pass through the gateway at all so this filter only affects
 * external ingress.
 *
 * <p>A9 hardening: 4-step path normalization (double-percent-decode + collapse-slashes +
 * strip-semicolon + dot-segments collapse), mutate-write normalized path back into the exchange
 * request, and expanded deny-pattern set (swagger / api-docs / druid / doc.html / monitor).
 */
@Component
public class InternalSysApiBlockWebFilter implements WebFilter, Ordered {

  private static final byte[] FORBIDDEN_BODY =
      "{\"success\":false,\"message\":\"internal api denied via gateway\"}"
          .getBytes(StandardCharsets.UTF_8);

  private static final Pattern MULTI_SLASH_PATTERN = Pattern.compile("//+");
  private static final Pattern SEMICOLON_SEGMENT_PATTERN = Pattern.compile(";[^/]*");
  private static final Pattern DOT_SEGMENT_PATTERN = Pattern.compile("/\\.(/|$)");
  private static final Pattern DOTDOT_SEGMENT_PATTERN = Pattern.compile("/[^/]+/\\.\\.(/|$)");

  private static final String[] DENY_KEYWORDS =
      new String[] {"swagger", "api-docs", "druid", "doc.html", "monitor"};

  static String normalizePath(String rawPath) {
    if (rawPath == null || rawPath.isEmpty()) {
      return rawPath;
    }
    String s = rawPath;
    s = decodePercentTwice(s);
    s = collapseSlashes(s);
    s = stripSemicolonSegments(s);
    s = collapseDotSegments(s);
    if (!s.startsWith("/")) {
      s = "/" + s;
    }
    return s;
  }

  private static String decodePercentTwice(String s) {
    String once = decodeOnce(s);
    return decodeOnce(once);
  }

  private static String decodeOnce(String s) {
    if (s.indexOf('%') < 0) {
      return s;
    }
    try {
      return URLDecoder.decode(s, StandardCharsets.UTF_8);
    } catch (IllegalArgumentException ex) {
      return s;
    }
  }

  private static String collapseSlashes(String s) {
    return MULTI_SLASH_PATTERN.matcher(s).replaceAll("/");
  }

  private static String stripSemicolonSegments(String s) {
    return SEMICOLON_SEGMENT_PATTERN.matcher(s).replaceAll("");
  }

  private static String collapseDotSegments(String s) {
    String prev;
    String cur = s;
    do {
      prev = cur;
      cur = DOT_SEGMENT_PATTERN.matcher(cur).replaceAll("/");
      cur = DOTDOT_SEGMENT_PATTERN.matcher(cur).replaceAll("/");
      if (cur.equals("/..")) {
        cur = "/";
      }
      if (cur.startsWith("/../")) {
        cur = "/" + cur.substring(4);
      }
    } while (!cur.equals(prev));
    return cur;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String rawPath = exchange.getRequest().getURI().getRawPath();
    if (rawPath == null || rawPath.isEmpty()) {
      return chain.filter(exchange);
    }

    String normalized = normalizePath(rawPath);

    String stripped = normalized;
    if (stripped.startsWith("/api/")) {
      stripped = stripped.substring(4);
      if (stripped.isEmpty()) {
        stripped = "/";
      }
      if (!stripped.startsWith("/")) {
        stripped = "/" + stripped;
      }
    }

    if (isBlocked(stripped)) {
      ServerHttpResponse resp = exchange.getResponse();
      resp.setStatusCode(HttpStatus.FORBIDDEN);
      resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);
      DataBuffer buf = resp.bufferFactory().wrap(FORBIDDEN_BODY);
      return resp.writeWith(Mono.just(buf));
    }

    // Only rewrite when normalization changed the path. Never replace the URI with a
    // path-only URI.create(...) — that drops scheme/host and breaks Gateway routing /
    // CORS handling for Origin requests (empty-body 403 after CorsWebFilter allows).
    if (normalized.equals(rawPath)) {
      return chain.filter(exchange);
    }
    URI original = exchange.getRequest().getURI();
    URI newUri = rebuildUri(original, normalized);
    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate().uri(newUri).build();
    return chain.filter(exchange.mutate().request(mutatedRequest).build());
  }

  /**
   * Rebuild the request URI with the normalized path.
   *
   * <p>{@code normalized} is the result of percent-decoding, so it may contain characters that are
   * illegal in a URI path (space, {@code |}, {@code \t}, ...). Writing it back verbatim with {@code
   * build(true)} (="already encoded") makes Spring's URI validation throw {@code
   * IllegalArgumentException: Invalid character ' ' for PATH}, and the exception escapes the
   * WebFilter → the gateway answers HTTP 500 (pre-auth) for any path containing e.g. an encoded
   * space. Example: {@code /sys/dict/getDictItems/dcs_currency,code,code,%20type='1'}.
   *
   * <p>Fix: re-encode only the path (illegal characters back to {@code %xx}); the query string is
   * left untouched by {@code build(true)}.
   */
  static URI rebuildUri(URI original, String normalized) {
    String encodedPath = UriUtils.encodePath(normalized, StandardCharsets.UTF_8);
    return UriComponentsBuilder.fromUri(original).replacePath(encodedPath).build(true).toUri();
  }

  static boolean isBlocked(String normalized) {
    // M-1: allow only the exact health-probe path /actuator/health (status only); other /actuator/** stay blocked
    if (isHealthProbe(normalized)) {
      return false;
    }
    int idx = normalized.indexOf("/sys/api/");
    if (idx == 0 || (idx > 0 && normalized.startsWith("/jeecg-"))) {
      return true;
    }
    if (normalized.startsWith("/actuator/") || normalized.endsWith("/actuator")
        || (normalized.startsWith("/jeecg-") && normalized.contains("/actuator/"))) {
      return true;
    }
    if (normalized.endsWith("/sys/getEncryptedString")) {
      return true;
    }
    String lower = normalized.toLowerCase();
    for (String kw : DENY_KEYWORDS) {
      if (lower.contains(kw)) {
        return true;
      }
    }
    return false;
  }

  /**
   * M-1: the gateway allows only the exact {@code /actuator/health} (up/down for probes); all other
   * actuator paths stay blocked.
   *
   * <p>Exact match, to prevent normalization bypasses such as {@code /actuator/health/../env}.
   */
  static boolean isHealthProbe(String normalized) {
    return "/actuator/health".equals(normalized);
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 1;
  }
}
