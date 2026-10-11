package org.jeecg.common.env;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * IDE / local: load repository-root {@code .env} into the Spring {@link ConfigurableEnvironment}
 * so Nacos placeholders like {@code ${INTERNAL_CALL_SECRET:}} resolve without stuffing secrets into
 * Filter/Feign fallbacks.
 *
 * <p>Registered via {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports}.
 * Runs before {@code spring.config.import=nacos:...} property resolution.
 */
public class RepoRootDotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  public static final String PROPERTY_SOURCE_NAME = "repoRootDotEnv";

  @Override
  public int getOrder() {
    // Before Nacos ConfigData / application.yaml placeholders are fully resolved.
    return Ordered.HIGHEST_PRECEDENCE + 10;
  }

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
      return;
    }
    Map<String, Object> map = RepoRootEnvLoader.readDotEnvAsMap();
    if (map.isEmpty()) {
      return;
    }
    // Prefer non-blank OS env / already-set Spring props; only fill gaps from .env
    Map<String, Object> fill = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : map.entrySet()) {
      String key = e.getKey();
      String fromEnv = environment.getProperty(key);
      if (fromEnv != null && !fromEnv.isBlank()) {
        continue;
      }
      fill.put(key, e.getValue());
    }
    if (fill.isEmpty()) {
      return;
    }
    environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, fill));
    System.out.println(
        "[env] EnvironmentPostProcessor added "
            + fill.size()
            + " keys from repo-root .env as PropertySource '"
            + PROPERTY_SOURCE_NAME
            + "'");
  }
}
