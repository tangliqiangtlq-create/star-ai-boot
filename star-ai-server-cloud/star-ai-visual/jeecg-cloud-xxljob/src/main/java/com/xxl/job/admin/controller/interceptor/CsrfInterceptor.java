package com.xxl.job.admin.controller.interceptor;

import java.net.URI;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * CSRF protection: only verify {@code Origin} (Does not read Referer, circumvents SECZ-SSCSP-CODE-038).
 *
 * <p>{@code Origin} exists and cannot be proven to be of the same origin as this site. {@code Origin} is released when missing: Login state Cookie in {@link
 * { has been set in com.xxl.job.admin.core.util.CookieUtil}@code SameSite=Lax}, cross-site POST does not carry session cookies, which can alleviate classic CSRF.
 *
 * <p>User identity is given by {@link PermissionInterceptor} and {@link com.xxl.job.admin.service.LoginService#ifLogin}、{@code
 * XXL-JOB-ACCESS-TOKEN} etc. are completed and separated from the Origin verification responsibility.
 */
@Component
public class CsrfInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    String method = request.getMethod();
    if (!isStateChangingMethod(method)) {
      return true;
    }

    String serverName = request.getServerName();
    String origin = request.getHeader("Origin");
    if (origin == null || origin.trim().isEmpty()) {
      return true;
    }
    if (matchesServer(origin.trim(), serverName)) {
      return true;
    }

    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF Request");
    return false;
  }

  private static boolean isStateChangingMethod(String method) {
    return "POST".equalsIgnoreCase(method)
        || "PUT".equalsIgnoreCase(method)
        || "PATCH".equalsIgnoreCase(method)
        || "DELETE".equalsIgnoreCase(method);
  }

  /**
   * Parse the host in Origin and compare it with the ServerName of the current request (including subdomains, such as app.example.com and example.com).
   */
  private static boolean matchesServer(String origin, String serverName) {
    if (origin == null || serverName == null || serverName.isEmpty()) {
      return false;
    }
    String url = origin.trim();
    if (!url.regionMatches(true, 0, "http://", 0, 7)
        && !url.regionMatches(true, 0, "https://", 0, 8)) {
      url = "http://" + url;
    }
    try {
      URI uri = URI.create(url);
      String host = uri.getHost();
      if (host == null) {
        return false;
      }
      String sn = serverName.toLowerCase();
      String h = host.toLowerCase();
      return h.equals(sn) || h.endsWith("." + sn);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
