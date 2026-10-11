package org.jeecg.common.modules.redis.writer;

import java.time.Duration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Classpath override of upstream {@code jeecg-boot-common} {@code JeecgRedisCacheWriter}.
 *
 * <p>CodePlan 36 / C2-3: same FQCN so {@code RedisConfig#cacheManager} {@code new
 * JeecgRedisCacheWriter(...)} binds to this SCAN implementation when this module precedes the
 * common jar on the classpath. Prefer {@link
 * org.jeecg.config.redis.ScanSafeRedisCacheManagerConfig} {@code @Primary CacheManager} as the
 * authoritative path.
 */
public class JeecgRedisCacheWriter extends ScanSafeJeecgRedisCacheWriter {

  public JeecgRedisCacheWriter(RedisConnectionFactory connectionFactory) {
    super(connectionFactory);
  }

  public JeecgRedisCacheWriter(RedisConnectionFactory connectionFactory, Duration sleepTime) {
    super(connectionFactory, sleepTime);
  }
}
