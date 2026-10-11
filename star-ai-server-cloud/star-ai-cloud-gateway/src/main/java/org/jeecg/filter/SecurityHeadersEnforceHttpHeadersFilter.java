package org.jeecg.filter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * V-HIGH-09 Hardening: Last-mile HTTP security headers (incl. CSP).
 *
 * <p>{@link SecurityHeadersGlobalWebFilter} still owns CSP policy/mode and nonce generation via
 * {@code beforeCommit}. On proxied API responses, response headers are often already sealed there
 * ({@code ReadOnlyHttpHeaders}), so CSP was silently dropped while this filter continued to write
 * HSTS/X-Frame successfully. This filter therefore also applies CSP onto the <b>writable</b>
 * header bag on the RESPONSE path.
 *
 * <p>Order = {@code LOWEST_PRECEDENCE} so downstream custom headers are overridden by the secure
 * defaults.
 */
@Component
public class SecurityHeadersEnforceHttpHeadersFilter
    implements HttpHeadersFilter, Ordered {

  @Autowired private SecurityHeadersGlobalWebFilter securityHeaders;

  @Override
  public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
    input.set(
        "Strict-Transport-Security",
        "max-age=63072000; includeSubDomains; preload");
    input.set("X-Content-Type-Options", "nosniff");
    input.set("X-Frame-Options", "DENY");
    input.set("Referrer-Policy", "strict-origin-when-cross-origin");
    input.set(
        "Permissions-Policy",
        "camera=(), microphone=(), geolocation=(), usb=(), payment=()");

    if (securityHeaders != null && securityHeaders.isHeadersEnabled()) {
      String nonce = securityHeaders.resolveOrCreateNonce(exchange);
      securityHeaders.applyCspToHeaders(input, exchange, nonce);
    }
    return input;
  }

  @Override
  public boolean supports(Type type) {
    return Type.RESPONSE.equals(type);
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
