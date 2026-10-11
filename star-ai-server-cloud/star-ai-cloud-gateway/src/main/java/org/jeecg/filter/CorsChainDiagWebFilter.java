package org.jeecg.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Temporary CORS chain probe: runs just after {@code CorsWebFilter} (same precedence band) to show
 * whether the filter chain continues for Origin requests and what status comes back.
 */
@Slf4j
@Component
public class CorsChainDiagWebFilter implements WebFilter, Ordered {

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    ServerHttpRequest req = exchange.getRequest();
    String origin = req.getHeaders().getOrigin();
    if (origin == null || origin.isEmpty()) {
      return chain.filter(exchange);
    }
    String path = req.getURI().getRawPath();
    log.info("CORS_CHAIN_ENTER method={} path={} origin={}", req.getMethod(), path, origin);
    return chain
        .filter(exchange)
        .doOnSuccess(
            v ->
                log.info(
                    "CORS_CHAIN_EXIT method={} path={} origin={} status={}",
                    req.getMethod(),
                    path,
                    origin,
                    exchange.getResponse().getStatusCode()))
        .doOnError(
            e ->
                log.warn(
                    "CORS_CHAIN_ERROR method={} path={} origin={} err={}",
                    req.getMethod(),
                    path,
                    origin,
                    e.toString()));
  }

  @Override
  public int getOrder() {
    // After CorsWebFilter (HIGHEST) / ApiServiceRouteWebFilter (HIGHEST)
    return Ordered.HIGHEST_PRECEDENCE + 2;
  }
}
