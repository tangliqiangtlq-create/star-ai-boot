package org.jeecg.common.util;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

/**
 * Production-safe Redis key enumeration / bulk delete via {@code SCAN} (never {@code KEYS}).
 *
 * <p>CodePlan 36 / C2-3: this is the <b>sole</b> pattern-list entry point in mightyPay-boot. Do not
 * add a second SCAN helper. Default {@link #DEFAULT_SCAN_COUNT}=100 and {@link
 * #DEFAULT_SCAN_LIMIT}=50_000 — changing them requires updating {@code
 * security remediation CodePlan 36 (Redis KEYS to SCAN, C2-3 v1.0)}.
 */
@Slf4j
public class RedisScanSupport {

  public static final int DEFAULT_SCAN_COUNT = 100;
  public static final long DEFAULT_SCAN_LIMIT = 50_000L;
  public static final int DEFAULT_DELETE_BATCH_SIZE = 500;
  public static final int MIN_PREFIX_LENGTH = 2;

  public static Set<String> scanKeys(RedisTemplate<String, ?> redisTemplate, String pattern) {
    return scanKeys(redisTemplate, pattern, DEFAULT_SCAN_COUNT, DEFAULT_SCAN_LIMIT);
  }

  public static Set<String> scanKeys(
      RedisTemplate<String, ?> redisTemplate, String pattern, int count, long limit) {
    if (redisTemplate == null || pattern == null || pattern.isEmpty()) {
      return Collections.emptySet();
    }
    if ("*".equals(pattern.trim())) {
      log.warn("[REDIS-SCAN] rejected unbounded MATCH *");
      return Collections.emptySet();
    }
    final int finalCount = (count <= 0) ? DEFAULT_SCAN_COUNT : count;
    final long finalLimit = (limit <= 0) ? DEFAULT_SCAN_LIMIT : limit;
    final String finalPattern = pattern;

    try {
      return redisTemplate.execute(
          (RedisCallback<Set<String>>)
              connection -> {
                Set<String> collected = new LinkedHashSet<>();
                ScanOptions options =
                    ScanOptions.scanOptions().match(finalPattern).count(finalCount).build();

                long scanned = 0L;
                try (Cursor<byte[]> cursor = keyScan(connection, options)) {
                  while (cursor.hasNext()) {
                    byte[] raw = cursor.next();
                    if (raw == null) {
                      continue;
                    }
                    collected.add(new String(raw, StandardCharsets.UTF_8));
                    scanned++;
                    if (scanned >= finalLimit) {
                      log.warn(
                          "[REDIS-SCAN] pattern={} hit scan limit={}, aborting early (collected={})",
                          finalPattern,
                          finalLimit,
                          collected.size());
                      break;
                    }
                  }
                } catch (Exception e) {
                  log.warn(
                      "[REDIS-SCAN] cursor failed pattern={}: {}",
                      finalPattern,
                      e.getMessage());
                }
                return new HashSet<>(collected);
              });
    } catch (Exception ex) {
      log.warn(
          "[REDIS-SCAN] fallback: scan callback failed pattern={}; returning empty set. Reason={}",
          finalPattern,
          ex.getMessage());
      return Collections.emptySet();
    }
  }

  /**
   * Bulk-delete keys matching {@code keyPrefix} (appends {@code *} if missing) via SCAN + batched,
   * cluster-safe per-key delete. Rejects blank / too-short / unbounded prefixes.
   *
   * @return approximate number of keys deleted
   */
  public static long deleteByPattern(RedisTemplate<String, ?> redisTemplate, String keyPrefix) {
    if (redisTemplate == null) {
      return 0L;
    }
    if (keyPrefix == null || keyPrefix.isBlank()) {
      log.warn("[REDIS-SCAN] deleteByPattern rejected blank prefix");
      return 0L;
    }
    String trimmed = keyPrefix.trim();
    if (trimmed.length() < MIN_PREFIX_LENGTH) {
      log.warn("[REDIS-SCAN] deleteByPattern rejected short prefix={}", trimmed);
      return 0L;
    }
    if ("*".equals(trimmed) || "**".equals(trimmed)) {
      log.warn("[REDIS-SCAN] deleteByPattern rejected unbounded pattern={}", trimmed);
      return 0L;
    }
    String pattern = trimmed.endsWith("*") ? trimmed : trimmed + "*";
    Set<String> keys = scanKeys(redisTemplate, pattern);
    if (keys == null || keys.isEmpty()) {
      return 0L;
    }
    long total = 0L;
    List<String> batch = new ArrayList<>(DEFAULT_DELETE_BATCH_SIZE);
    for (String key : keys) {
      if (key == null || key.isEmpty()) {
        continue;
      }
      batch.add(key);
      if (batch.size() >= DEFAULT_DELETE_BATCH_SIZE) {
        total += deleteEach(redisTemplate, batch);
        batch.clear();
      }
    }
    if (!batch.isEmpty()) {
      total += deleteEach(redisTemplate, batch);
    }
    return total;
  }

  /**
   * Cluster-safe delete of an explicit key collection: <b>one single-key {@code DEL} per key</b>.
   *
   * <p>Redis Cluster rejects any command whose keys do not hash to the same slot ({@code CROSSSLOT}).
   * Spring Data Redis does not split multi-key {@code DEL} client-side and Lettuce routes a
   * multi-key command by its first key — so {@code delete(Collection)} over arbitrary cache keys
   * fails on a cluster. Because that failure used to be swallowed here, it silently skipped the
   * eviction (dictionary / department / rate-lock caches stayed stale). Issuing per-key deletes
   * keeps every command single-key and therefore cluster-safe by construction.
   *
   * @return number of keys actually deleted
   */
  public static long deleteEach(RedisTemplate<String, ?> redisTemplate, Collection<String> keys) {
    if (redisTemplate == null || keys == null || keys.isEmpty()) {
      return 0L;
    }
    long total = 0L;
    for (String key : keys) {
      if (key == null || key.isEmpty()) {
        continue;
      }
      try {
        if (Boolean.TRUE.equals(redisTemplate.delete(key))) {
          total++;
        }
      } catch (Exception e) {
        log.warn("[REDIS-SCAN] per-key delete failed key={}: {}", key, e.getMessage());
      }
    }
    return total;
  }

  private static Cursor<byte[]> keyScan(RedisConnection connection, ScanOptions options) {
    RedisConnection nativeConn =
        connection.getNativeConnection() instanceof RedisConnection
            ? (RedisConnection) connection.getNativeConnection()
            : connection;
    return nativeConn.keyCommands().scan(options);
  }

  private RedisScanSupport() {}
}
