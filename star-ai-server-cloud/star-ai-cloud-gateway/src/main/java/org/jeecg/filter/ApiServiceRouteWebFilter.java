package org.jeecg.filter;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * Frontend path normalization before Spring Cloud Gateway route matching.
 *
 * <p>Two modes (aligned with {@code spring.cloud.gateway.discovery.locator.enabled}):
 *
 * <ul>
 *   <li><b>Explicit routes</b> (locator=false, prod default): strip {@code /api} only. DB routes use
 *       {@code Path=/sys/**} → {@code lb://jeecg-system}, so path must stay {@code /sys/...}.
 *   <li><b>Discovery locator</b> (locator=true): strip {@code /api} and prefix service id, e.g.
 *       {@code /api/sys/...} → {@code /jeecg-system/sys/...}.
 * </ul>
 *
 * <p>Must run before route matching (WebFilter); GlobalFilter timing is too late to match routes.
 */
@Slf4j
@Component
public class ApiServiceRouteWebFilter implements WebFilter, Ordered {

  /** Business path prefix -> Nacos service name (locator mode only) */
  private static final Map<String, String> MODULE_SERVICE =
      new LinkedHashMap<>() {
        {
          put("/sys/", "jeecg-system");
          put("/dcs/", "jeecg-dcs");
          put("/processengine/", "jeecg-processengine");
        }
      };

  @Value("${spring.cloud.gateway.discovery.locator.enabled:false}")
  private boolean discoveryLocatorEnabled;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String rawPath = exchange.getRequest().getURI().getRawPath();
    if (rawPath == null || rawPath.isEmpty()) {
      return chain.filter(exchange);
    }

    String rewritten = rewritePath(rawPath, discoveryLocatorEnabled);
    if (rewritten.equals(rawPath)) {
      if (shouldDiagPath(rawPath)) {
        log.info(
            "[GW-DIAG] path-keep method={} path={} locator={}",
            exchange.getRequest().getMethod(),
            rawPath,
            discoveryLocatorEnabled);
      }
      return chain.filter(exchange);
    }

    log.info(
        "[GW-DIAG] path-rewrite method={} from={} to={} locator={}",
        exchange.getRequest().getMethod(),
        rawPath,
        rewritten,
        discoveryLocatorEnabled);

    URI newUri =
        UriComponentsBuilder.fromUri(exchange.getRequest().getURI())
            .replacePath(rewritten)
            .build(true)
            .toUri();
    ServerHttpRequest newRequest = exchange.getRequest().mutate().uri(newUri).build();
    return chain.filter(exchange.mutate().request(newRequest).build());
  }

  private static boolean shouldDiagPath(String path) {
    return path.startsWith("/api/")
        || path.startsWith("/sys/")
        || path.startsWith("/dcs/")
        || path.startsWith("/processengine/")
        || path.startsWith("/jeecg-")
        || path.startsWith("/websocket");
  }

  /**
   * @param locatorEnabled when true, prefix module paths with service id for discovery locator;
   *     when false, only strip {@code /api} so explicit {@code Path=/sys/**} routes match.
   */
  static String rewritePath(String rawPath, boolean locatorEnabled) {
    if (rawPath.startsWith("/jeecg-") || rawPath.startsWith("/actuator")) {
      return rawPath;
    }

    String modulePath = rawPath;
    if (modulePath.startsWith("/api/")) {
      modulePath = modulePath.substring("/api".length());
      if (modulePath.isEmpty()) {
        modulePath = "/";
      }
    }

    if (!locatorEnabled) {
      // Explicit Redis/DB routes: Path=/sys/**, /dcs/**, /websocket/** — do not prepend service id.
      return modulePath;
    }

    for (Map.Entry<String, String> entry : MODULE_SERVICE.entrySet()) {
      if (modulePath.startsWith(entry.getKey())) {
        return "/" + entry.getValue() + modulePath;
      }
    }
    // /api/websocket/** etc.: strip /api only
    return modulePath;
  }

  /** @deprecated use {@link #rewritePath(String, boolean)}; kept for older call sites. */
  static String rewriteToDiscoveryPath(String rawPath) {
    return rewritePath(rawPath, true);
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
