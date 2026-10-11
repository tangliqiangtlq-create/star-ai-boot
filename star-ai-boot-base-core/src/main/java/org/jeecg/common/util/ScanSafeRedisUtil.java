package org.jeecg.common.util;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * CodePlan 36 / C2-3: {@code @Primary} override of upstream {@link RedisUtil#removeAll(String)}.
 *
 * <p>Upstream {@code jeecg-boot-common} already uses {@code SCAN} (not {@code KEYS}), but with {@code
 * COUNT=Integer.MAX_VALUE} and no collection limit. This bean keeps all other RedisUtil behavior
 * and routes prefix deletes through {@link RedisScanSupport#deleteByPattern} (COUNT=100, limit
 * 50_000, batched unlink).
 */
@Slf4j
@Primary
@Component
public class ScanSafeRedisUtil extends RedisUtil {

  @Resource private RedisTemplate<String, Object> redisTemplate;

  @Override
  public void removeAll(String keyPrefix) {
    long deleted = RedisScanSupport.deleteByPattern(redisTemplate, keyPrefix);
    if (log.isDebugEnabled()) {
      log.debug("[REDIS-SCAN] ScanSafeRedisUtil.removeAll prefix={} deleted~{}", keyPrefix, deleted);
    }
  }
}
