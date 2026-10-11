package org.jeecg.config.filter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.CommonAPI;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.common.util.oConvertUtils;

/**
 * WebSocket: frontend passes token in subprotocol when connecting; HTTP protocol used for backend
 * token validation @Author taoYan @Date 2022/4/21 17:01
 *
 * <p>B8-2: Harden — this Filter runs on the original HTTP 101 Upgrade request BEFORE the websocket
 * handshake; we extract the Sa-Token from any known carrier, call {@link
 * cn.dev33.satoken.stp.StpUtil#checkLogin()} so the thread carries the session, and stash the
 * loginId on the request attributes for downstream {@code @ServerEndpoint#onOpen} re-validation
 * against the {@code /websocket/{userId}} path parameter (IDOR hardening).
 */
@Slf4j
public class WebsocketFilter implements Filter {

  private static final String TOKEN_KEY = "Sec-WebSocket-Protocol";
  /** Legacy Jeecg frontends / patches still use the name {@code satoken}. */
  private static final String LEGACY_SA_TOKEN_NAME = "satoken";

  private static CommonAPI commonApi;

  private static RedisUtil redisUtil;

  public static final String ATTR_VERIFIED_LOGIN_ID = "__jeecg_ws_verified_loginId";
  public static final String ATTR_VERIFIED_TENANT_ID = "__jeecg_ws_verified_tenantId";

  /**
   * Same-thread bridge to {@code WebSocket.AuthConfigurator}: HandshakeRequest may not expose
   * servlet attributes, while Cookie/{@code token-name} carriers are already verified here.
   */
  private static final ThreadLocal<String> HANDSHAKE_VERIFIED_LOGIN_ID = new ThreadLocal<>();

  /** @return loginId verified by this filter for the current upgrade thread, or null */
  public static String peekVerifiedLoginId() {
    return HANDSHAKE_VERIFIED_LOGIN_ID.get();
  }

  @Override
  public void doFilter(
      ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
      throws IOException, ServletException {
    if (commonApi == null) {
      commonApi = SpringContextUtils.getBean(CommonAPI.class);
    }
    if (redisUtil == null) {
      redisUtil = SpringContextUtils.getBean(RedisUtil.class);
    }
    HttpServletRequest request = (HttpServletRequest) servletRequest;
    HttpServletResponse response = (HttpServletResponse) servletResponse;

    // Try to extract Sa-Token from: (1) Sec-WebSocket-Protocol, (2) Authorization Bearer,
    // (3) configured sa-token.token-name header/query/cookie (often "token"), (4) legacy "satoken".
    String token = extractSaToken(request);

    log.debug(
        "Websocket Token security verification, Path = {}, token present:{}",
        request.getRequestURI(),
        token != null);

    if (token != null && !token.isEmpty()) {
      try {
        cn.dev33.satoken.stp.StpUtil.setTokenValue(token);
        // B8-2: mandatory login check.  Anonymous websocket subscriptions are forbidden.
        Object loginId = cn.dev33.satoken.stp.StpUtil.getLoginIdByToken(token);
        if (loginId != null) {
          String loginIdStr = String.valueOf(loginId);
          request.setAttribute(ATTR_VERIFIED_LOGIN_ID, loginIdStr);
          HANDSHAKE_VERIFIED_LOGIN_ID.set(loginIdStr);
          Object tenantId = org.jeecg.common.config.TenantContext.getTenant();
          if (tenantId != null) {
            request.setAttribute(ATTR_VERIFIED_TENANT_ID, String.valueOf(tenantId));
          }
        } else {
          log.warn("Websocket rejected: invalid Sa-Token Path={}", request.getRequestURI());
          writeUnauthorized(response, "WebSocket requires valid Sa-Token");
          return;
        }
      } catch (Exception exception) {
        log.warn(
            "Websocket Token security verification failed, IP:{}, Path = {}, exception: {}",
            oConvertUtils.getIpAddrByRequest(request),
            request.getRequestURI(),
            exception.toString());
        writeUnauthorized(response, "WebSocket Sa-Token invalid");
        return;
      }
    } else {
      log.warn(
          "Websocket rejected: missing Sa-Token Path={} IP={}",
          request.getRequestURI(),
          oConvertUtils.getIpAddrByRequest(request));
      writeUnauthorized(response, "WebSocket requires Sa-Token");
      return;
    }

    response.setHeader(TOKEN_KEY, request.getHeader(TOKEN_KEY));
    try {
      filterChain.doFilter(servletRequest, servletResponse);
    } finally {
      HANDSHAKE_VERIFIED_LOGIN_ID.remove();
    }
  }

  /**
   * Avoid {@code sendError} → Tomcat error dispatch to {@code /error} (often POST-only) which
   * floods logs with {@code HttpRequestMethodNotSupportedException} on WS handshake GET.
   */
  private static void writeUnauthorized(HttpServletResponse response, String message)
      throws IOException {
    if (response.isCommitted()) {
      return;
    }
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"success\":false,\"code\":401,\"message\":\"" + message + "\"}");
  }

  static String extractSaToken(HttpServletRequest request) {
    // 1. Sec-WebSocket-Protocol (Jeecg convention; e.g. "base64token, base64protocol...")
    String subProtocol = request.getHeader(TOKEN_KEY);
    if (oConvertUtils.isNotEmpty(subProtocol)) {
      for (String part : subProtocol.split(",")) {
        String t = part.trim();
        if (!t.isEmpty()) return t;
      }
    }
    // 2. Authorization Bearer
    String auth = request.getHeader("Authorization");
    if (oConvertUtils.isNotEmpty(auth) && auth.regionMatches(true, 0, "Bearer ", 0, 7)) {
      return auth.substring(7).trim();
    }

    String configuredName = resolveConfiguredTokenName();
    // 3. Configured token-name header / query / cookie (nacos: sa-token.token-name=token)
    String fromConfigured = firstNonEmpty(
        request.getHeader(configuredName),
        request.getParameter(configuredName),
        cookieValue(request, configuredName));
    if (fromConfigured != null) {
      return fromConfigured;
    }
    // 4. Legacy satoken carriers (frontend patch / older clients)
    if (!LEGACY_SA_TOKEN_NAME.equalsIgnoreCase(configuredName)) {
      String fromLegacy = firstNonEmpty(
          request.getHeader(LEGACY_SA_TOKEN_NAME),
          request.getParameter(LEGACY_SA_TOKEN_NAME),
          cookieValue(request, LEGACY_SA_TOKEN_NAME));
      if (fromLegacy != null) {
        return fromLegacy;
      }
    }
    return null;
  }

  private static String resolveConfiguredTokenName() {
    try {
      String name = cn.dev33.satoken.stp.StpUtil.getTokenName();
      if (oConvertUtils.isNotEmpty(name)) {
        return name.trim();
      }
    } catch (Exception ignore) {
    }
    return LEGACY_SA_TOKEN_NAME;
  }

  private static String cookieValue(HttpServletRequest request, String name) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null || oConvertUtils.isEmpty(name)) {
      return null;
    }
    for (Cookie c : cookies) {
      if (name.equalsIgnoreCase(c.getName()) && oConvertUtils.isNotEmpty(c.getValue())) {
        return c.getValue().trim();
      }
    }
    return null;
  }

  private static String firstNonEmpty(String... values) {
    if (values == null) {
      return null;
    }
    for (String v : values) {
      if (oConvertUtils.isNotEmpty(v)) {
        return v.trim();
      }
    }
    return null;
  }
}
