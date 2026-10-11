package org.jeecg.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisKeyCommands;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

class RedisScanSupportTest {

  @Test
  void scanKeys_nullOrEmpty_returnsEmpty() {
    assertTrue(RedisScanSupport.scanKeys(null, "a*").isEmpty());
    @SuppressWarnings("unchecked")
    RedisTemplate<String, Object> tpl = mock(RedisTemplate.class);
    assertTrue(RedisScanSupport.scanKeys(tpl, null).isEmpty());
    assertTrue(RedisScanSupport.scanKeys(tpl, "").isEmpty());
  }

  @Test
  void scanKeys_rejectsUnboundedStar() {
    @SuppressWarnings("unchecked")
    RedisTemplate<String, Object> tpl = mock(RedisTemplate.class);
    assertTrue(RedisScanSupport.scanKeys(tpl, "*").isEmpty());
  }

  @Test
  void defaultScanCountIs100() {
    assertEquals(100, RedisScanSupport.DEFAULT_SCAN_COUNT);
  }

  @Test
  void deleteByPattern_rejectsBlankShortAndStar() {
    @SuppressWarnings("unchecked")
    RedisTemplate<String, Object> tpl = mock(RedisTemplate.class);
    assertEquals(0L, RedisScanSupport.deleteByPattern(tpl, null));
    assertEquals(0L, RedisScanSupport.deleteByPattern(tpl, " "));
    assertEquals(0L, RedisScanSupport.deleteByPattern(tpl, "a"));
    assertEquals(0L, RedisScanSupport.deleteByPattern(tpl, "*"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void scanKeys_abortsAtLimit() {
    RedisTemplate<String, Object> tpl = mock(RedisTemplate.class);
    RedisConnection connection = mock(RedisConnection.class);
    RedisKeyCommands keyCommands = mock(RedisKeyCommands.class);
    when(connection.getNativeConnection()).thenReturn(connection);
    when(connection.keyCommands()).thenReturn(keyCommands);

    List<byte[]> fake = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      fake.add(("k" + i).getBytes(StandardCharsets.UTF_8));
    }
    Cursor<byte[]> cursor = cursorOf(fake);
    when(keyCommands.scan(any(ScanOptions.class))).thenReturn(cursor);

    when(tpl.execute(any(RedisCallback.class)))
        .thenAnswer(
            inv -> {
              RedisCallback<?> cb = inv.getArgument(0);
              return cb.doInRedis(connection);
            });

    Set<String> keys = RedisScanSupport.scanKeys(tpl, "k*", 100, 3L);
    assertEquals(3, keys.size());
  }

  @Test
  @SuppressWarnings("unchecked")
  void deleteByPattern_batchesDelete() {
    RedisTemplate<String, Object> tpl = mock(RedisTemplate.class);
    RedisConnection connection = mock(RedisConnection.class);
    RedisKeyCommands keyCommands = mock(RedisKeyCommands.class);
    when(connection.getNativeConnection()).thenReturn(connection);
    when(connection.keyCommands()).thenReturn(keyCommands);

    List<byte[]> fake = List.of("ab:1".getBytes(StandardCharsets.UTF_8), "ab:2".getBytes(StandardCharsets.UTF_8));
    when(keyCommands.scan(any(ScanOptions.class))).thenReturn(cursorOf(fake));
    when(tpl.execute(any(RedisCallback.class)))
        .thenAnswer(
            inv -> {
              RedisCallback<?> cb = inv.getArgument(0);
              return cb.doInRedis(connection);
            });
    when(tpl.delete(anyString())).thenReturn(true);

    assertEquals(2L, RedisScanSupport.deleteByPattern(tpl, "ab"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void deleteEach_issuesOneSingleKeyDelPerKey() {
    RedisTemplate<String, Object> tpl = mock(RedisTemplate.class);
    when(tpl.delete(anyString())).thenReturn(true);

    // Cluster-safe contract: never a multi-key DEL, always one single-key DEL per key.
    assertEquals(3L, RedisScanSupport.deleteEach(tpl, List.of("a", "b", "c")));
    verify(tpl, times(3)).delete(anyString());
  }

  private static Cursor<byte[]> cursorOf(List<byte[]> values) {
    Iterator<byte[]> it = values.iterator();
    return new Cursor<byte[]>() {
      @Override
      public boolean hasNext() {
        return it.hasNext();
      }

      @Override
      public byte[] next() {
        return it.next();
      }

      @Override
      public void close() {}

      @Override
      public Cursor.CursorId getId() {
        return Cursor.CursorId.initial();
      }

      @Override
      public long getCursorId() {
        return 0L;
      }

      @Override
      public boolean isClosed() {
        return !it.hasNext();
      }

      @Override
      public long getPosition() {
        return 0;
      }
    };
  }
}
