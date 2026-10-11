package org.jeecg.config;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * PR-CL-B1 startup observability. Emits a single structured log line after bean initialization that
 * summarizes the three most important gateway runtime flags so operators can diagnose "am I running with the
 * intended security posture?" without having to diff yml files.
 *
 * <p>Flags logged:
 *
 * <ul>
 *   <li>{@code GATEWAY_ROUTES_EXPLICIT} — whether discovery locator is disabled (routes are declared
 *       explicitly, the secure default in prod)
 *   <li>{@code GATEWAY_CORS_ORIGIN_COUNT} — number of origins in the allow-list (non-"*" patterns only)
 *   <li>{@code GATEWAY_SIG_ACTIVE} — whether gateway HMAC signing is active
 * </ul>
 */
@Configuration
@Slf4j
public class GatewayHardeningStartupConfig {

  @Value("${spring.cloud.gateway.discovery.locator.enabled:false}")
  private boolean locatorEnabled;

  @Value("${spring.cloud.gateway.globalcors.cors-configurations.'[/**]'.allowed-origin-patterns:}")
  private String corsOriginsRaw;

  @Value("${gateway.cors.allowed-origins:${CORS_ALLOWED_ORIGINS:}}")
  private String corsOriginsEnvRaw;

  @Value("${gateway.sig.secret:${GATEWAY_SECRET:}}")
  private String gatewaySigSecret;

  @Value("${spring.profiles.active:}")
  private String activeProfile;

  @PostConstruct
  public void logHardeningFlags() {
    boolean routesExplicit = !locatorEnabled;
    int origins = parseOrigins().size();
    boolean wildcard =
        StringUtils.isNotBlank(corsOriginsRaw)
            && (corsOriginsRaw.contains("\"*\"") || "*".equals(corsOriginsRaw.trim()));
    boolean sigActive = StringUtils.isNotBlank(gatewaySigSecret);
    log.info(
        "GATEWAY_HARDENING profile={} GATEWAY_ROUTES_EXPLICIT={} GATEWAY_LOCATOR_ENABLED={}"
            + " GATEWAY_CORS_ORIGIN_COUNT={} GATEWAY_CORS_WILDCARD={} GATEWAY_SIG_ACTIVE={}",
        activeProfile,
        routesExplicit,
        locatorEnabled,
        wildcard ? -1 : origins,
        wildcard,
        sigActive);
    if (!locatorEnabled) {
      log.info(
          "[GW-DIAG] discovery locator DISABLED — ApiServiceRouteWebFilter only strips /api; "
              + "paths stay /sys/** to match Redis explicit routes (Path=/sys/** → lb://jeecg-system). "
              + "If Redis routes empty → 404.");
    } else {
      log.info(
          "[GW-DIAG] discovery locator ENABLED — rewrite /sys/** → /jeecg-system/sys/**");
    }
  }

  private List<String> parseOrigins() {
    List<String> out = new ArrayList<>();
    for (String raw : Arrays.asList(corsOriginsRaw, corsOriginsEnvRaw)) {
      if (StringUtils.isBlank(raw)) {
        continue;
      }
      for (String p : raw.split(",")) {
        String s = p == null ? "" : p.trim();
        if (s.isEmpty()) {
          continue;
        }
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
          s = s.substring(1, s.length() - 1);
        }
        if (!s.isEmpty() && !"*".equals(s)) {
          out.add(s);
        }
      }
    }
    return Collections.unmodifiableList(out);
  }
}
