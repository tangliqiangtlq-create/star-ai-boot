package org.jeecg.filter;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * PR-CL-B1: Gateway-to-downstream HMAC signature writer.
 *
 * <p>Every request forwarded from gateway to any business microservice carries two headers:
 *
 * <ol>
 *   <li>{@code X-Gateway-Exp} — epoch-second expiration (default now + 30s, tight window to avoid
 *       replay)
 *   <li>{@code X-Gateway-Sig} — lower-hex HMAC-SHA256 over {@code normalized_path + ":" + exp}
 *       keyed by {@code GATEWAY_SECRET}
 * </ol>
 *
 * <p>Downstream consumers validate with the symmetric {@link
 * org.jeecg.config.filter.GatewaySignatureFilter} registered in every WebMvc start module.
 *
 * <p>Fail-close: if {@code GATEWAY_SECRET} is blank the application refuses to start under any
 * non-dev profile. {@code GatewaySignatureFilter#startupFailFast} mirrors the same check so
 * downstream services that miss the secret exit before accepting traffic.
 */
@Component
public class GatewaySignatureWriteFilter implements GlobalFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(GatewaySignatureWriteFilter.class);

  public static final String HEADER_SIG = "X-Gateway-Sig";
  public static final String HEADER_EXP = "X-Gateway-Exp";

  @Value("${gateway.sig.secret:${GATEWAY_SECRET:}}")
  private String secret;

  @Value("${gateway.sig.ttl-seconds:${GATEWAY_SIG_TTL:30}}")
  private int ttlSeconds;

  @Value("${gateway.sig.required-in-prod:true}")
  private boolean requiredInProd;

  @Value("${spring.profiles.active:}")
  private String activeProfile;

  @PostConstruct
  public void startupFailFast() {
    boolean blank = StringUtils.isBlank(secret);
    if (isProd() && requiredInProd && blank) {
      String err =
          "[PR-CL-B1-3] gateway sig secret is empty on profile="
              + activeProfile
              + ". Set GATEWAY_SECRET (>= 32 chars) or explicitly gateway.sig.required-in-prod=false.";
      log.error(err);
      throw new IllegalStateException(err);
    }
    if (!blank && secret.trim().length() < 16) {
      throw new IllegalStateException("[PR-CL-B1-3] GATEWAY_SECRET too short (<16 chars, >=32 recommended).");
    }
    if (ttlSeconds < 5 || ttlSeconds > 300) {
      log.warn("gateway.sig.ttl-seconds={} outside recommended [5..300]; clamping to 30.", ttlSeconds);
      ttlSeconds = 30;
    }
    log.info(
        "GatewaySignatureWriteFilter startupOk: activeProfile={} configured={} ttl={}s",
        activeProfile, !blank, ttlSeconds);
  }

  private boolean isProd() {
    if (StringUtils.isBlank(activeProfile)) {
      return false;
    }
    String low = activeProfile.toLowerCase();
    return low.contains("prod") || low.contains("prd") || low.contains("production");
  }

  private static String hmacSha256(String data, String key) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      SecretKeySpec spec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
      mac.init(spec);
      byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
        sb.append(String.format(Locale.ROOT, "%02x", b));
      }
      return sb.toString();
    } catch (Exception e) {
      throw new IllegalStateException("HMAC-SHA256 unavailable", e);
    }
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (StringUtils.isBlank(secret)) {
      return chain.filter(exchange);
    }
    ServerHttpRequest req = exchange.getRequest();
    String path = req.getURI().getRawPath();
    long exp = System.currentTimeMillis() / 1000L + ttlSeconds;
    String payload = path + ":" + exp;
    String sig = hmacSha256(payload, secret.trim());
    ServerHttpRequest mutated =
        req.mutate()
            .header(HEADER_EXP, String.valueOf(exp))
            .header(HEADER_SIG, sig == null ? "" : sig)
            .build();
    return chain.filter(exchange.mutate().request(mutated).build());
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE - 9000;
  }
}
