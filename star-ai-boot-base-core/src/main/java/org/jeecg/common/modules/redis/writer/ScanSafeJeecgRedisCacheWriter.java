package org.jeecg.common.modules.redis.writer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.util.RedisScanSupport;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.redis.cache.CacheStatistics;
import org.springframework.data.redis.cache.CacheStatisticsCollector;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * CodePlan 36 / C2-3: SCAN-based replacement for upstream {@code JeecgRedisCacheWriter} which used
 * blocking {@code KEYS} in {@link #remove} / {@link #clean}.
 *
 * <p>Registered via {@link org.jeecg.config.redis.ScanSafeRedisCacheManagerConfig} as {@code
 * @Primary CacheManager} so Spring Cache eviction never hits the jar's KEYS path.
 */
@Slf4j
public class ScanSafeJeecgRedisCacheWriter implements RedisCacheWriter {

  private final RedisConnectionFactory connectionFactory;
  private final Duration sleepTime;
  private final CacheStatisticsCollector statistics = CacheStatisticsCollector.create();

  public ScanSafeJeecgRedisCacheWriter(RedisConnectionFactory connectionFactory) {
    this(connectionFactory, Duration.ZERO);
  }

  public ScanSafeJeecgRedisCacheWriter(
      RedisConnectionFactory connectionFactory, Duration sleepTime) {
    Assert.notNull(connectionFactory, "ConnectionFactory must not be null!");
    Assert.notNull(sleepTime, "SleepTime must not be null!");
    this.connectionFactory = connectionFactory;
    this.sleepTime = sleepTime;
  }

  @Override
  public void put(String name, byte[] key, byte[] value, @Nullable Duration ttl) {
    Assert.notNull(name, "Name must not be null!");
    Assert.notNull(key, "Key must not be null!");
    Assert.notNull(value, "Value must not be null!");
    execute(
        name,
        connection -> {
          if (shouldExpireWithin(ttl)) {
            connection.set(
                key,
                value,
                Expiration.from(ttl.toMillis(), TimeUnit.MILLISECONDS),
                SetOption.upsert());
          } else {
            connection.set(key, value);
          }
          return "OK";
        });
  }

  @Override
  public CompletableFuture<Void> store(
      String name, byte[] key, byte[] value, @Nullable Duration ttl) {
    return CompletableFuture.runAsync(() -> put(name, key, value, ttl));
  }

  @Override
  public byte[] get(String name, byte[] key) {
    Assert.notNull(name, "Name must not be null!");
    Assert.notNull(key, "Key must not be null!");
    return execute(name, connection -> connection.get(key));
  }

  @Override
  public CompletableFuture<byte[]> retrieve(String name, byte[] key, @Nullable Duration ttl) {
    return CompletableFuture.supplyAsync(() -> get(name, key));
  }

  @Override
  public byte[] putIfAbsent(String name, byte[] key, byte[] value, @Nullable Duration ttl) {
    Assert.notNull(name, "Name must not be null!");
    Assert.notNull(key, "Key must not be null!");
    Assert.notNull(value, "Value must not be null!");
    return execute(
        name,
        connection -> {
          if (isLockingCacheWriter()) {
            doLock(name, connection);
          }
          try {
            boolean put;
            if (shouldExpireWithin(ttl)) {
              put = connection.set(key, value, Expiration.from(ttl), SetOption.ifAbsent());
            } else {
              put = Boolean.TRUE.equals(connection.setNX(key, value));
            }
            if (!put) {
              return connection.get(key);
            }
            return null;
          } finally {
            if (isLockingCacheWriter()) {
              doUnlock(name, connection);
            }
          }
        });
  }

  @Override
  public void remove(String name, byte[] key) {
    Assert.notNull(name, "Name must not be null!");
    Assert.notNull(key, "Key must not be null!");
    String keyString = new String(key, StandardCharsets.UTF_8);
    if (keyString.endsWith("*")) {
      execute(name, connection -> deleteByScan(connection, keyString));
    } else {
      execute(name, connection -> connection.del(key));
    }
  }

  @Override
  public void clean(String name, byte[] pattern) {
    Assert.notNull(name, "Name must not be null!");
    Assert.notNull(pattern, "Pattern must not be null!");
    String patternStr = new String(pattern, StandardCharsets.UTF_8);
    execute(
        name,
        connection -> {
          boolean wasLocked = false;
          try {
            if (isLockingCacheWriter()) {
              doLock(name, connection);
              wasLocked = true;
            }
            deleteByScan(connection, patternStr);
          } finally {
            if (wasLocked && isLockingCacheWriter()) {
              doUnlock(name, connection);
            }
          }
          return "OK";
        });
  }

  /** SCAN COUNT 100 + batched DEL — never KEYS. */
  private static long deleteByScan(RedisConnection connection, String pattern) {
    if (pattern == null || pattern.isBlank() || "*".equals(pattern.trim())) {
      log.warn("[REDIS-SCAN] cache writer rejected unbounded pattern={}", pattern);
      return 0L;
    }
    long deleted = 0L;
    ScanOptions options =
        ScanOptions.scanOptions()
            .match(pattern)
            .count(RedisScanSupport.DEFAULT_SCAN_COUNT)
            .build();
    List<byte[]> batch = new ArrayList<>(RedisScanSupport.DEFAULT_DELETE_BATCH_SIZE);
    long scanned = 0L;
    try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
      while (cursor.hasNext()) {
        byte[] next = cursor.next();
        if (next == null) {
          continue;
        }
        batch.add(next);
        scanned++;
        if (batch.size() >= RedisScanSupport.DEFAULT_DELETE_BATCH_SIZE) {
          deleted += delBatch(connection, batch);
          batch.clear();
        }
        if (scanned >= RedisScanSupport.DEFAULT_SCAN_LIMIT) {
          log.warn(
              "[REDIS-SCAN] cache writer hit scan limit={} pattern={}",
              RedisScanSupport.DEFAULT_SCAN_LIMIT,
              pattern);
          break;
        }
      }
    } catch (Exception e) {
      log.warn("[REDIS-SCAN] cache writer scan failed pattern={}: {}", pattern, e.getMessage());
    }
    if (!batch.isEmpty()) {
      deleted += delBatch(connection, batch);
    }
    return deleted;
  }

  /**
   * Cluster-safe batch delete: one single-key {@code DEL} per key. A multi-key {@code DEL} over
   * arbitrary cache keys spans hash slots and is rejected by Redis Cluster ({@code CROSSSLOT}),
   * which would abort the whole eviction.
   */
  private static long delBatch(RedisConnection connection, List<byte[]> batch) {
    long total = 0L;
    for (byte[] key : batch) {
      if (key == null) {
        continue;
      }
      try {
        Long n = connection.del(key);
        if (n != null) {
          total += n;
        }
      } catch (Exception e) {
        log.warn("[REDIS-SCAN] cache writer per-key del failed: {}", e.getMessage());
      }
    }
    return total;
  }

  private Boolean doLock(String name, RedisConnection connection) {
    return connection.setNX(createCacheLockKey(name), new byte[0]);
  }

  private Long doUnlock(String name, RedisConnection connection) {
    return connection.del(createCacheLockKey(name));
  }

  private boolean doCheckLock(String name, RedisConnection connection) {
    Boolean exists = connection.exists(createCacheLockKey(name));
    return Boolean.TRUE.equals(exists);
  }

  private boolean isLockingCacheWriter() {
    return !sleepTime.isZero() && !sleepTime.isNegative();
  }

  private <T> T execute(String name, Function<RedisConnection, T> callback) {
    RedisConnection connection = connectionFactory.getConnection();
    try {
      checkAndPotentiallyWaitUntilUnlocked(name, connection);
      return callback.apply(connection);
    } finally {
      connection.close();
    }
  }

  private void checkAndPotentiallyWaitUntilUnlocked(String name, RedisConnection connection) {
    if (!isLockingCacheWriter()) {
      return;
    }
    try {
      while (doCheckLock(name, connection)) {
        Thread.sleep(sleepTime.toMillis());
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new PessimisticLockingFailureException(
          String.format("Interrupted while waiting to unlock cache %s", name), ex);
    }
  }

  private static boolean shouldExpireWithin(@Nullable Duration ttl) {
    return ttl != null && !ttl.isZero() && !ttl.isNegative();
  }

  private static byte[] createCacheLockKey(String name) {
    return (name + "~lock").getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public CacheStatistics getCacheStatistics(String cacheName) {
    return statistics.getCacheStatistics(cacheName);
  }

  @Override
  public void clearStatistics(String name) {
    // no-op (parity with upstream)
  }

  @Override
  public RedisCacheWriter withStatisticsCollector(
      CacheStatisticsCollector cacheStatisticsCollector) {
    return this;
  }
}
