package org.jeecg.config.redisson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JeecgRedissonAddressUtilsTest {

  @Test
  void hostPort_plain() {
    assertEquals(
        "redis://127.0.0.1:6379", JeecgRedissonAddressUtils.toUri("127.0.0.1:6379", false));
  }

  @Test
  void hostPort_ssl() {
    assertEquals(
        "rediss://master.example.com:6379",
        JeecgRedissonAddressUtils.toUri("master.example.com:6379", true));
  }

  @Test
  void upgradeRedisSchemeWhenSsl() {
    assertEquals(
        "rediss://master.example.com:6379",
        JeecgRedissonAddressUtils.toUri("redis://master.example.com:6379", true));
  }

  @Test
  void keepRedissScheme() {
    assertEquals(
        "rediss://master.example.com:6379",
        JeecgRedissonAddressUtils.toUri("rediss://master.example.com:6379", false));
  }

  @Test
  void resolveSsl_fromFlagOrScheme() {
    assertTrue(JeecgRedissonTlsAutoConfiguration.resolveSsl("rediss://h:6379", false));
    assertTrue(JeecgRedissonTlsAutoConfiguration.resolveSsl("h:6379", true));
    assertFalse(JeecgRedissonTlsAutoConfiguration.resolveSsl("h:6379", false));
  }
}
