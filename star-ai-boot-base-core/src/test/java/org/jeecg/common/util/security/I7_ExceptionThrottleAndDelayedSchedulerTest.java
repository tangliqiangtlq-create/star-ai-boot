package org.jeecg.common.util.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jeecg.common.util.SecureHashUtil;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class I7_ExceptionThrottleAndDelayedSchedulerTest {

  /**
   * Mirrors I7 throttle counting: count=1 → FULL stack; count=2/3 or count%10 milestone → SUMMARY;
   * other counts → log.warn only (no email). In-memory mock matches the same semantics without Redis.
   */
  static class InMemoryThrottle {
    private final java.util.Map<String, AtomicInteger> counts = new java.util.concurrent.ConcurrentHashMap<>();
    private final int windowSeconds;
    final AtomicInteger fullMailsSent = new AtomicInteger(0);
    final AtomicInteger summaryMailsSent = new AtomicInteger(0);

    InMemoryThrottle(int windowSeconds) {
      this.windowSeconds = windowSeconds;
    }

    String onException(String topic, Class<?> throwableCls, String appName, long nowMs) {
      String key =
          SecureHashUtil.sha256Encode(topic + "|" + throwableCls.getName() + "|" + appName, "UTF-8");
      int count = counts.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
      if (count == 1) {
        fullMailsSent.incrementAndGet();
        return "FULL";
      }
      if (count == 2 || count == 3 || count % 10 == 0) {
        summaryMailsSent.incrementAndGet();
        return "SUMMARY";
      }
      return "LOG_ONLY";
    }
  }

  @Test
  void throttle_count1_full_count2_count3_summary_milestone10_summary() {
    InMemoryThrottle throttle = new InMemoryThrottle(300);
    for (int i = 1; i <= 12; i++) {
      String r = throttle.onException("sendEmailTopic", NullPointerException.class, "system-staging", 0);
      if (i == 1) assertEquals("FULL", r, "first count=1");
      else if (i == 2 || i == 3 || i == 10) assertEquals("SUMMARY", r, "i=" + i);
      else assertEquals("LOG_ONLY", r, "i=" + i);
    }
    assertEquals(1, throttle.fullMailsSent.get());
    assertEquals(3, throttle.summaryMailsSent.get(), "2/3/10 → 3 summary emails within 5min window");
  }

  @Test
  void throttle_distinctTopics_countIndependently() {
    InMemoryThrottle a = new InMemoryThrottle(300);
    a.onException("T1", NullPointerException.class, "A", 0);
    a.onException("T2", IllegalStateException.class, "A", 0);
    a.onException("T1", NullPointerException.class, "A", 0);
    assertEquals(2, a.fullMailsSent.get());
    assertEquals(1, a.summaryMailsSent.get());
  }

  @Test
  void delayLevel_toMs_valuesMatchSpec() {
    assertEquals(TimeUnit.SECONDS.toMillis(10), DelayedMessageScheduler.DelayLevel.D10S.toMs());
    assertEquals(TimeUnit.SECONDS.toMillis(30), DelayedMessageScheduler.DelayLevel.D30S.toMs());
    assertEquals(TimeUnit.MINUTES.toMillis(1), DelayedMessageScheduler.DelayLevel.D1M.toMs());
    assertEquals(TimeUnit.MINUTES.toMillis(2 * 60), DelayedMessageScheduler.DelayLevel.D2H.toMs());
  }

  @Test
  void extractRouting_parsesDelayerKeyPrefixAndSuffix() {
    assertEquals("sendEmail.retry", DelayedMessageScheduler.extractRouting("delayer:sendEmail.retry:abcd-1234"));
    assertEquals("", DelayedMessageScheduler.extractRouting("delayer:badFormatNoColonsAfter"));
    assertEquals("", DelayedMessageScheduler.extractRouting("not_delayer_prefix"));
  }

  @Test
  void delayedScheduler_pluginField_lazyInitiallyFalse() {
    DelayedMessageScheduler s = new DelayedMessageScheduler();
    assertFalse(s.isDelayedPluginAvailable(), "plugin available default false before @PostConstruct runs");
    assertNotNull(DelayedMessageScheduler.FALLBACK_REDIS_PREFIX);
  }
}
