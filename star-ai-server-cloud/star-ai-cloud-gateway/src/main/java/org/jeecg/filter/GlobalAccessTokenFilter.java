package org.jeecg.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global interceptor: inject {@link #X_GATEWAY_BASE_PATH} downstream for @FilePreview and similar file URL assembly.
 *
 * <p>Path rewrite: {@link ApiServiceRouteWebFilter} (runs before route matching).
 */
@Slf4j
@Component
public class GlobalAccessTokenFilter implements GlobalFilter, Ordered {

  public static final String X_ACCESS_TOKEN = "X-Access-Token";
  public static final String X_GATEWAY_BASE_PATH = "X_GATEWAY_BASE_PATH";

  @Value("${spring.profiles.active}")
  private String active;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

    String scheme = exchange.getRequest().getURI().getScheme();

    if (!active.equals("dev")) {
      scheme = "https";
    }

    String host = exchange.getRequest().getURI().getHost();
    int port = exchange.getRequest().getURI().getPort();
    String basePath = scheme + "://" + host;
    if (port != -1) {
      basePath += ":" + port;
    }

    ServerHttpRequest mutableReq =
        exchange
            .getRequest()
            .mutate()
            .headers(
                h -> {
                  h.remove("Authorization-UserName");
                  h.remove("X-Internal-Call-Secret");
                })
            .header("Authorization-UserName", "")
            .header(X_GATEWAY_BASE_PATH, basePath)
            .build();
    ServerWebExchange mutableExchange = exchange.mutate().request(mutableReq).build();

    return chain.filter(mutableExchange);
  }

  @Override
  public int getOrder() {
    return 0;
  }
}
