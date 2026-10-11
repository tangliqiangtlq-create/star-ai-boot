package org.jeecg.config.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * B13-1: Site-wide browser security headers — NON-CSP ONLY.
 *
 * <p>CSP is owned EXCLUSIVELY by the gateway single-source-of-truth:
 * {@code org.jeecg.filter.SecurityHeadersGlobalWebFilter}. This MVC Filter runs on
 * system/dcs/xxljob services and writes only non-CSP defensive headers as a last-mile fallback
 * when the response path bypasses the gateway (e.g. internal service-to-service, direct
 * container-port access).
 *
 * <p>Headers are applied AFTER the response is committed so downstream error dispatchers cannot
 * overwrite them. Headers are set with {@code setHeader(...)} rather than {@code addHeader(...)}
 * so we get a single canonical value per name (gateway CORS headers should not conflict).
 */
public class SecurityResponseHeadersFilter extends OncePerRequestFilter implements Ordered {

  public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

  private static final String REFERRER = "strict-origin-when-cross-origin";
  private static final String PERMISSIONS =
      "camera=(), microphone=(), geolocation=(), payment=(), fullscreen=(self), gyroscope=()";

  @Override
  public int getOrder() {
    return ORDER;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      filterChain.doFilter(request, response);
    } finally {
      applyHeaders(request, response);
    }
  }

  private void applyHeaders(HttpServletRequest req, HttpServletResponse res) {
    // -- MIME / framing / clickjacking --
    if (res.getHeader("X-Content-Type-Options") == null) {
      res.setHeader("X-Content-Type-Options", "nosniff");
    }
    if (res.getHeader("X-Frame-Options") == null) {
      res.setHeader("X-Frame-Options", "DENY");
    }

    // -- CSP intentionally removed — gateway single-source-of-truth writes it on every gateway hop. --
    // Keep X-XSS-Protection 0 — modern browsers don't use the auditor and enabling it can introduce
    // side-channel vulnerabilities; CSP (written by gateway SecurityHeadersGlobalWebFilter) is
    // authoritative for script injection defence.
    if (res.getHeader("X-XSS-Protection") == null) {
      res.setHeader("X-XSS-Protection", "0");
    }

    // -- Referer / Permissions / transport --
    if (res.getHeader("Referrer-Policy") == null) {
      res.setHeader("Referrer-Policy", REFERRER);
    }
    if (res.getHeader("Permissions-Policy") == null) {
      res.setHeader("Permissions-Policy", PERMISSIONS);
    }
    // Strict-Transport-Security: only add for HTTPS requests. (Added via env/Gateway for
    // TLS-terminated upstream; here we only populate for direct HTTPS requests to avoid setting
    // HSTS on internal plaintext traffic.)
    if (isHttpsRequest(req) && res.getHeader("Strict-Transport-Security") == null) {
      res.setHeader("Strict-Transport-Security", "max-age=63072000; includeSubDomains");
    }
    // Cross-Origin Opener / Embedded policies to isolate this origin from opened windows.
    if (res.getHeader("Cross-Origin-Opener-Policy") == null) {
      res.setHeader("Cross-Origin-Opener-Policy", "same-origin-allow-popups");
    }
    if (res.getHeader("Cross-Origin-Resource-Policy") == null) {
      res.setHeader("Cross-Origin-Resource-Policy", "same-origin");
    }
  }

  private static boolean isHttpsRequest(HttpServletRequest req) {
    String scheme = req.getScheme();
    if (scheme != null && scheme.equalsIgnoreCase("https")) return true;
    String[] protoHeaders = {
      "X-Forwarded-Proto", "X-Forwarded-Protocol", "X-Url-Scheme",
      "Front-End-Https", "X-Forwarded-Ssl"
    };
    for (String h : protoHeaders) {
      String v = req.getHeader(h);
      if (v == null) continue;
      if ("on".equalsIgnoreCase(v) || "https".equalsIgnoreCase(v)) return true;
    }
    return false;
  }
}
