package org.jeecg.common.util.security;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.jeecg.common.util.RequestUtils;
import org.jeecg.common.util.SpringContextUtils;
import org.jeecg.config.filter.InternalSecretHeaderStripFilter;
import org.jeecg.config.oss.OssTenantOwnerUtil;
import org.springframework.core.env.Environment;

/**
 * Detects trusted service-to-service calls into processengine (A5-1 Feign path).
 *
 * <p>True when either the session principal is {@code internal_call}, or the request carries a
 * valid {@code X-Internal-Call-Secret} (always set by {@code CustomFeignConfig}). Direct browser
 * callers do not have the secret.
 */
public final class InternalCallRequestSupport {

  private InternalCallRequestSupport() {}

  /**
   * Set on the {@link jakarta.servlet.http.HttpServletRequest} when a S2S call passes the
   * (CIDR guard + shared secret) check AND matches the module-matrix URI/prefix. Downstream
   * callers (StpInterfaceImpl, InternalCallRequestSupport.isTrustedServiceCall) query this ATTR
   * to detect service context without relying on Sa-Token cookie propagation.
   *
   * <p>InternalTrustedS2sFilter writes this attribute; InternalFeignProcessPermFilter (DELETED in
   * Phase 6) was the legacy writer.
   */
  public static final String ATTR_TRUSTED_INTERNAL_FEIGN = "__jeecg_trusted_internal_feign";

  /**
   * PR-CL-32-CG-2.1: Unified CIDR + trusted-proxy guard for every secretMatches() call-site.
   * Returns true ONLY when the inbound remote address (NOT X-Forwarded-For — that is forgeable)
   * belongs to a private/loopback/link-local CIDR block per {@link IpAccessGuard}.
   *
   * <p>Used by: InternalTrustedS2sFilter, InternalCallRequestSupport, StpInterfaceImpl fallback
   * path — verified at boot by {@code validateCidrGuardOnAllSecretMatches} in WebMvcConfiguration.
   *
   * @param req non-null inbound HttpServletRequest
   * @return true if remoteAddr is on a trusted internal CIDR, false otherwise (public internet)
   */
  public static boolean isTrustedInboundRemoteAddr(HttpServletRequest req) {
    if (req == null) {
      return false;
    }
    return IpAccessGuard.isPrivateIpOrLoopback(req.getRemoteAddr());
  }

  public static boolean isTrustedServiceCall() {
    if (OssTenantOwnerUtil.isInternalCallContext()) {
      return true;
    }
    HttpServletRequest req = RequestUtils.getCurrentRequest();
    if (req == null) {
      return false;
    }
    if (Boolean.TRUE.equals(req.getAttribute(ATTR_TRUSTED_INTERNAL_FEIGN))) {
      return true;
    }
    return secretMatches(req);
  }

  public static boolean secretMatches(HttpServletRequest req) {
    if (req == null) {
      return false;
    }
    if (!isTrustedInboundRemoteAddr(req)) {
      return false;
    }
    String expected = configuredSecret();
    if (StrUtil.isBlank(expected)) {
      return false;
    }
    String header = req.getHeader(InternalSecretHeaderStripFilter.HEADER_NAME);
    if (StrUtil.isBlank(header)) {
      return false;
    }
    return constantTimeEquals(header.trim(), expected);
  }

  private static String configuredSecret() {
    try {
      if (SpringContextUtils.getApplicationContext() == null) {
        return "";
      }
      Environment env = SpringContextUtils.getApplicationContext().getEnvironment();
      String s = env.getProperty("jeecg.internal.call.secret", "");
      return StrUtil.isBlank(s) ? "" : s.trim();
    } catch (Exception e) {
      return "";
    }
  }

  private static boolean constantTimeEquals(String a, String b) {
    byte[] xa = a.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] xb = b.getBytes(java.nio.charset.StandardCharsets.UTF_8);
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
