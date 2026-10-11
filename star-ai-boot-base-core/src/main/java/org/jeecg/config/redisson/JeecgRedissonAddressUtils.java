package org.jeecg.config.redisson;

import org.apache.commons.lang3.StringUtils;

/**
 * Normalizes jeecg.redisson.address to a Redisson URI (redis:// or rediss://).
 */
public final class JeecgRedissonAddressUtils {

  private static final String REDIS = "redis://";
  private static final String REDIS_SSL = "rediss://";

  private JeecgRedissonAddressUtils() {}

  /**
   * @param address host:port, or already prefixed with redis:// / rediss://
   * @param ssl when true, use rediss:// (TLS)
   */
  public static String toUri(String address, boolean ssl) {
    if (StringUtils.isBlank(address)) {
      throw new IllegalArgumentException("jeecg.redisson.address is not configured");
    }
    String trimmed = address.trim();
    if (trimmed.startsWith(REDIS_SSL)) {
      return trimmed;
    }
    if (trimmed.startsWith(REDIS)) {
      String hostPart = trimmed.substring(REDIS.length());
      return (ssl ? REDIS_SSL : REDIS) + hostPart;
    }
    return (ssl ? REDIS_SSL : REDIS) + trimmed;
  }
}
