package org.jeecg.config.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

public class PrivacyCacheControlFilter extends OncePerRequestFilter implements Ordered {

  public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 50;

  private static final Set<String> PUBLIC_SCENES =
      Set.of("BANNER", "CURRENCY", "BLOCKCHAIN", "LOGO", "CHAIN_EXPLORER", "PUBLIC");

  private static final Set<String> PUBLIC_SUFFIXES =
      Set.of(".js", ".css", ".svg", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".woff", ".woff2", ".ttf", ".eot");

  @Override
  public int getOrder() {
    return ORDER;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    filterChain.doFilter(request, response);
    try {
      String uri = request.getRequestURI();
      String scene = request.getParameter("scene");
      boolean isPublic = false;
      if (scene != null && !scene.isEmpty()) {
        String upper = scene.toUpperCase(Locale.ROOT);
        for (String p : PUBLIC_SCENES) {
          if (upper.contains(p)) {
            isPublic = true;
            break;
          }
        }
      }
      if (!isPublic && uri != null) {
        String lowerUri = uri.toLowerCase(Locale.ROOT);
        if (lowerUri.contains("/public/")
            || lowerUri.contains("/assets/")
            || lowerUri.contains("/static/")) {
          for (String suffix : PUBLIC_SUFFIXES) {
            if (lowerUri.endsWith(suffix)) {
              isPublic = true;
              break;
            }
          }
        }
      }
      if (!isPublic) {
        String existing = response.getHeader(HttpHeaders.CACHE_CONTROL);
        if (existing == null || !existing.toLowerCase(Locale.ROOT).contains("no-store")) {
          response.setHeader(
              HttpHeaders.CACHE_CONTROL, "private, no-store, no-cache, must-revalidate, max-age=0");
          response.setHeader(HttpHeaders.PRAGMA, "no-cache");
          response.setDateHeader(HttpHeaders.EXPIRES, 0L);
        }
      } else {
        String existing = response.getHeader(HttpHeaders.CACHE_CONTROL);
        if (existing == null || existing.trim().isEmpty()) {
          response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable");
        }
      }
    } catch (Exception ignore) {
    }
  }
}
