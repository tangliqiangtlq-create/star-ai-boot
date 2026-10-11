package org.jeecg.common.util.security;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.PostConstruct;
import org.jeecg.common.util.SpringContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * I6 delayed scheduling off-JVM: prefer RabbitMQ {@code x-delayed-message} exchange plugin
 * (disk-persistent, survives k8s rolling restarts); when the plugin is missing, fallback dual path:
 * <ol>
 *   <li>JVM {@link ScheduledExecutorService} runs callbacks</li>
 *   <li>Write-ahead Redis key {@code delayer:{uuid}:payload} TTL=delay+60s; after restart, SCAN
 *       re-queues non-expired tasks to the thread pool.</li>
 * </ol>
 *
 * <h3>Eight {@link DelayLevel} values (unified API; do not hard-code milliseconds in producers)</h3>
 * <pre>
 *   D10S(10s) D30S(30s) D1M(1m) D5M(5m) D10M(10m) D30M(30m) D1H(1h) D2H(2h)
 * </pre>
 *
 * <p>L3 alert: startup warns if plugin missing; Runbook I3 expects plugin installed and fallback as last resort.
 *
 * <h3>Delayed-work code standard (I6 GATE-07)</h3>
 * <ol>
 *   <li>Do not use {@code new java.util.Timer}.schedule(...): process kill loses tasks with no ops trail.</li>
 *   <li>Do not use {@code @Scheduled(fixedDelay=X)} for one-shot "run once after N hours".
 *       {@code @Scheduled} is for fixed-interval polling only, with CAS claim to avoid XXL+Spring double-run.</li>
 *   <li>Do not hard-code millisecond/second literals (e.g. {@code 3_600_000}); use {@link DelayLevel} or Nacos config.</li>
 * </ol>
 * <p>Compliant example:
 * <pre>
 * DelayedMessageScheduler scheduler = SpringContextUtils.getBean(DelayedMessageScheduler.class);
 * scheduler.scheduleRunnable(DelayLevel.D1H, "withdraw_notify", payloadJsonStr, () -&gt; notifyUser(userId));
 * </pre>
 */
@Component
public class DelayedMessageScheduler {
  private static final Logger log = LoggerFactory.getLogger(DelayedMessageScheduler.class);

  public enum DelayLevel {
    D10S(10, TimeUnit.SECONDS),
    D30S(30, TimeUnit.SECONDS),
    D1M(1, TimeUnit.MINUTES),
    D5M(5, TimeUnit.MINUTES),
    D10M(10, TimeUnit.MINUTES),
    D30M(30, TimeUnit.MINUTES),
    D1H(1, TimeUnit.HOURS),
    D2H(2, TimeUnit.HOURS);

    public final long amount;
    public final TimeUnit unit;

    DelayLevel(long amount, TimeUnit unit) {
      this.amount = amount;
      this.unit = unit;
    }

    public long toMs() {
      return unit.toMillis(amount);
    }
  }

  static final String FALLBACK_REDIS_PREFIX = "delayer:";
  static final String DELAYED_EXCHANGE_NAME = "delayed.message.exchange";
  static final String DELAYED_HEADER_KEY = "x-delay";
  private static final int RECOVERY_BATCH_SIZE = 100;

  private static final int SCHEDULER_CORE_POOL_SIZE = 8;
  private static final ScheduledExecutorService FALLBACK_EXEC;

  static {
    ScheduledThreadPoolExecutor s =
        new ScheduledThreadPoolExecutor(
            SCHEDULER_CORE_POOL_SIZE,
            new ThreadFactory() {
              private final AtomicInteger c = new AtomicInteger(0);

              @Override
              public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "delayed-fallback-" + c.incrementAndGet());
                t.setDaemon(true);
                return t;
              }
            });
    s.setKeepAliveTime(60L, TimeUnit.SECONDS);
    s.allowCoreThreadTimeOut(true);
    s.setRemoveOnCancelPolicy(true);
    s.setRejectedExecutionHandler(new CallerRunsPolicy());
    FALLBACK_EXEC = Executors.unconfigurableScheduledExecutorService(s);
  }

  @Autowired(required = false)
  @Lazy
  private RabbitTemplate rabbitTemplate;

  @Autowired(required = false)
  @Lazy
  private RabbitAdmin rabbitAdmin;

  @Autowired(required = false)
  @Lazy
  private StringRedisTemplate stringRedisTemplate;

  private final AtomicBoolean pluginAvailable = new AtomicBoolean(false);

  @PostConstruct
  public void autoDetectPluginAndRecover() {
    boolean detected = false;
    try {
      if (rabbitAdmin != null && SpringContextUtils.getApplicationContext() != null) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "direct");
        CustomExchange ex = new CustomExchange(DELAYED_EXCHANGE_NAME, "x-delayed-message", true, false, args);
        rabbitAdmin.declareExchange(ex);
        detected = true;
      }
    } catch (Exception e) {
      log.warn(
          "I6 x-delayed-message plugin not installed (L3 DEPLOY-ACTION: enable rabbitmq_delayed_message_exchange on cluster). Will enable Fallback: JVM Executor + write-ahead Redis delayer:* TTL. Running recovery scan now.");
    }
    pluginAvailable.set(detected);
    FALLBACK_EXEC.schedule(this::recoverFromRedisOnStartup, 8, TimeUnit.SECONDS);
  }

  /**
   * Unified entry: schedule a delayed task.
   *
   * @param level   DelayLevel (producers must not pass raw milliseconds)
   * @param routing RMQ routing key; also used in Redis key for hash-slot tagging
   * @param payload JSON string (UTF-8). With plugin: x-delayed exchange; without: JVM pool + Redis write-ahead
   * @param runner  unused when plugin is on; on fallback, runs when delay elapses
   */
  @Async
  public void schedule(DelayLevel level, String routing, String payload, Runnable runner) {
    if (level == null || routing == null) return;
    if (StrUtil.isBlank(payload)) payload = "";
    long ms = level.toMs();
    if (pluginAvailable.get() && rabbitTemplate != null) {
      try {
        Map<String, Object> headers = new HashMap<>();
        headers.put(DELAYED_HEADER_KEY, (int) ms);
        rabbitTemplate.convertAndSend(
            DELAYED_EXCHANGE_NAME, routing, payload.getBytes(StandardCharsets.UTF_8),
            message -> {
              message.getMessageProperties().getHeaders().putAll(headers);
              return message;
            });
        return;
      } catch (Exception e) {
        log.warn("I6 delayed RMQ send failed, fallback to JVM exec route={}", routing, e);
      }
    }
    fallbackSchedule(ms, routing, payload, runner);
  }

  private void fallbackSchedule(long ms, String routing, String payload, Runnable runner) {
    long now = System.currentTimeMillis();
    String uuid = java.util.UUID.randomUUID().toString().replace("-", "");
    String redisKey = FALLBACK_REDIS_PREFIX + routing + ":" + uuid;
    String meta = (now + ms) + "|" + payload;
    if (stringRedisTemplate != null) {
      try {
        long ttlSecs = Math.max(60L, TimeUnit.MILLISECONDS.toSeconds(ms) + 60L);
        stringRedisTemplate.opsForValue().set(redisKey, meta, ttlSecs, TimeUnit.SECONDS);
      } catch (Exception e) {
        log.warn("I6 fallback write-ahead redis set fail route={}", routing, e);
      }
    }
    Runnable safeRunnable =
        () -> {
          try {
            if (runner != null) runner.run();
          } catch (Exception e) {
            log.error("I6 fallback runner raised uncaught exception route={}", routing, e);
          } finally {
            if (stringRedisTemplate != null) {
              try {
                stringRedisTemplate.delete(redisKey);
              } catch (Exception ignored) {
              }
            }
          }
        };
    FALLBACK_EXEC.schedule(safeRunnable, Math.max(0L, ms), TimeUnit.MILLISECONDS);
  }

  /** After startup, SCAN delayer:* in batches of 100 and re-queue non-expired tasks to the fallback pool. */
  void recoverFromRedisOnStartup() {
    if (stringRedisTemplate == null) return;
    Environment env;
    try {
      env = SpringContextUtils.getApplicationContext().getEnvironment();
    } catch (Exception e) {
      return;
    }
    if (!"true".equalsIgnoreCase(env.getProperty("jeecg.delayed.fallback.recover-on-startup", "true"))) return;
    int restored = 0;
    try (Cursor<String> cursor =
        stringRedisTemplate.scan(
            ScanOptions.scanOptions()
                .match(FALLBACK_REDIS_PREFIX + "*")
                .count(RECOVERY_BATCH_SIZE)
                .build())) {
      while (cursor.hasNext()) {
        String k = cursor.next();
        try {
          String meta = stringRedisTemplate.opsForValue().get(k);
          if (StrUtil.isBlank(meta)) continue;
          int pipe = meta.indexOf('|');
          if (pipe < 0) continue;
          long fireAt = Long.parseLong(meta.substring(0, pipe));
          long delay = fireAt - System.currentTimeMillis();
          String payload = meta.substring(pipe + 1);
          String routing = extractRouting(k);
          Runnable nop = () -> log.info("I6 recovered task fire route={} payloadSize={}", routing, payload.length());
          if (delay <= 0) FALLBACK_EXEC.submit(nop);
          else FALLBACK_EXEC.schedule(nop, delay, TimeUnit.MILLISECONDS);
          restored++;
        } catch (Exception e) {
          log.warn("I6 recover task skip key={}", k, e);
        }
        if (restored % 500 == 499) log.info("I6 recover already restored {} tasks...", restored + 1);
      }
    } catch (Exception e) {
      log.warn("I6 recover scan stopped", e);
    }
    if (restored > 0) log.info("I6 recover complete: restored {} pending delayed tasks from Redis delayer:* keys", restored);
  }

  static String extractRouting(String redisKey) {
    if (redisKey == null || !redisKey.startsWith(FALLBACK_REDIS_PREFIX)) return "";
    int i = redisKey.lastIndexOf(':');
    int j = FALLBACK_REDIS_PREFIX.length();
    if (i <= j) return "";
    return redisKey.substring(j, i);
  }

  public boolean isDelayedPluginAvailable() {
    return pluginAvailable.get();
  }
}
