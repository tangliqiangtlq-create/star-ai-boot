package org.jeecg.config.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.jeecg.common.util.security.IpAccessGuard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PR-CL-32-HS / G-32-07: Strip and fail-close on forged {@code X-Internal-Call-Secret} headers
 * arriving from non-internal (public / untrusted proxy) remote addresses.
 *
 * <p>Rules (fail-close, R2b-2 mandatory):
 *
 * <ol>
 *   <li>If {@code req.getRemoteAddr()} is NOT in the private CIDR set of {@link IpAccessGuard} AND
 *       the request carries {@code X-Internal-Call-Secret} header → HTTP 403 JSON (code=40303) and
 *       terminate the filter chain. Internal-call secrets are only valid on gateway-pod →
 *       service-pod or pod-to-pod Feign paths.
 *   <li>Otherwise: allow chain to proceed.
 * </ol>
 *
 * <p>Order in {@link org.jeecg.config.WebMvcConfiguration} MUST be {@code
 * Ordered.HIGHEST_PRECEDENCE} — higher priority than {@link GatewaySignatureFilter} (HP+3) and
 * {@link InternalTrustedS2sFilter} (HP+1) so an attacker cannot slip a forged secret past the
 * outermost strip gate before any downstream secretMatches() reads the header.
 */
public class InternalSecretHeaderStripFilter implements Filter {

  private static final Logger log = LoggerFactory.getLogger(InternalSecretHeaderStripFilter.class);

  public static final String HEADER_NAME = "X-Internal-Call-Secret";

  private static final byte[] FORGED_HEADER_BODY =
      ("{\"success\":false,\"code\":40303,\"message\":"
              + "\"forged internal secret header from non-internal IP\"}")
          .getBytes(StandardCharsets.UTF_8);

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      chain.doFilter(request, response);
      return;
    }
    HttpServletRequest req = (HttpServletRequest) request;
    HttpServletResponse resp = (HttpServletResponse) response;
    String header = req.getHeader(HEADER_NAME);
    if (header != null
        && !IpAccessGuard.isPrivateIpOrLoopback(req.getRemoteAddr())) {
      String uri = req.getRequestURI();
      String remote = req.getRemoteAddr();
      log.warn(
          "INTERNAL_SECRET_FORGED_BLOCKED uri={} remote={} ua={}",
          uri,
          remote,
          req.getHeader("User-Agent"));
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      resp.setContentType("application/json;charset=UTF-8");
      resp.getOutputStream().write(FORGED_HEADER_BODY);
      return;
    }
    chain.doFilter(request, response);
  }
}
