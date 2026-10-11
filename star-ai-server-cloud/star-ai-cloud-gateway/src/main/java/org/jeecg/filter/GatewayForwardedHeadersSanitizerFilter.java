package org.jeecg.filter;

import java.util.Arrays;
import java.util.List;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * A9: Sanitize (strip) 8 Forwarded-family request headers before any downstream logic runs.
 *
 * <p>Upstream reverse proxies (nginx / Cloudflare) may legitimately set these headers; however
 * once the request reaches the Spring Cloud Gateway we MUST NOT trust user-supplied Forwarded-*
 * values because they can poison URI-building, CORS origin evaluation, redirect construction,
 * access-log host spoofing and similar downstream behaviours.
 *
 * <p>The 8 stripped headers are the standardised + de-facto set recognised by Spring's
 * {@code ForwardedHeaderTransformer} / Tomcat {@code RemoteIpValve}:
 * <ol>
 *   <li>{@code Forwarded}</li>
 *   <li>{@code X-Forwarded-For}</li>
 *   <li>{@code X-Forwarded-Host}</li>
 *   <li>{@code X-Forwarded-Proto}</li>
 *   <li>{@code X-Forwarded-Port}</li>
 *   <li>{@code X-Forwarded-Prefix}</li>
 *   <li>{@code X-Forwarded-Ssl}</li>
 *   <li>{@code X-Forwarded-Server}</li>
 * </ol>
 *
 * <p>Order is {@code -1500} so the strip happens before {@code AdaptCachedBodyGlobalFilter}
 * (-1000), route predicate matching, {@link InternalSysApiBlockWebFilter} and CORS evaluation.
 * Implements both {@link GlobalFilter} (official Cloud Gateway SPI) and {@link WebFilter} so the
 * same protection applies even if the gateway classpath also boots a plain WebFlux chain directly.
 */
@Component
public class GatewayForwardedHeadersSanitizerFilter implements GlobalFilter, WebFilter, Ordered {

  public static final int ORDER = -1500;

  private static final List<String> FORWARDED_HEADERS =
      Arrays.asList(
          "Forwarded",
          "X-Forwarded-For",
          "X-Forwarded-Host",
          "X-Forwarded-Proto",
          "X-Forwarded-Port",
          "X-Forwarded-Prefix",
          "X-Forwarded-Ssl",
          "X-Forwarded-Server");

  private static ServerHttpRequest stripForwardedHeaders(ServerHttpRequest request) {
    return request.mutate().headers(h -> FORWARDED_HEADERS.forEach(h::remove)).build();
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    ServerHttpRequest sanitized = stripForwardedHeaders(exchange.getRequest());
    ServerWebExchange mutated = exchange.mutate().request(sanitized).build();
    return chain.filter(mutated);
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest sanitized = stripForwardedHeaders(exchange.getRequest());
    ServerWebExchange mutated = exchange.mutate().request(sanitized).build();
    return chain.filter(mutated);
  }

  @Override
  public int getOrder() {
    return ORDER;
  }
}
