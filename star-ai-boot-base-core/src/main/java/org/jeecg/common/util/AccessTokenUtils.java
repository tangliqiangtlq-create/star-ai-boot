package org.jeecg.common.util;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.jeecg.common.constant.CommonConstant;

/**
 * Parse access token from HTTP request.
 *
 * <p>A2-1: tokens are accepted from Header / Bearer / Cookie only — never from query string
 * ({@code ?token=} / {@code ?X-Access-Token=}) to prevent leakage via Referer/logs and download
 * bypass patterns.
 */
public final class AccessTokenUtils {

  /** Cookie names accepted when resolving session; includes prod {@code unibit-token}. */
  private static final String[] COOKIE_TOKEN_NAMES = {
    "token", "unibit-token", "auth_token", CommonConstant.X_ACCESS_TOKEN
  };

  private AccessTokenUtils() {}

  public static String resolveFromRequest(HttpServletRequest request) {
    if (request == null) {
      return null;
    }

    String token = request.getHeader(CommonConstant.X_ACCESS_TOKEN);
    if (StrUtil.isNotBlank(token)) {
      return token.trim();
    }

    String authorization = request.getHeader("Authorization");
    if (StrUtil.isNotBlank(authorization) && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return authorization.substring(7).trim();
    }

    token = resolveFromCookies(request);
    if (StrUtil.isNotBlank(token)) {
      return token;
    }

    try {
      return StpUtil.getTokenValue();
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String resolveFromCookies(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null || cookies.length == 0) {
      return null;
    }

    String saTokenName = null;
    try {
      saTokenName = StpUtil.getTokenName();
    } catch (Exception ignored) {
    }

    for (Cookie cookie : cookies) {
      if (cookie == null || StrUtil.isBlank(cookie.getValue())) {
        continue;
      }
      String name = cookie.getName();
      if (matchesTokenCookieName(name, saTokenName)) {
        return cookie.getValue().trim();
      }
    }
    return null;
  }

  private static boolean matchesTokenCookieName(String cookieName, String saTokenName) {
    if (StrUtil.isBlank(cookieName)) {
      return false;
    }
    if (StrUtil.isNotBlank(saTokenName) && cookieName.equalsIgnoreCase(saTokenName)) {
      return true;
    }
    for (String name : COOKIE_TOKEN_NAMES) {
      if (cookieName.equalsIgnoreCase(name)) {
        return true;
      }
    }
    return false;
  }
}
