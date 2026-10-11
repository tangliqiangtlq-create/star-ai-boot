package org.jeecg.modules.system.passwordreset.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.modules.system.passwordreset.config.PasswordResetProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Password-reset Origin/Referer allowlist filter (D-2 medium fix).
 *
 * <p>Intercept:
 * <ul>
 *   <li>GET /sys/password-reset/meta/*</li>
 *   <li>POST /sys/password-reset/submit</li>
 *   <li>POST /sys/password-reset/captcha</li>
 * </ul>
 *
 * <p>Allow rules (any match allows; else 403 LINK_INVALID):
 * <ol>
 *   <li>Origin or Referer matches configured portalOrigin (strict host+scheme+port)</li>
 *   <li>Empty Origin/Referer: treat as direct client (Postman/cURL) → allow, WARN for prod audit</li>
 * </ol>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@RequiredArgsConstructor
public class PasswordResetOriginFilter implements Filter {

    private static final String META_PREFIX = "/sys/password-reset/meta";
    private static final String SUBMIT_PATH = "/sys/password-reset/submit";
    private static final String CAPTCHA_PATH = "/sys/password-reset/captcha";

    private final PasswordResetProperties props;

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;
        String uri = request.getRequestURI();
        boolean isMeta = uri.startsWith(META_PREFIX);
        boolean isSubmit = SUBMIT_PATH.equals(uri);
        boolean isCaptcha = CAPTCHA_PATH.equals(uri);
        if (!isMeta && !isSubmit && !isCaptcha) {
            chain.doFilter(req, resp);
            return;
        }
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        String portalOrigin = props.getPortalOrigin();

        boolean originOk = origin == null || origin.isEmpty() || matchesOrigin(origin, portalOrigin);
        boolean refererOk = referer == null || referer.isEmpty() || matchesOrigin(referer, portalOrigin);
        if (!originOk || !refererOk) {
            log.warn("[PasswordResetOriginFilter] invalid Origin/Referer blocked: uri={}, origin={}, referer={}, portalOrigin={}",
                    uri, origin, referer, portalOrigin);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"LINK_INVALID\",\"message\":\"Link is invalid or expired. Please request a new one\"}");
            return;
        }
        if ((origin == null || origin.isEmpty()) && (referer == null || referer.isEmpty())) {
            // Allow empty Origin/Referer for local/intranet debug; log it
            log.debug("[PasswordResetOriginFilter] empty Origin/Referer allowed (debug mode): uri={}, remote={}", uri, request.getRemoteAddr());
        }
        chain.doFilter(req, resp);
    }

    static boolean matchesOrigin(String headerValue, String portalOrigin) {
        if (headerValue == null || headerValue.isEmpty()) return true;
        String headerRoot = extractOrigin(headerValue);
        String portalRoot = extractOrigin(portalOrigin);
        return portalRoot != null && portalRoot.equalsIgnoreCase(headerRoot);
    }

    private static String extractOrigin(String s) {
        if (s == null) return null;
        try {
            URI uri = new URI(s.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            if (scheme == null || host == null) return null;
            StringBuilder sb = new StringBuilder(scheme).append("://").append(host.toLowerCase());
            if (port > 0 && !isDefaultPort(scheme, port)) {
                sb.append(':').append(port);
            }
            return sb.toString();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static boolean isDefaultPort(String scheme, int port) {
        if ("http".equalsIgnoreCase(scheme)) return port == 80;
        if ("https".equalsIgnoreCase(scheme)) return port == 443;
        return false;
    }
}
