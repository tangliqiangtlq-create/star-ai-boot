package org.jeecg.common.aspect;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.mq.RocketMQMessageListener;
import org.jeecg.common.util.SecureHashUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.jeecg.common.util.RocketMQUtil;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class RocketMqConsumeExceptionMailAspect {

  private static final String EXCEPTION_MAIL_TOPIC = "sendEmailTopic_topic";
  private static final String DEDUP_KEY_PREFIX = "mq:exception:dedup:";

  private final RocketMQUtil rocketMQUtil;
  private final String activeProfile;
  private final String appName;
  private final boolean enabled;
  private final int everyN;
  private final int windowSeconds;

  @Lazy
  @Autowired(required = false)
  private StringRedisTemplate stringRedisTemplate;

  public RocketMqConsumeExceptionMailAspect(
      RocketMQUtil rocketMQUtil,
      @Value("${spring.profiles.active:}") String activeProfile,
      @Value("${spring.application.name:unknown}") String appName,
      @Value("${jeecg.mq.exception.mail.enabled:false}") boolean enabled,
      @Value("${jeecg.mq.exception.mail.every-n:10}") int everyN,
      @Value("${jeecg.mq.exception.mail.window-seconds:300}") int windowSeconds) {
    this.rocketMQUtil = rocketMQUtil;
    this.activeProfile = activeProfile;
    this.appName = appName;
    this.enabled = enabled;
    this.everyN = everyN <= 0 ? 10 : everyN;
    this.windowSeconds = windowSeconds <= 0 ? 300 : windowSeconds;
  }

  @Around("execution(* org.jeecg.common.mq.RocketMQListener+.onMessage(..))")
  public Object aroundOnMessage(ProceedingJoinPoint joinPoint) throws Throwable {
    try {
      return joinPoint.proceed();
    } catch (Throwable throwable) {
      try {
        handleThrowable(joinPoint, throwable);
      } catch (Exception ignored) {
      }
      throw throwable;
    }
  }

  private void handleThrowable(ProceedingJoinPoint joinPoint, Throwable throwable) {
    if (!enabled && !StrUtil.equals(activeProfile, "prod")) {
      return;
    }

    Class<?> targetClass = AopUtils.getTargetClass(joinPoint.getTarget());
    RocketMQMessageListener listenerAnn = targetClass.getAnnotation(RocketMQMessageListener.class);
    if (listenerAnn == null) {
      return;
    }

    String topic = listenerAnn.topic();
    if (StrUtil.equals(topic, EXCEPTION_MAIL_TOPIC)) {
      return;
    }

    long count = acquireDedupCountOrOne(topic, throwable);
    ThrottleLevel level = resolveLevel(count);
    if (count >= 10L) { log.error("ALERT_DLQ_DEPTH topic={} failCount={} everyN={} window={}s", topic, count, everyN, windowSeconds); }

    if (ThrottleLevel.LOG_ONLY.equals(level)) {
      log.warn(
          "[MQ-EX-THROTTLE] suppress full mail, count={} level=LOG topic={} ex={} app={}",
          count,
          topic,
          throwable.getClass().getName(),
          appName);
      return;
    }

    Object[] args = joinPoint.getArgs();
    Object messageArg = (args == null || args.length == 0) ? null : args[0];
    String messageText = String.valueOf(messageArg);
    if (messageText.length() > 8000) {
      messageText = messageText.substring(0, 8000) + "...(truncated)";
    }

    String stackTrace = "";
    if (ThrottleLevel.FULL.equals(level)) {
      StringWriter stringWriter = new StringWriter();
      stringWriter.write(org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace(throwable));
      stackTrace = stringWriter.toString();
      if (stackTrace.length() > 20000) {
        stackTrace = stackTrace.substring(0, 20000) + "...(truncated)";
      }
    } else {
      stackTrace = "[SUMMARY MODE] Throwable: " + throwable + "; message: " + throwable.getMessage();
    }

    String exceptionDetails =
        StrUtil.format(
            "MQ consumption exception [{} count={} everyN={} window={}s]\nappName={}\nprofile={}\ntopic={}\nconsumerGroup={}\nlistener={}\nmessage={}\n\n{}",
            level,
            count,
            everyN,
            windowSeconds,
            appName,
            activeProfile,
            topic,
            listenerAnn.consumerGroup(),
            targetClass.getName(),
            ThrottleLevel.FULL.equals(level) ? messageText : "(summary: suppressed)",
            stackTrace);

    rocketMQUtil.sendRetry(
        EXCEPTION_MAIL_TOPIC,
        new JSONObject().set("emailScene", "EXCEPTION").set("exceptionDetails", exceptionDetails),
        null);
  }

  private long acquireDedupCountOrOne(String topic, Throwable throwable) {
    if (stringRedisTemplate == null) {
      return 1L;
    }
    try {
      String raw = topic + "|" + throwable.getClass().getName() + "|" + appName;
      String hash = SecureHashUtil.sha256Encode(raw, "UTF-8");
      String dedupKey = DEDUP_KEY_PREFIX + hash;
      Long count = stringRedisTemplate.opsForValue().increment(dedupKey);
      if (count == null) {
        return 1L;
      }
      if (count == 1L) {
        stringRedisTemplate.expire(dedupKey, windowSeconds, TimeUnit.SECONDS);
      }
      return count;
    } catch (Exception ex) {
      log.warn("[MQ-EX-THROTTLE] dedup redis fail, fallback count=1", ex);
      return 1L;
    }
  }

  private ThrottleLevel resolveLevel(long count) {
    if (count <= 1L) {
      return ThrottleLevel.FULL;
    }
    if (count == 2L || count == 3L || count % everyN == 0L) {
      return ThrottleLevel.SUMMARY;
    }
    return ThrottleLevel.LOG_ONLY;
  }

  enum ThrottleLevel {
    FULL,
    SUMMARY,
    LOG_ONLY
  }
}
