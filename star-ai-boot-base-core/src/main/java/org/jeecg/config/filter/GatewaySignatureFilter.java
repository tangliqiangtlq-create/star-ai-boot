package org.jeecg.config.filter;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.jeecg.common.util.SecureHashUtil;
import org.jeecg.common.util.security.InternalCallRequestSupport;
import org.jeecg.common.util.security.IpAccessGuard;
import org.jeecg.common.util.security.TrustedProxyIpResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * PR-CL-B1: Downstream gatekeeper. Rejects any request NOT carrying a valid gateway HMAC signature
 * ({@link org.jeecg.filter.GatewaySignatureWriteFilter}).
 *
 * <p>Whitelist: {@code /actuator/health}, {@code /actuator/info}, and the servlet container root
 * welcome page.
 *
 * <p>Fail-close semantics mirror the writer side; {@code startupFailFast} enforces a shared secret
 * in any prod-like profile so operators cannot misconfigure a cluster into always-deny or
 * always-allow.
 *
 * <p>PR-CL-B1-3 fix: this filter MUST be Spring-managed so its {@code @Value} fields
 * ({@code gateway.sig.secret} etc.) are injected and {@link #startupFailFast()} actually runs.
 * Previously {@code WebMvcConfiguration} registered a raw {@code new} instance, which left
 * {@code secret} blank forever → every request was let through and the prod fail-fast never fired.
 *
 * <p>Explicit bean name: {@code WebMvcConfiguration#gatewaySignatureFilter()} already uses the
 * bean name {@code gatewaySignatureFilter} for its {@link org.springframework.boot.web.servlet.FilterRegistrationBean},
 * and the boot-time validator resolves that name expecting a FilterRegistrationBean. A default
 * component name would collide with it, so the delegate is named explicitly.
 */
@Component("gatewaySignatureFilterDelegate")
public class GatewaySignatureFilter implements Filter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(GatewaySignatureFilter.class);

  public static final String HEADER_SIG = "X-Gateway-Sig";
  public static final String HEADER_EXP = "X-Gateway-Exp";

  private static final byte[] DENY_BODY =
      "{\"success\":false,\"code\":40102,\"message\":\"missing or invalid gateway signature\"}"
          .getBytes(StandardCharsets.UTF_8);

  private static final Set<String> WHITELIST_EXACT;
  private static final Set<String> WHITELIST_PREFIX;

  static {
    Set<String> exact = new LinkedHashSet<>();
    exact.add("/");
    exact.add("");
    exact.add("/actuator/health");
    exact.add("/actuator/info");
    WHITELIST_EXACT = Collections.unmodifiableSet(exact);

    Set<String> prefix = new LinkedHashSet<>();
    prefix.add("/actuator/prometheus");
    prefix.add("/error");
    WHITELIST_PREFIX = Collections.unmodifiableSet(prefix);
  }

  @Value("${gateway.sig.secret:${GATEWAY_SECRET:}}")
  private String secret;

  @Value("${gateway.sig.required-in-prod:true}")
  private boolean requiredInProd;

  @Value("${gateway.sig.clock-skew-seconds:10}")
  private int clockSkewSeconds;

  @Value("${spring.profiles.active:}")
  private String activeProfile;

  @Override
  public int getOrder() {
    return HIGHEST_PRECEDENCE + 3;
  }

  @PostConstruct
  public void startupFailFast() {
    boolean blank = StringUtils.isBlank(secret);
    if (isProd() && requiredInProd && blank) {
      String err =
          "[PR-CL-B1-3] downstream GATEWAY_SECRET is empty on profile="
              + activeProfile
              + ". Traffic bypass impossible. Set gateway.sig.secret >= 32 chars.";
      log.error(err);
      throw new IllegalStateException(err);
    }
    if (!blank && secret.trim().length() < 16) {
      throw new IllegalStateException("[PR-CL-B1-3] downstream GATEWAY_SECRET too short (<16 chars).");
    }
    log.info(
        "GatewaySignatureFilter startupOk: activeProfile={} configured={} skew={}s",
        activeProfile, !blank, clockSkewSeconds);
  }

  @Override
  public void init(FilterConfig filterConfig) {}

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      chain.doFilter(request, response);
      return;
    }
    HttpServletRequest req = (HttpServletRequest) request;
    String uri = req.getRequestURI();
    if (uri == null) {
      uri = "";
    }
    if (WHITELIST_EXACT.contains(uri) || startsWithAny(uri, WHITELIST_PREFIX)) {
      chain.doFilter(request, response);
      return;
    }
    // Legitimate service-to-service Feign calls never carry X-Gateway-Sig (they are authenticated
    // by CIDR + X-Internal-Call-Secret + S2S module matrix instead). InternalTrustedS2sFilter
    // (order HP+1) runs before this filter (HP+3) and marks such requests; honour that mark,
    // otherwise enabling this filter breaks every internal call with 401/40102.
    if (Boolean.TRUE.equals(
        req.getAttribute(InternalCallRequestSupport.ATTR_TRUSTED_INTERNAL_FEIGN))) {
      chain.doFilter(request, response);
      return;
    }
    if (StringUtils.isBlank(secret)) {
      // Secret intentionally unset in local dev — do not block.
      chain.doFilter(request, response);
      return;
    }
    String expRaw = req.getHeader(HEADER_EXP);
    String sig = req.getHeader(HEADER_SIG);
    if (StringUtils.isBlank(expRaw) || StringUtils.isBlank(sig)) {
      reject((HttpServletResponse) response, req, "missing_gateway_sig_headers", uri);
      return;
    }
    long exp;
    try {
      exp = Long.parseLong(expRaw.trim());
    } catch (NumberFormatException nfe) {
      reject((HttpServletResponse) response, req, "bad_gateway_exp", uri);
      return;
    }
    long now = System.currentTimeMillis() / 1000L;
    if (exp < now - clockSkewSeconds || exp > now + 600L) {
      reject((HttpServletResponse) response, req, "gateway_sig_expired_or_skewed", uri);
      return;
    }
    String path = req.getRequestURI();
    String payload = path + ":" + exp;
    String expected =
        SecureHashUtil.hmacSha256Sign(payload, secret.trim(), StandardCharsets.UTF_8.name());
    if (expected == null || !constantTimeEquals(expected, sig.trim())) {
      reject((HttpServletResponse) response, req, "gateway_sig_mismatch", uri);
      return;
    }
    chain.doFilter(request, response);
  }

  private void reject(HttpServletResponse resp, HttpServletRequest req, String reason, String uri)
      throws IOException {
    String remote =
        IpAccessGuard.isPrivateIpOrLoopback(req.getRemoteAddr())
            ? TrustedProxyIpResolver.getStrictClientIp(req)
            : req.getRemoteAddr();
    log.warn(
        "GATEWAY_SIG_FAIL reason={} uri={} method={} remote={}", reason, uri, req.getMethod(), remote);
    resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    resp.setContentType("application/json;charset=UTF-8");
    resp.getOutputStream().write(DENY_BODY);
  }

  @Override
  public void destroy() {}

  private boolean isProd() {
    if (StringUtils.isBlank(activeProfile)) {
      return false;
    }
    String low = activeProfile.toLowerCase();
    return low.contains("prod") || low.contains("prd") || low.contains("production");
  }

  private static boolean startsWithAny(String uri, Set<String> prefixes) {
    for (String p : prefixes) {
      if (uri.startsWith(p)) {
        return true;
      }
    }
    return false;
  }

  private static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null) {
      return false;
    }
    byte[] xa = a.getBytes(StandardCharsets.UTF_8);
    byte[] xb = b.getBytes(StandardCharsets.UTF_8);
    if (xa.length != xb.length) {
      return false;
    }
    int r = 0;
    for (int i = 0; i < xa.length; i++) {
      r |= (xa[i] ^ xb[i]);
    }
    return r == 0;
  }
}
