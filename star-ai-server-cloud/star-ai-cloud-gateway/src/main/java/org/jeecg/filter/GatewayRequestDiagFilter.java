package org.jeecg.filter;

import java.net.URI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Request/response diagnostics for gateway 404 / routing issues.
 *
 * <p>Logs method, path, matched route id/uri, status and latency. Controlled by {@code
 * GATEWAY_DIAG_REQUEST_LOG} (default {@code true}).
 */
@Slf4j
@Component
public class GatewayRequestDiagFilter implements GlobalFilter, Ordered {

  @Value("${gateway.diag.request-log:${GATEWAY_DIAG_REQUEST_LOG:false}}")
  private boolean requestLogEnabled;

  private static final java.security.SecureRandom SR = new java.security.SecureRandom();

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!requestLogEnabled) {
      return chain.filter(exchange);
    }
    long start = System.currentTimeMillis();
    String method = exchange.getRequest().getMethod() == null ? "?" : exchange.getRequest().getMethod().name();
    String path = exchange.getRequest().getURI().getRawPath();
    return chain
        .filter(exchange)
        .doFinally(
            signal -> {
              try {
                HttpStatusCode status = exchange.getResponse().getStatusCode();
                int code = status == null ? -1 : status.value();
                Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                String routeId = route == null ? "-" : route.getId();
                URI routeUri = route == null ? null : route.getUri();
                // GATEWAY_REQUEST_URL_ATTR is a single URI (not Set); Set is ORIGINAL_REQUEST_URL_ATTR.
                Object targetAttr =
                    exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
                String target =
                    targetAttr instanceof URI
                        ? targetAttr.toString()
                        : (targetAttr == null ? "-" : String.valueOf(targetAttr));
                long cost = System.currentTimeMillis() - start;
                if (code >= 500 || cost > 200) {
                  log.info(
                      "[GW-DIAG] {} {} status={} routeId={} routeUri={} target={} costMs={}",
                      method,
                      path,
                      code,
                      routeId,
                      routeUri,
                      target,
                      cost);
                } else if (code >= 400 || route == null) {
                  log.warn(
                      "[GW-DIAG] {} {} status={} routeId={} routeUri={} target={} costMs={}",
                      method,
                      path,
                      code,
                      routeId,
                      routeUri,
                      target,
                      cost);
                } else {
                  log.debug(
                      "[GW-DIAG] {} {} status={} routeId={} routeUri={} costMs={}",
                      method,
                      path,
                      code,
                      routeId,
                      routeUri,
                      cost);
                }
              } catch (Throwable t) {
                log.debug("[GW-DIAG] log failed: {}", t.toString());
              }
            });
  }

  @Override
  public int getOrder() {
    // After route matching attributes are set; still early enough for response status.
    return Ordered.LOWEST_PRECEDENCE - 50;
  }
}
