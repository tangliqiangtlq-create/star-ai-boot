package org.jeecg.config;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Consumer health check and auto-recovery: attempt restart when a Listener is offline (running=false).
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "spring.rabbitmq.host")
public class RabbitMqConsumerMonitor {

  private final RabbitMqListenerRegistrar listenerRegistrar;

  @Value("${spring.rabbitmq.host}")
  private String host;

  @Value("${jeecg.mq.consumer.auto-recover:true}")
  private boolean autoRecover;

  public RabbitMqConsumerMonitor(RabbitMqListenerRegistrar listenerRegistrar) {
    this.listenerRegistrar = listenerRegistrar;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    log.info("RabbitMQ host={}", host);
    List<SimpleMessageListenerContainer> containers = listenerRegistrar.getContainers();
    if (containers.isEmpty()) {
      log.info("RabbitMQ: no local consumers in this service (producer-only or no listener)");
      return;
    }

    log.info("RabbitMQ: {} local consumer(s):", containers.size());
    for (SimpleMessageListenerContainer container : containers) {
      logConsumerState(container);
      recoverIfNeeded(container);
    }
  }

  @Scheduled(fixedDelayString = "${jeecg.mq.consumer.watchdog-interval-ms:30000}")
  public void watchConsumers() {
    for (SimpleMessageListenerContainer container : listenerRegistrar.getContainers()) {
      if (!container.isRunning()) {
        log.error("RabbitMQ consumer NOT running: {}", describe(container));
        recoverIfNeeded(container);
      }
    }
  }

  private void recoverIfNeeded(SimpleMessageListenerContainer container) {
    if (!autoRecover || container.isRunning()) {
      return;
    }
    try {
      log.warn("RabbitMQ consumer recovering: {}", describe(container));
      safeStop(container);
      container.start();
      log.info(
          "RabbitMQ consumer recovered: {}, running={}",
          describe(container),
          container.isRunning());
    } catch (Exception e) {
      log.error("RabbitMQ consumer recovery failed: {}", describe(container), e);
    }
  }

  private void safeStop(SimpleMessageListenerContainer container) {
    try {
      if (container.isRunning()) {
        container.stop();
      }
    } catch (Exception e) {
      log.warn("RabbitMQ consumer stop before recover ignored: {}", describe(container), e);
    }
  }

  private void logConsumerState(SimpleMessageListenerContainer container) {
    log.info(
        "  - queues={}, running={}",
        String.join(",", container.getQueueNames()),
        container.isRunning());
  }

  private static String describe(SimpleMessageListenerContainer container) {
    return String.format(
        "queues=%s, running=%s",
        String.join(",", container.getQueueNames()), container.isRunning());
  }
}
