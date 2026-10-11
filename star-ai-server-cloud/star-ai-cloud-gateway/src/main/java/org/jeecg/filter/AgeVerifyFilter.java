package org.jeecg.filter;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 18+ 内容合规过滤器 —— 在 Gateway 层拦截 stream 相关路径
 *
 * 检查:
 * ① 请求头 Age-Verified: true → 已过, 放行
 * ② Cookie age_verified=1 → 已过, 放行
 * ③ 其他 → 返回 418 + 引导前端打开年龄验证弹窗
 *
 * Phase 1 轻量实现, 不查后端 age_verification 表, 仅依赖前端状态
 */
@Component
public class AgeVerifyFilter implements WebFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AgeVerifyFilter.class);

    private static final byte[] AGE_BLOCKED_BODY =
        "{\"success\":false,\"code\":418,\"message\":\"age verification required\",\"redirect\":\"/age-verify\"}"
            .getBytes(StandardCharsets.UTF_8);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest req = exchange.getRequest();
        String path = req.getURI().getPath();

        if (!isSensitivePath(path)) {
            return chain.filter(exchange);
        }

        String ageVerified = req.getHeaders().getFirst("Age-Verified");
        if ("true".equalsIgnoreCase(ageVerified)) {
            return chain.filter(exchange);
        }

        String cookieAge = extractCookie(req, "age_verified");
        if ("1".equals(cookieAge)) {
            return chain.filter(exchange);
        }

        log.info("年龄校验拦截 path={}, remoteIp={}", path, req.getRemoteAddress());
        ServerHttpResponse resp = exchange.getResponse();
        resp.setStatusCode(HttpStatus.I_AM_A_TEAPOT);
        resp.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buf = resp.bufferFactory().wrap(AGE_BLOCKED_BODY);
        return resp.writeWith(Mono.just(buf));
    }

    private boolean isSensitivePath(String path) {
        return path.startsWith("/stream/play")
            || path.startsWith("/stream/videos/") && path.contains("/play")
            || path.startsWith("/stream/videos/") && path.contains("/preview");
    }

    private String extractCookie(ServerHttpRequest req, String name) {
        String cookie = req.getHeaders().getFirst("Cookie");
        if (cookie == null) return null;
        for (String part : cookie.split(";")) {
            String kv = part.trim();
            int eq = kv.indexOf('=');
            if (eq > 0 && kv.substring(0, eq).equals(name)) {
                return kv.substring(eq + 1);
            }
        }
        return null;
    }

    @Override
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE - 50; }
}
