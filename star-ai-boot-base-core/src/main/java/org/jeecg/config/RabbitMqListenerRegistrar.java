package org.jeecg.config;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.mq.RocketMQListener;
import org.jeecg.common.mq.RocketMQMessageListener;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Registers {@link RocketMQMessageListener} + {@link RocketMQListener} as RabbitMQ consumers;
 * business Listener classes do not need to switch to {@code @RabbitListener}.
 *
 * <p>Hardened parameters (retry + DLQ) are inherited from {@link RabbitMQConfig#rabbitListenerContainerFactory}
 * so that all 14 business listeners share the same 3-attempt exponential backoff and route to
 * {@link RabbitMQConfig#DLX_EXCHANGE} / {@link RabbitMQConfig#DLQ_QUEUE} on terminal failure.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "spring.rabbitmq.host")
public class RabbitMqListenerRegistrar implements ApplicationListener<ApplicationReadyEvent> {

  private final ConnectionFactory connectionFactory;
  private final SimpleRabbitListenerContainerFactory hardenedFactory;
  private final MessageConverter rabbitMessageConverter;

  private final List<SimpleMessageListenerContainer> containers = new CopyOnWriteArrayList<>();

  private volatile boolean registered;

  public RabbitMqListenerRegistrar(
      ConnectionFactory connectionFactory,
      @Qualifier("rabbitListenerContainerFactory")
          SimpleRabbitListenerContainerFactory hardenedFactory,
      @Qualifier("rabbitMessageConverter") MessageConverter rabbitMessageConverter) {
    this.connectionFactory = connectionFactory;
    this.hardenedFactory = hardenedFactory;
    this.rabbitMessageConverter = rabbitMessageConverter;
  }

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    if (registered) {
      return;
    }
    registered = true;

    Map<String, Object> beans =
        event.getApplicationContext().getBeansWithAnnotation(RocketMQMessageListener.class);
    if (beans.isEmpty()) {
      log.info("RabbitMQ: no @RocketMQMessageListener beans in this service");
      return;
    }

    log.info("RabbitMQ: registering {} listener(s)", beans.size());
    for (Object bean : beans.values()) {
      registerListener(bean);
    }
  }

  @EventListener(ContextClosedEvent.class)
  public void onShutdown() {
    for (SimpleMessageListenerContainer container : containers) {
      try {
        if (container.isRunning()) {
          container.stop();
        }
      } catch (Exception e) {
        log.debug("RabbitMQ listener stop ignored: {}");
      }
    }
    containers.clear();
  }

  public List<SimpleMessageListenerContainer> getContainers() {
    return containers;
  }

  private void registerListener(Object bean) {
    Class<?> targetClass = AopUtils.getTargetClass(bean);
    RocketMQMessageListener annotation = targetClass.getAnnotation(RocketMQMessageListener.class);
    if (annotation == null || !(bean instanceof RocketMQListener<?> listener)) {
      return;
    }

    String queueName = annotation.topic().trim();
    if (StrUtil.isBlank(queueName)) {
      log.warn("Skip RabbitMQ listener with blank topic: {}", targetClass.getName());
      return;
    }

    Class<?> payloadType = resolvePayloadType(targetClass);

    SimpleMessageListenerContainer container = hardenedFactory.createListenerContainer();
    container.setQueueNames(queueName);
    container.setAcknowledgeMode(AcknowledgeMode.AUTO);

    container.setDefaultRequeueRejected(false);

    container.setMessageListener(
        (org.springframework.amqp.core.MessageListener)
            message -> {
              Object payload = deserialize(message, payloadType);
              invokeListener(listener, payload);
            });

    int concurrentConsumers = readIntField(hardenedFactory, "concurrentConsumers", 2);
    int maxConcurrentConsumers = readIntField(hardenedFactory, "maxConcurrentConsumers", 8);
    int prefetchCount = readIntField(hardenedFactory, "prefetchCount", 50);
    boolean adviceChainAttached =
        readObjField(hardenedFactory, "adviceChain", Object[].class) != null
            || readObjField(hardenedFactory, "retryInterceptor", Object.class) != null
            || readIntField(container, "adviceChainLength", 0) > 0;

    try {
      container.start();
      containers.add(container);
      log.info(
          "RabbitMQ listener started (hardened factory): queue={}, group={}, listener={},"
              + " concurrentConsumers={}, maxConcurrentConsumers={}, prefetchCount={},"
              + " defaultRequeueRejected=false, retryAdviceAttached={}, running={}",
          queueName,
          annotation.consumerGroup(),
          targetClass.getName(),
          concurrentConsumers,
          maxConcurrentConsumers,
          prefetchCount,
          adviceChainAttached,
          container.isRunning());
    } catch (Exception e) {
      log.error(
          "RabbitMQ listener start failed: queue={}, listener={}",
          queueName,
          targetClass.getName(),
          e);
    }
  }

  private static int readIntField(Object target, String name, int fallback) {
    Class<?> c = target.getClass();
    while (c != null && c != Object.class) {
      try {
        java.lang.reflect.Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        Object v = f.get(target);
        if (v instanceof Integer i) return i;
      } catch (NoSuchFieldException ignored) {
        c = c.getSuperclass();
      } catch (Throwable t) {
        return fallback;
      }
    }
    return fallback;
  }

  private static <T> T readObjField(Object target, String name, Class<T> type) {
    Class<?> c = target.getClass();
    while (c != null && c != Object.class) {
      try {
        java.lang.reflect.Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        Object v = f.get(target);
        if (v != null && type.isInstance(v)) return type.cast(v);
      } catch (NoSuchFieldException ignored) {
        c = c.getSuperclass();
      } catch (Throwable t) {
        return null;
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static void invokeListener(RocketMQListener<?> listener, Object payload) {
    ((RocketMQListener<Object>) listener).onMessage(payload);
  }

  private static Object deserialize(Message message, Class<?> payloadType) {
    byte[] body = message.getBody();
    if (body == null || body.length == 0) {
      return null;
    }
    String text = new String(body, StandardCharsets.UTF_8);
    if (JSONObject.class.equals(payloadType)) {
      return JSONUtil.parseObj(text);
    }
    if (String.class.equals(payloadType)) {
      return text;
    }
    return JSONUtil.toBean(text, payloadType);
  }

  private static Class<?> resolvePayloadType(Class<?> listenerClass) {
    for (Type type : listenerClass.getGenericInterfaces()) {
      if (type instanceof ParameterizedType parameterizedType
          && RocketMQListener.class.equals(parameterizedType.getRawType())) {
        Type arg = parameterizedType.getActualTypeArguments()[0];
        if (arg instanceof Class<?> clazz) {
          return clazz;
        }
      }
    }
    return JSONObject.class;
  }
}
