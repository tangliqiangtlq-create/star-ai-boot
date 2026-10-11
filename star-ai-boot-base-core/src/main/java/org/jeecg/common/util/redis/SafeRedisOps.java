package org.jeecg.common.util.redis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

public final class SafeRedisOps {

  public static final int DEFAULT_SCAN_COUNT = 100;

  private SafeRedisOps() {}

  /**
   * Redis {@code SCAN}-based key iteration — O(1) per cursor chunk, never blocks the whole Redis
   * server like the dangerous {@code KEYS pattern} command. Prefer this helper over any raw {@code
   * redisTemplate.keys(pattern)} call (our codebase should contain zero of those).
   */
  public static List<String> keysToScan(RedisTemplate<String, ?> redisTemplate, String pattern) {
    if (redisTemplate == null || pattern == null) {
      return Collections.emptyList();
    }
    return redisTemplate.execute(
        (RedisCallback<List<String>>)
            connection -> {
              List<String> out = new ArrayList<>();
              ScanOptions opts =
                  ScanOptions.scanOptions().match(pattern).count(DEFAULT_SCAN_COUNT).build();
              try (Cursor<byte[]> cursor =
                  connection.keyCommands() == null
                      ? null
                      : connection.keyCommands().scan(opts)) {
                if (cursor == null) {
                  return out;
                }
                while (cursor.hasNext()) {
                  byte[] raw = cursor.next();
                  if (raw != null) {
                    out.add(new String(raw, java.nio.charset.StandardCharsets.UTF_8));
                  }
                }
              }
              return out;
            });
  }
}
