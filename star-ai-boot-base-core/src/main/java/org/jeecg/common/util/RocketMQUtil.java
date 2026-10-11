package org.jeecg.common.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jeecg.boot.starter.lock.annotation.JLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.MessagingException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class RocketMQUtil {
  private static final Logger log = LoggerFactory.getLogger(RocketMQUtil.class);

  /** RocketMQ latency level → milliseconds (level 4 ≈ 30s, consistent with RemittanceProcessStateSync). */
  private static final long[] DELAY_LEVEL_MS = {
    0L,
    1_000L,
    5_000L,
    10_000L,
    30_000L,
    60_000L,
    120_000L,
    180_000L,
    240_000L,
    300_000L,
    360_000L,
    420_000L,
    480_000L,
    540_000L,
    600_000L,
    1_200_000L,
    1_800_000L,
    3_600_000L,
    7_200_000L
  };

  private static final int SCHEDULER_CORE_POOL_SIZE = 10;
  private static final ScheduledExecutorService DELAY_SCHEDULER;

  static {
    ScheduledThreadPoolExecutor scheduler =
        new ScheduledThreadPoolExecutor(
            SCHEDULER_CORE_POOL_SIZE,
            new ThreadFactory() {
              private final AtomicInteger counter = new AtomicInteger(0);

              @Override
              public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "mq-delay-" + counter.incrementAndGet());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
              }
            });
    scheduler.setKeepAliveTime(60L, TimeUnit.SECONDS);
    scheduler.allowCoreThreadTimeOut(true);
    scheduler.setRemoveOnCancelPolicy(true);
    scheduler.setRejectedExecutionHandler(new CallerRunsPolicy());
    DELAY_SCHEDULER = Executors.unconfigurableScheduledExecutorService(scheduler);
  }

  @Autowired @Lazy private RabbitTemplate rabbitTemplate;

  @Value("${jeecg.mq.send.max-attempts:10}")
  private int maxAttempts;

  @Value("${jeecg.mq.send.retry-delay-ms:2000}")
  private long retryDelayMs;

  @JLock(lockKey = {"RocketMQUtil", "#messageId"})
  public void send(String messageId, String destination, Object messageBody, Integer delayLevel)
      throws MessagingException {
    sendWithRetry(destination, messageBody, delayLevel);
  }

  public void send(String destination, Object messageBody, Integer delayLevel)
      throws MessagingException {
    sendWithRetry(destination, messageBody, delayLevel);
  }

  /** Asynchronous sending, built-in retry, does not block the caller thread. */
  @Async("mqSendExecutor")
  public void sendRetry(String destination, Object messageBody, Integer delayLevel) {
    try {
      sendWithRetry(destination, messageBody, delayLevel);
    } catch (RuntimeException e) {
      log.error(
          "[ALERT-CHOKE] Async MQ send failed. Destination: {}, Payload: {}"
              + " | This warning means EXCEPTION ALERTS WILL NOT BE DELIVERED via RabbitMQ."
              + " Fix spring.rabbitmq.username/password/vhost on the target cluster ASAP.",
          destination,
          safeMaskMessageBody(messageBody),
          e);
    }
  }

  private void sendWithRetry(String destination, Object messageBody, Integer delayLevel)
      throws MessagingException {
    MessagingException lastException = null;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        sendOnce(destination, messageBody, delayLevel);
        return;
      } catch (MessagingException e) {
        lastException = e;
        if (attempt < maxAttempts) {
          log.warn(
              "MQ send attempt {}/{} failed. Destination: {}, Error: {}",
              attempt,
              maxAttempts,
              destination);
          sleepBeforeRetry();
        }
      }
    }
    throw lastException;
  }

  private void sendOnce(String destination, Object messageBody, Integer delayLevel)
      throws MessagingException {
    log.info("Preparing to send message. Destination: {}, Payload: {}", destination, safeMaskMessageBody(messageBody));

    long delayMs = delayLevelToMs(delayLevel);
    if (delayMs > 0) {
      DELAY_SCHEDULER.schedule(
          () -> doSend(destination, messageBody), delayMs, TimeUnit.MILLISECONDS);
      log.info("Message scheduled with delay {}ms. Destination: {}", delayMs, destination);
      return;
    }

    doSend(destination, messageBody);
  }

  private void doSend(String destination, Object messageBody) {
    try {
      rabbitTemplate.convertAndSend("", destination, messageBody);
      log.info("Message sent successfully. Destination: {}", destination);
    } catch (Exception e) {
      log.error(
          "Exception while sending message. Destination: {}, Error: {}",
          destination,
          e);
      throw new MessagingException("Exception occurred while sending message", e);
    }
  }

  private static long delayLevelToMs(Integer delayLevel) {
    if (delayLevel == null || delayLevel <= 0) {
      return 0L;
    }
    if (delayLevel < DELAY_LEVEL_MS.length) {
      return DELAY_LEVEL_MS[delayLevel];
    }
    return DELAY_LEVEL_MS[DELAY_LEVEL_MS.length - 1];
  }

  private void sleepBeforeRetry() throws MessagingException {
    try {
      Thread.sleep(retryDelayMs);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      throw new MessagingException("MQ send retry interrupted", ie);
    }
  }

  private static final String[] OTP_SENSITIVE_KEYS = {
    "captcha", "otp", "verificationCode", "smsCode", "emailCode", "smscode", "checkCode", "code"
  };
  private static final String OTP_MASK = "******";

  private static Object safeMaskMessageBody(Object body) {
    if (body == null) {
      return null;
    }
    try {
      if (body instanceof String) {
        String s = (String) body;
        if (s.length() <= 2) {
          return s;
        }
        Object parsed;
        try {
          parsed = com.alibaba.fastjson.JSON.parse(s);
        } catch (Exception e) {
          return s;
        }
        Object masked = safeMaskRecursive(parsed);
        return (masked instanceof String) ? (String) masked : com.alibaba.fastjson.JSON.toJSONString(masked);
      }
      return com.alibaba.fastjson.JSON.toJSONString(safeMaskRecursive(body));
    } catch (Exception any) {
      return "<MASKED-EXCEPTION:" + any.getClass().getSimpleName() + ">";
    }
  }

  private static Object safeMaskRecursive(Object obj) {
    if (obj == null) {
      return null;
    }
    if (obj instanceof java.util.Map) {
      java.util.Map<?, ?> m = (java.util.Map<?, ?>) obj;
      com.alibaba.fastjson.JSONObject out = new com.alibaba.fastjson.JSONObject(m.size());
      for (java.util.Map.Entry<?, ?> e : m.entrySet()) {
        String k = String.valueOf(e.getKey());
        String kLower = k.toLowerCase();
        boolean hit = false;
        for (String tk : OTP_SENSITIVE_KEYS) {
          if (kLower.contains(tk.toLowerCase())) {
            hit = true;
            break;
          }
        }
        if (hit && e.getValue() != null) {
          String sv = String.valueOf(e.getValue());
          if (sv.length() <= 12) {
            out.put(k, OTP_MASK);
          } else {
            out.put(k, sv.substring(0, 2) + OTP_MASK + sv.substring(sv.length() - 2));
          }
        } else {
          out.put(k, safeMaskRecursive(e.getValue()));
        }
      }
      return out;
    }
    if (obj instanceof java.util.List) {
      java.util.List<?> l = (java.util.List<?>) obj;
      java.util.List<Object> out = new java.util.ArrayList<>(l.size());
      for (Object o : l) {
        out.add(safeMaskRecursive(o));
      }
      return out;
    }
    if (obj instanceof com.alibaba.fastjson.JSONObject) {
      return safeMaskRecursive(((com.alibaba.fastjson.JSONObject) obj).getInnerMap());
    }
    return obj;
  }
}
