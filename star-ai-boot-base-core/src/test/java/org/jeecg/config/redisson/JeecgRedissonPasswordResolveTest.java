package org.jeecg.config.redisson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * Regression tests for "Redisson silently connects without AUTH → {@code NOAUTH Authentication required}".
 *
 * <p>Secrets are injected by {@code MpaySecretsEnvironmentPostProcessor} into the Spring
 * {@code Environment} only (never OS env), so the password must be resolvable from those exact keys.
 */
class JeecgRedissonPasswordResolveTest {

  private static StandardEnvironment envWith(Map<String, Object> values) {
    StandardEnvironment environment = new StandardEnvironment();
    // Isolate from the machine running the tests (a stray SPRING_REDIS_PASSWORD would mask failures).
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    if (!values.isEmpty()) {
      environment.getPropertySources().addFirst(new MapPropertySource("test", values));
    }
    return environment;
  }

  @Test
  void boundPropertyWins() {
    Map<String, Object> values = new HashMap<>();
    values.put("SPRING_REDIS_PASSWORD", "from-secrets");
    assertEquals(
        "from-nacos",
        JeecgRedissonTlsAutoConfiguration.resolvePassword(envWith(values), "from-nacos"));
  }

  @Test
  void fallsBackToSecretsKeyWhenBoundPropertyMissing() {
    Map<String, Object> values = new HashMap<>();
    values.put("SPRING_REDIS_PASSWORD", "from-secrets");
    assertEquals(
        "from-secrets", JeecgRedissonTlsAutoConfiguration.resolvePassword(envWith(values), null));
  }

  @Test
  void prefersDedicatedRedissonSecretsKey() {
    Map<String, Object> values = new HashMap<>();
    values.put("SPRING_REDIS_PASSWORD", "spring-key");
    values.put("JEECG_REDISSON_PASSWORD", "redisson-key");
    assertEquals(
        "redisson-key", JeecgRedissonTlsAutoConfiguration.resolvePassword(envWith(values), null));
  }

  @Test
  void blankBoundPropertyDoesNotBlockFallback() {
    Map<String, Object> values = new HashMap<>();
    values.put("SPRING_REDIS_PASSWORD", "from-secrets");
    assertEquals(
        "from-secrets", JeecgRedissonTlsAutoConfiguration.resolvePassword(envWith(values), "   "));
  }

  @Test
  void unresolvedNacosPlaceholderDoesNotBlockFallback() {
    Map<String, Object> values = new HashMap<>();
    values.put("SPRING_REDIS_PASSWORD", "from-secrets");
    assertEquals(
        "from-secrets",
        JeecgRedissonTlsAutoConfiguration.resolvePassword(
            envWith(values), "${SPRING_REDIS_PASSWORD}"));
  }

  @Test
  void emptySecretValueIsTreatedAsNotProvided() {
    Map<String, Object> values = new HashMap<>();
    values.put("JEECG_REDISSON_PASSWORD", "");
    values.put("SPRING_REDIS_PASSWORD", "from-secrets");
    assertEquals(
        "from-secrets", JeecgRedissonTlsAutoConfiguration.resolvePassword(envWith(values), null));
  }

  @Test
  void returnsNullWhenNothingIsProvided() {
    assertNull(JeecgRedissonTlsAutoConfiguration.resolvePassword(envWith(Map.of()), null));
    assertNull(JeecgRedissonTlsAutoConfiguration.resolvePassword(null, " "));
  }

  @Test
  void trimsSurroundingWhitespace() {
    Map<String, Object> values = new HashMap<>();
    values.put("SPRING_REDIS_PASSWORD", "  padded  ");
    assertEquals(
        "padded", JeecgRedissonTlsAutoConfiguration.resolvePassword(envWith(values), null));
  }
}
