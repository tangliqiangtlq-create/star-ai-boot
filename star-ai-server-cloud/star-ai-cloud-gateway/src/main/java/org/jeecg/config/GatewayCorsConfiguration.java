package org.jeecg.config;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

/**
 * Production-safe gateway CORS configuration.
 *
 * <p>Replaces the permissive {@code allowedOriginPatterns: "*"} default (see application.yml) with
 * an explicit allow-list when running under any non-dev profile.
 *
 * <p>Configuration sources (priority top-down):
 * <ol>
 *   <li>Environment variable {@code CORS_ALLOWED_ORIGINS} — comma-separated origin list, e.g.
 *       {@code https://app.example.com,https://admin.example.com}</li>
 *   <li>Spring property {@code gateway.cors.allowed-origins}</li>
 * </ol>
 *
 * <p>Fail-close: when {@code spring.profiles.active} contains "prod" / "prd" / "production" and
 * the origin list is blank the application refuses to start. Dev profile also fail-closes when
 * {@code allowCredentials=true} is paired with wildcard {@code *} origins/headers (spec violation
 * that browsers silently reject and has historically caused hard-to-debug regressions).
 *
 * <p>A9 hardening: {@code allowedHeaders} is an explicit allow-list (never {@code *}), every
 * user-supplied origin pattern is anchored with {@code ^} and has literal dots escaped so
 * attackers cannot match unintended subdomains via wildcard regex segments.
 */
@Configuration
@Slf4j
public class GatewayCorsConfiguration {

  private static final List<String> EXPLICIT_ALLOWED_HEADERS =
      Arrays.asList(
          "Authorization",
          "X-Access-Token",
          "Content-Type",
          "X-Requested-With",
          "Accept",
          "Origin",
          "Access-Control-Request-Method",
          "Access-Control-Request-Headers",
          "X-Tenant-Id",
          "X-Internal-Call-Secret",
          "X-Gateway-Sig",
          "X-Gateway-Exp",
          "X-Gateway-Base-Path",
          "X-Signature",
          // Jeecg admin/web sign headers (login + captcha XHR preflight)
          "X-Sign",
          "X-Version",
          "X-Timestamp",
          "X-Nonce",
          "X-App-Id",
          "Lang",
          "Accept-Language",
          "Range",
          "If-Match",
          "If-None-Match");

  private static final Pattern LITERAL_DOT_PATTERN = Pattern.compile("\\.");

  @Value("${gateway.cors.allowed-origins:${CORS_ALLOWED_ORIGINS:}}")
  private String allowedOriginsRaw;

  @Value("${spring.profiles.active:}")
  private String activeProfile;

  @Value("${gateway.cors.required-in-prod:true}")
  private boolean requiredInProd;

  private boolean isProdProfile() {
    if (StringUtils.isBlank(activeProfile)) {
      return false;
    }
    String lower = activeProfile.toLowerCase();
    return lower.contains("prod") || lower.contains("prd") || lower.contains("production");
  }

  private boolean isDevProfile() {
    if (StringUtils.isBlank(activeProfile)) {
      return false;
    }
    return activeProfile.toLowerCase().contains("dev");
  }

  @PostConstruct
  public void startupFailFast() {
    boolean blank = StringUtils.isBlank(allowedOriginsRaw);
    if (isProdProfile() && requiredInProd && blank) {
      String err =
          "[PR-B4-B1-2] production profile ("
              + activeProfile
              + ") but gateway CORS allow-list is empty. "
              + "Set CORS_ALLOWED_ORIGINS=https://app.example.com,https://admin.example.com "
              + "or explicitly set gateway.cors.required-in-prod=false to bypass.";
      log.error(err);
      throw new BeanInitializationException(err);
    }

    if (isDevProfile() && blank) {
      String err =
          "[CORS-DEV-SPEC-VIOLATION][A9] dev profile ("
              + activeProfile
              + ") with allowCredentials=true but CORS origin list is blank (would fall back to "
              + "\"*\" pattern). Fetch/CORS spec forbids wildcard origin paired with "
              + "Access-Control-Allow-Credentials: true — browsers reject the response at runtime "
              + "and the gateway would otherwise silently hide this developer mistake. "
              + "Either explicitly set CORS_ALLOWED_ORIGINS=http://localhost:3000,... or (only if "
              + "you really know what you are doing) set gateway.cors.required-in-prod=false and "
              + "gateway.cors.dev-allow-permissive=true.";
      log.error(err);
      throw new BeanInitializationException(err);
    }

    log.info(
        "GatewayCorsConfiguration startupOk: activeProfile={} originsConfigured={} requiredInProd={}",
        activeProfile,
        !blank,
        requiredInProd);
  }

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  public CorsWebFilter corsWebFilter() {
    CorsConfiguration cfg = buildCorsConfiguration();

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource(new PathPatternParser());
    source.registerCorsConfiguration("/**", cfg);
    // Log processor decisions — empty-body 403 with ACAO means something AFTER Cors short-circuits.
    return new CorsWebFilter(
        source,
        (corsConfiguration, exchange) -> {
          String origin = exchange.getRequest().getHeaders().getOrigin();
          boolean valid =
              new org.springframework.web.cors.reactive.DefaultCorsProcessor()
                  .process(corsConfiguration, exchange);
          // Production flood risk: keep at DEBUG (compliance alert CORS_DIAG INFO spam)
          log.debug(
              "CORS_DIAG process valid={} method={} path={} origin={} status={} acao={} preflight={}",
              valid,
              exchange.getRequest().getMethod(),
              exchange.getRequest().getURI().getRawPath(),
              origin,
              exchange.getResponse().getStatusCode(),
              exchange.getResponse().getHeaders().getAccessControlAllowOrigin(),
              org.springframework.web.cors.reactive.CorsUtils.isPreFlightRequest(
                  exchange.getRequest()));
          return valid;
        });
  }

  /**
   * A6 + SCG 4.x: {@link RoutePredicateHandlerMapping} applies its own CORS check. Without a matching
   * allow-list there, Origin requests can be short-circuited to empty-body 403 (NO_OP_HANDLER) and
   * never reach GlobalFilters/proxy — even when {@link CorsWebFilter} already allowed the Origin.
   * Keep GlobalCorsProperties in sync (also survives {@code RefreshRoutesResultEvent}).
   */
  @Bean
  ApplicationRunner alignRoutePredicateHandlerMappingCors(
      ObjectProvider<GlobalCorsProperties> globalCorsProperties,
      ObjectProvider<RoutePredicateHandlerMapping> routePredicateHandlerMapping) {
    return args -> {
      List<String> origins = parseOrigins();
      if (origins.isEmpty()) {
        log.info("Skip HandlerMapping CORS align: allow-list empty");
        return;
      }
      CorsConfiguration cfg = buildCorsConfiguration();
      Map<String, CorsConfiguration> map = new LinkedHashMap<>();
      map.put("/**", cfg);
      GlobalCorsProperties props = globalCorsProperties.getIfAvailable();
      if (props != null) {
        props.getCorsConfigurations().clear();
        props.getCorsConfigurations().putAll(map);
      }
      RoutePredicateHandlerMapping mapping = routePredicateHandlerMapping.getIfAvailable();
      if (mapping != null) {
        mapping.setCorsConfigurations(map);
      }
      log.info(
          "Aligned Gateway HandlerMapping CORS allow-list size={} origins={}",
          origins.size(),
          origins);
    };
  }

  private CorsConfiguration buildCorsConfiguration() {
    CorsConfiguration cfg = new CorsConfiguration();
    cfg.setAllowCredentials(true);
    cfg.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"));
    cfg.setAllowedHeaders(EXPLICIT_ALLOWED_HEADERS);
    cfg.setExposedHeaders(
        Arrays.asList(
            "Content-Disposition",
            "X-Requested-With",
            "Access-Control-Allow-Origin",
            "Access-Control-Expose-Headers",
            "ETag",
            "X-CSP-NONCE"));
    cfg.setMaxAge(3600L);

    List<String> origins = parseOrigins();
    if (origins.isEmpty()) {
      cfg.addAllowedOriginPattern("*");
    } else {
      List<String> exact = new ArrayList<>(origins.size());
      for (String p : origins) {
        validateExactOrigin(p);
        String anchored = anchorAndEscapeOriginPattern(p);
        log.info("GatewayCors allow origin exact={} anchoredAudit={}", p, anchored);
        exact.add(p);
      }
      cfg.setAllowedOrigins(exact);
    }
    return cfg;
  }

  /**
   * A6 audit form: literal-dot escape + {@code ^}/{@code $} anchors. Used for validation/logging
   * only — never passed to {@code setAllowedOriginPatterns}.
   */
  static String anchorAndEscapeOriginPattern(String pattern) {
    if (pattern == null) {
      return null;
    }
    String s = pattern.trim();
    if (s.isEmpty()) {
      return s;
    }
    String escaped = LITERAL_DOT_PATTERN.matcher(s).replaceAll("\\\\.");
    if (!escaped.startsWith("^")) {
      escaped = "^" + escaped;
    }
    if (!escaped.endsWith("$")) {
      escaped = escaped + "$";
    }
    return escaped;
  }

  /** Reject wildcards / blank schemes so allow-list stays exact absolute origins. */
  static void validateExactOrigin(String origin) {
    if (origin == null || origin.isBlank()) {
      throw new BeanInitializationException("[A6] CORS origin entry is blank");
    }
    String s = origin.trim();
    if (s.contains("*") || s.contains("?") || s.contains("^") || s.contains("$")) {
      throw new BeanInitializationException(
          "[A6] CORS origin must be an exact absolute URL without wildcards/regex: " + s);
    }
    if (!(s.startsWith("https://") || s.startsWith("http://"))) {
      throw new BeanInitializationException(
          "[A6] CORS origin must start with http(s):// : " + s);
    }
  }

  /**
   * B13-1: Spring Cloud Gateway (WebFlux) does NOT see the MVC
   * SecurityResponseHeadersFilter — so we keep a minimal non-CSP fallback here for other response
   * headers.
   *
   * <p>CSP itself is owned exclusively by {@code SecurityHeadersGlobalWebFilter}; this filter must
   * never add {@code Content-Security-Policy}, otherwise report-only / enforce mode becomes
   * unpredictable.
   */
  @Bean
  @Order(Ordered.LOWEST_PRECEDENCE - 100)
  public GlobalFilter gatewaySecurityHeadersGlobalFilter() {
    return (exchange, chain) -> {
      final ServerHttpResponse resp = exchange.getResponse();
      resp.beforeCommit(
          () -> {
            HttpHeaders h = resp.getHeaders();
            // -- framing / MIME / clickjacking --
            if (h.getFirst("X-Content-Type-Options") == null) {
              h.set("X-Content-Type-Options", "nosniff");
            }
            if (h.getFirst("X-Frame-Options") == null) {
              h.set("X-Frame-Options", "DENY");
            }
            if (h.getFirst("X-XSS-Protection") == null) {
              h.set("X-XSS-Protection", "0");
            }
            // -- referrer / permissions / isolation --
            if (h.getFirst("Referrer-Policy") == null) {
              h.set("Referrer-Policy", "strict-origin-when-cross-origin");
            }
            if (h.getFirst("Permissions-Policy") == null) {
              h.set(
                  "Permissions-Policy",
                  "camera=(), microphone=(), geolocation=(), payment=(), fullscreen=(self), gyroscope=()");
            }
            if (h.getFirst("Cross-Origin-Opener-Policy") == null) {
              h.set("Cross-Origin-Opener-Policy", "same-origin-allow-popups");
            }
            // API gateway serves SPA on a different origin — same-origin CORP blocks browser reads.
            if (h.getFirst("Cross-Origin-Resource-Policy") == null
                || "same-origin".equalsIgnoreCase(h.getFirst("Cross-Origin-Resource-Policy"))) {
              h.set("Cross-Origin-Resource-Policy", "cross-origin");
            }
            // Proxied Netty write can drop early CorsWebFilter ACAO — re-assert on commit.
            String origin = exchange.getRequest().getHeaders().getOrigin();
            if (StringUtils.isNotBlank(origin) && parseOrigins().contains(origin.trim())) {
              h.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin.trim());
              h.set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
              if (!h.getOrEmpty(HttpHeaders.VARY).contains("Origin")) {
                h.add(HttpHeaders.VARY, "Origin");
              }
            }
            // -- HSTS: if the request arrived via HTTPS (forwarded proto) set STS. --
            String fwd = exchange.getRequest().getHeaders().getFirst("X-Forwarded-Proto");
            if ((fwd != null && fwd.equalsIgnoreCase("https"))
                || "https".equalsIgnoreCase(exchange.getRequest().getURI().getScheme())) {
              if (h.getFirst("Strict-Transport-Security") == null) {
                h.set("Strict-Transport-Security", "max-age=63072000; includeSubDomains");
              }
            }
            return Mono.empty();
          });
      return chain.filter(exchange);
    };
  }

  private List<String> parseOrigins() {
    if (StringUtils.isBlank(allowedOriginsRaw)) {
      return Collections.emptyList();
    }
    String[] parts = allowedOriginsRaw.split(",");
    List<String> out = new ArrayList<>(parts.length);
    for (String p : parts) {
      String s = p == null ? "" : p.trim();
      if (!s.isEmpty()) {
        out.add(s);
      }
    }
    return out;
  }
}
