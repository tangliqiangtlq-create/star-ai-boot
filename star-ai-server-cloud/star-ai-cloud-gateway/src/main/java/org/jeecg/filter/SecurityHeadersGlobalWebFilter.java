package org.jeecg.filter;

import cn.hutool.core.util.StrUtil;
import java.net.URI;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Injects 6 defensive HTTP response headers (B9-1 / §10-Nginx hardening) on every gateway
 * response.
 *
 * <p>Header stack (all opt-out per profile via yml):
 *
 * <ol>
 *   <li>{@code Strict-Transport-Security: max-age=63072000; includeSubDomains; preload}
 *   <li>{@code X-Content-Type-Options: nosniff}
 *   <li>{@code X-Frame-Options: DENY}
 *   <li>{@code Referrer-Policy: strict-origin-when-cross-origin}
 *   <li>{@code Permissions-Policy: camera=(), microphone=(), geolocation=(), usb=(), payment=()}
 *   <li>{@code Content-Security-Policy-Report-Only} + <b>per-request nonce</b> (stashed in request
 *       attribute {@code X-CSP-NONCE} so downstream services can echo it in {@code <script nonce>})
 *       Default: {@code default-src 'self'; script-src 'self' 'strict-dynamic' 'nonce-<b64>'
 *       https:; style-src 'self' 'unsafe-inline'; img-src 'self' data: https: blob:
 *       ; font-src 'self' data: https:; connect-src 'self' https: wss:; frame-ancestors
 *       'none'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-src 'none';
 *       report-uri /_/csp-report; report-to csp-endpoint}
 * </ol>
 *
 * <p>Config keys (all prefixed with {@code gateway.security.headers}):
 *
 * <ul>
 *   <li>{@code .enabled=false} → skip entirely (dev/ut only; prod fail-fast if blank token or blank
 *       master — see B8-1/B8-3; this header one is opt-out only via explicit boolean false)
 *   <li>{@code .hsts.maxAgeSeconds=63072000}, {@code .hsts.includeSubDomains=true}, {@code .hsts.preload=true}
 *   <li>{@code .frameOptions=DENY} (or SAMEORIGIN)
 *   <li>{@code .csp.mode=ReportOnly | Enforce | Off}
 *   <li>{@code .csp.policy} override the full CSP string, use literal placeholder {@code __NONCE__}
 *       for the per-request value
 *   <li>{@code .csp.reportUri} / {@code .csp.reportTo}
 * </ul>
 *
 * <p>Nonce strategy: 18 random bytes → b64url-safe. Different per request even for the same user;
 * not bound to session (script re-use forces browser to re-fetch nonce from server). Requesters of
 * the <b>nonce</b> can get it via {@code X-CSP-NONCE} response header (small info disclosure; the
 * nonce is already visible on every script tag). When CSP reporting is enabled the gateway also
 * emits {@code Reporting-Endpoints} / {@code Report-To} so UAT can run in {@code ReportOnly}
 * mode with a real sink instead of a dead directive.
 */
@Component
public class SecurityHeadersGlobalWebFilter implements WebFilter, Ordered, GlobalFilter {

  private static final byte[] EMPTY = new byte[0];
  private static final String ATTR_NONCE = "X-CSP-NONCE";
  private static final SecureRandom RND = new SecureRandom();

  @Value("${gateway.security.headers.enabled:true}")
  private boolean enabled;

  @Value("${gateway.security.headers.hsts.maxAgeSeconds:63072000}")
  private long hstsMaxAge;

  @Value("${gateway.security.headers.hsts.includeSubDomains:true}")
  private boolean hstsIncludeSub;

  @Value("${gateway.security.headers.hsts.preload:true}")
  private boolean hstsPreload;

  @Value("${gateway.security.headers.frameOptions:DENY}")
  private String frameOptions;

  @Value("${gateway.security.headers.permissionsPolicy:camera=(), microphone=(), geolocation=(), usb=(), payment=()}")
  private String permissionsPolicy;

  @Value("${gateway.security.headers.csp.mode:ReportOnly}")
  private String cspMode;

  @Value("${gateway.security.headers.csp.policy:}")
  private String cspOverride;

  @Value("${gateway.security.headers.csp.reportUri:/_/csp-report}")
  private String reportUri;

  @Value("${gateway.security.headers.csp.reportTo:csp-endpoint}")
  private String reportTo;

  @Value("${gateway.security.headers.csp.enforceTrafficPct:0}")
  private int enforceTrafficPct;

  private int clampEnforceTrafficPct(int v) {
    if (v < 0) return 0;
    if (v > 100) return 100;
    return v;
  }

  private static String newNonce() {
    byte[] b = new byte[18];
    RND.nextBytes(b);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
  }

  private String hsts() {
    StringBuilder s = new StringBuilder("max-age=").append(hstsMaxAge);
    if (hstsIncludeSub) s.append("; includeSubDomains");
    if (hstsPreload) s.append("; preload");
    return s.toString();
  }

  private String csp(String nonce) {
    String pol = cspOverride;
    if (StrUtil.isBlank(pol)) {
      // ---------------------------------------------------------------------
      // Plan C Level-3 layered directives (transition mode until nonce is on
      // 100% traffic — route map §6 P0..P3):
      //   - style-src        = 'self' 'unsafe-inline' (CSP2 fallback, keeps
      //                         92 legacy el.style.* writes working on old
      //                         browsers; on CSP3 browsers this is OVERRIDDEN
      //                         by the two fine-grained directives below)
      //   - style-src-elem  = 'self'                   (NEW: hard-block any
      //                         injected <style> blocks / <link rel=stylesheet
      //                         href=//evil.css  →  main XSS-vector gain)
      //   - style-src-attr  = 'unsafe-inline'          (NEW: keep el.style.*
      //                         / Vue :style=... working during transition)
      //   + worker-src / frame-ancestors / object-src / base-uri / form-action
      //     / frame-src are unchanged from the hardened Step-A skeleton.
      //   + NEW: manifest-src 'self' (PWA manifest / favicon pack)
      // Step-B (applied AFTER 4-day client style-src cleanup lands):
      //   - script-src : remove trailing 'https:'  (strict-dynamic covers it)
      //   - style-src  : remove outer 'unsafe-inline' + set style-src-attr
      //                  = 'self' + style-src-elem stays 'self'
      //   - append    : require-trusted-types-for 'script'; trusted-types
      //                  dompurify;
      // ---------------------------------------------------------------------
      pol =
          "default-src 'self';"
              + " script-src 'self' 'strict-dynamic' 'nonce-__NONCE__' https:;"
              + " style-src 'self' 'unsafe-inline';"
              + " style-src-elem 'self';"
              + " style-src-attr 'unsafe-inline';"
              + " img-src 'self' data: https: blob:;"
              + " font-src 'self' data: https:;"
              + " connect-src 'self' https: wss:;"
              + " worker-src 'self' blob:;"
              + " manifest-src 'self';"
              + " frame-ancestors 'none';"
              + " object-src 'none';"
              + " base-uri 'self';"
              + " form-action 'self';"
              + " frame-src 'none'";
    }
    String resolved = pol.replace("__NONCE__", nonce);
    List<String> directives = new ArrayList<>();
    directives.add(resolved);
    if (StrUtil.isNotBlank(reportUri)) {
      directives.add("report-uri " + reportUri);
    }
    if (StrUtil.isNotBlank(reportTo)) {
      directives.add("report-to " + reportTo);
    }
    return String.join(";", directives);
  }

  private String resolveReportEndpointUrl(ServerWebExchange exchange) {
    if (StrUtil.isBlank(reportUri)) {
      return "";
    }
    String configured = reportUri.trim();
    if (configured.startsWith("https://") || configured.startsWith("http://")) {
      return configured;
    }
    String normalized = configured.startsWith("/") ? configured : "/" + configured;
    URI requestUri = exchange.getRequest().getURI();
    return requestUri.resolve(normalized).toString();
  }

  private void writeCspReportingHeaders(
      org.springframework.http.HttpHeaders headers, ServerWebExchange exchange) {
    if (StrUtil.isBlank(reportUri)) {
      return;
    }
    if (StrUtil.isNotBlank(reportTo)) {
      String endpointUrl = resolveReportEndpointUrl(exchange);
      headers.set(
          "Reporting-Endpoints",
          reportTo + "=\"" + endpointUrl + "\"");
      headers.set(
          "Report-To",
          "{\"group\":\""
              + reportTo
              + "\",\"max_age\":10886400,\"endpoints\":[{\"url\":\""
              + endpointUrl
              + "\"}]}");
    }
  }

  private void writeHeaders(ServerWebExchange exchange, String nonce) {
    // Response body may already be flushed; Spring then wraps headers as ReadOnlyHttpHeaders.
    // Mutating them throws UnsupportedOperationException and Netty closes the connection.
    if (exchange.getResponse().isCommitted()) {
      return;
    }
    var h = exchange.getResponse().getHeaders();
    try {
      h.set("Strict-Transport-Security", hsts());
      h.set("X-Content-Type-Options", "nosniff");
      String fo = StrUtil.blankToDefault(frameOptions, "DENY").toUpperCase();
      h.set("X-Frame-Options", "SAMEORIGIN".equals(fo) ? "SAMEORIGIN" : "DENY");
      h.set("Referrer-Policy", "strict-origin-when-cross-origin");
      h.set("Permissions-Policy", permissionsPolicy);
      // §3.5 / 合规告警5：CORP 必须存在（API 跨域读由 Cors filter 可升为 cross-origin）
      if (h.getFirst("Cross-Origin-Resource-Policy") == null) {
        h.set("Cross-Origin-Resource-Policy", "same-origin");
      }
      applyCspToHeaders(h, exchange, nonce);
      // Sa-Token cookie pinning: if upstream issued a Set-Cookie satoken=*, force SameSite=Strict +
      // HttpOnly + Secure flag (otherwise Vue via localStorage is vulnerable to XSS token theft).
      var cookies = exchange.getResponse().getCookies();
      if (cookies != null && !cookies.isEmpty()) {
        for (var entry : cookies.entrySet()) {
          for (ResponseCookie c : entry.getValue()) {
            String name = c.getName();
            if ("satoken".equalsIgnoreCase(name) || name.toLowerCase().contains("token")) {
              ResponseCookie fixed =
                  ResponseCookie.from(name, c.getValue())
                      .domain(c.getDomain())
                      .path(StrUtil.blankToDefault(c.getPath(), "/"))
                      .maxAge(c.getMaxAge())
                      .httpOnly(true)
                      .secure(
                          !"false"
                              .equalsIgnoreCase(
                                  System.getProperty("jeecg.cookie.secure.override", "")))
                      .sameSite("Strict")
                      .build();
              h.set("Set-Cookie", fixed.toString());
            }
          }
        }
      }
    } catch (UnsupportedOperationException ignored) {
      // Headers sealed (ReadOnlyHttpHeaders) — CSP is also applied via HttpHeadersFilter last-mile.
    }
  }

  /**
   * Apply CSP / nonce onto a <b>writable</b> header bag. Used by beforeCommit (when headers are
   * still mutable) and by {@link SecurityHeadersEnforceHttpHeadersFilter} (proxy response path,
   * which is the path that actually succeeds for API JSON in local/UAT).
   */
  void applyCspToHeaders(
      org.springframework.http.HttpHeaders h, ServerWebExchange exchange, String nonce) {
    if (h == null) {
      return;
    }
    String mode = StrUtil.blankToDefault(cspMode, "ReportOnly").toLowerCase();
    if (!"off".equals(mode)) {
      int pct = clampEnforceTrafficPct(enforceTrafficPct);
      boolean doEnforce = false;
      if ("enforce".equals(mode)) {
        // Align with ops docs: mode=Enforce + pct=0 means full Enforce (not a silent no-op).
        // Grey-release uses pct in (0, 100).
        if (pct <= 0 || pct >= 100) {
          doEnforce = true;
        } else {
          doEnforce = RND.nextInt(100) < pct;
        }
      }

      boolean sendReportOnly = (!"enforce".equals(mode)) || (pct > 0 && pct < 100) || !doEnforce;
      if (sendReportOnly) {
        String roHeader = "Content-Security-Policy-Report-Only";
        List<String> existingRo = h.get(roHeader);
        if (existingRo == null || existingRo.isEmpty()) {
          h.set(roHeader, csp(nonce));
        }
      }
      if (doEnforce) {
        String efHeader = "Content-Security-Policy";
        List<String> existingEf = h.get(efHeader);
        if (existingEf == null || existingEf.isEmpty()) {
          h.set(efHeader, csp(nonce));
        }
      }
      writeCspReportingHeaders(h, exchange);
    }
    if (StrUtil.isNotBlank(nonce) && h.getFirst("X-CSP-Nonce") == null) {
      h.set("X-CSP-NONCE", nonce);
    }
  }

  String resolveOrCreateNonce(ServerWebExchange exchange) {
    Object existing = exchange.getAttribute(ATTR_NONCE);
    if (existing instanceof String && StrUtil.isNotBlank((String) existing)) {
      return (String) existing;
    }
    String nonce = newNonce();
    exchange.getAttributes().put(ATTR_NONCE, nonce);
    return nonce;
  }

  boolean isHeadersEnabled() {
    return enabled;
  }

  private ServerWebExchange ensureNonce(ServerWebExchange exchange) {
    Object existing = exchange.getAttribute(ATTR_NONCE);
    if (existing instanceof String) {
      ServerHttpRequest request =
          exchange
              .getRequest()
              .mutate()
              .headers(
                  h -> {
                    h.remove("X-CSP-NONCE");
                    h.remove("X-Internal-Call-Secret");
                  })
              .header("X-CSP-NONCE", (String) existing)
              .build();
      return exchange.mutate().request(request).build();
    }
    String nonce = newNonce();
    exchange.getAttributes().put(ATTR_NONCE, nonce);
    // also expose via mutated request header so downstream microservices (not gateway) can read it
    ServerHttpRequest mutated =
        exchange
            .getRequest()
            .mutate()
            .headers(
                h -> {
                  h.remove("X-CSP-NONCE");
                  h.remove("X-Internal-Call-Secret");
                })
            .header("X-CSP-NONCE", nonce)
            .build();
    return exchange.mutate().request(mutated).build();
  }

  // ===== WebFilter (used when the gateway classpath also loads WebFlux WebFilterChain directly) =====

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    if (!enabled) return chain.filter(exchange);
    ServerWebExchange mutatedExchange = ensureNonce(exchange);
    String nonce = (String) mutatedExchange.getAttribute(ATTR_NONCE);
    // Must register beforeCommit BEFORE chain runs — .then() after filter is too late
    // (response already committed → ReadOnlyHttpHeaders → connection reset on login/RSA).
    mutatedExchange
        .getResponse()
        .beforeCommit(
            () -> {
              writeHeaders(mutatedExchange, nonce);
              return Mono.empty();
            });
    return chain.filter(mutatedExchange);
  }

  // ===== GlobalFilter (Cloud Gateway official SPI) =====

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!enabled) return chain.filter(exchange);
    ServerWebExchange mutatedExchange = ensureNonce(exchange);
    String nonce = (String) mutatedExchange.getAttribute(ATTR_NONCE);
    mutatedExchange
        .getResponse()
        .beforeCommit(
            () -> {
              writeHeaders(mutatedExchange, nonce);
              return Mono.empty();
            });
    return chain.filter(mutatedExchange);
  }

  @Override
  public int getOrder() {
    // Write late (after business / auth filters) so downstream Set-Cookie for satoken is visible.
    return Ordered.LOWEST_PRECEDENCE - 100;
  }
}
