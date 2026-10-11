package org.jeecg.modules.system.passwordreset.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.HttpResponseUtils;
import org.jeecg.common.util.RedisUtil;
import org.jeecg.modules.system.passwordreset.config.PasswordResetProperties;
import org.jeecg.modules.system.passwordreset.helper.ClientIpResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Password-reset anonymous IP rate-limit filter (P3-3).
 *
 * <p>Policy:
 * <ul>
 *   <li>60s window; GET /sys/password-reset/meta/* count > 50 → ban 10min</li>
 *   <li>60s window; POST /sys/password-reset/submit count > 20 → ban 10min</li>
 *   <li>Banned IP gets 403 LINK_INVALID when Redis ban key exists</li>
 * </ul>
 *
 * <p>Redis counter TTL=60s; INCR per request. Ban key: PWD_RESET_BAN:{ip} TTL 600s.</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 90)
@RequiredArgsConstructor
public class PasswordResetIpRateLimitFilter extends OncePerRequestFilter {

    private static final String META_PREFIX = "/sys/password-reset/meta";
    private static final String SUBMIT_PATH = "/sys/password-reset/submit";
    private static final String CAPTCHA_PATH = "/sys/password-reset/captcha";

    private static final int META_LIMIT = 50;
    private static final int SUBMIT_LIMIT = 20;
    private static final int CAPTCHA_LIMIT = 30;
    private static final int WINDOW_SEC = 60;
    private static final int BAN_SEC = 600;

    private static final String COUNT_META_PREFIX = "PWD_RESET_META_CNT:";
    private static final String COUNT_SUBMIT_PREFIX = "PWD_RESET_SUBMIT_CNT:";
    private static final String COUNT_CAPTCHA_PREFIX = "PWD_RESET_CAPTCHA_CNT:";
    private static final String BAN_PREFIX = "PWD_RESET_BAN:";

    private final RedisUtil redisUtil;
    private final ClientIpResolver clientIpResolver;
    private final PasswordResetProperties props;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (!props.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }
        String uri = request.getRequestURI();
        String method = request.getMethod();
        boolean isMeta = "GET".equalsIgnoreCase(method) && uri.startsWith(META_PREFIX);
        boolean isSubmit = "POST".equalsIgnoreCase(method) && SUBMIT_PATH.equals(uri);
        boolean isCaptcha = "GET".equalsIgnoreCase(method) && CAPTCHA_PATH.equals(uri);
        if (!isMeta && !isSubmit && !isCaptcha) {
            chain.doFilter(request, response);
            return;
        }
        String ip = clientIpResolver.resolve(request).getClientIp();
        // 1. Ban check first
        String banKey = BAN_PREFIX + ip;
        if (Boolean.TRUE.equals(redisUtil.hasKey(banKey))) {
            log.warn("[PasswordResetRateLimit] IP={} banned, rejecting uri={}", ip, uri);
            writeForbidden(response, "Too many requests. Please try again later");
            return;
        }
        // 2. Window counter
        int limit;
        String countKey;
        if (isMeta) {
            limit = META_LIMIT;
            countKey = COUNT_META_PREFIX + ip;
        } else if (isSubmit) {
            limit = SUBMIT_LIMIT;
            countKey = COUNT_SUBMIT_PREFIX + ip;
        } else {
            limit = CAPTCHA_LIMIT;
            countKey = COUNT_CAPTCHA_PREFIX + ip;
        }
        long count = incrementAndGet(countKey, WINDOW_SEC);
        if (count > limit) {
            log.warn("[PasswordResetRateLimit] IP={} rate limited uri={} count={} limit={}, banned for 10min", ip, uri, count, limit);
            redisUtil.set(banKey, "1", BAN_SEC);
            writeForbidden(response, "Too many requests. Please try again in 10 minutes");
            return;
        }
        chain.doFilter(request, response);
    }

    private long incrementAndGet(String key, int ttlSec) {
        try {
            Object cur = redisUtil.get(key);
            long next = 1L;
            if (cur != null) {
                next = Long.parseLong(cur.toString()) + 1L;
            }
            redisUtil.set(key, String.valueOf(next), ttlSec);
            return next;
        } catch (Exception e) {
            log.warn("[PasswordResetRateLimit] counting exception: {}");
            return 0L;
        }
    }

    private static void writeForbidden(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        java.util.Map<String, String> body = new java.util.LinkedHashMap<>();
        body.put("code", "LINK_INVALID");
        body.put("message", msg == null ? "" : msg);
        HttpResponseUtils.writeJson(response, body);
    }
}
