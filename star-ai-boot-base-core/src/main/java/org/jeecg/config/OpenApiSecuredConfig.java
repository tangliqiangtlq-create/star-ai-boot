package org.jeecg.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.servers.Server;
import java.util.ArrayList;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * V-HIGH-01 / P2 Hardening: Force every published OpenAPI document (if any
 * profile re-enables springdoc for UAT/internal docs) to advertise an
 * {@code https://} server URL — regardless of whether
 * {@code server.forward-headers-strategy} is wired correctly on the current
 * deployment.
 *
 * <p>Without this bean, a mis-configured reverse proxy (missing
 * {@code X-Forwarded-Proto}) can leak {@code http://<host>} inside the publicly
 * reachable {@code /v3/api-docs} JSON, which downgrades security and discloses
 * the internal plaintext topology.
 *
 * <p>Production default keeps springdoc closed (see nacos_prod.yaml key
 * {@code springdoc.api-docs.enabled=false}), so this bean will normally sit
 * idle; it is a guardrail for any future scenario where docs get temporarily
 * re-enabled for live troubleshooting.
 */
@Configuration
public class OpenApiSecuredConfig {

  @Value("${jeecg.public-domain:}")
  private String publicDomain;

  @Bean
  public OpenApiCustomizer securedServerUrlOpenApiCustomizer() {
    return openApi -> rewriteServersToHttps(openApi, publicDomain);
  }

  static void rewriteServersToHttps(OpenAPI openApi, String publicDomain) {
    String proto = "https";
    String host =
        (publicDomain != null && !publicDomain.isBlank())
            ? publicDomain
            : "unknown-host";
    // Use scheme://host only — never echo the incoming request scheme back
    // because an attacker could spoof X-Forwarded-Proto on mis-configured LBs.
    Server secured =
        new Server()
            .url(proto + "://" + host)
            .description("HTTPS-only (OpenApiSecuredConfig hardening)");
    List<Server> single = new ArrayList<>(1);
    single.add(secured);
    openApi.setServers(single);
  }
}
