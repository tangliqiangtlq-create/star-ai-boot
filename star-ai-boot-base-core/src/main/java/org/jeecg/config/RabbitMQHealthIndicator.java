package org.jeecg.config;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(name = "spring.rabbitmq.host")
public class RabbitMQHealthIndicator implements HealthIndicator {

  private final org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory;
  private final RabbitMqListenerRegistrar listenerRegistrar;

  @Value("${spring.rabbitmq.host}")
  private String host;

  @Value("${spring.rabbitmq.port:5672}")
  private int port;

  public RabbitMQHealthIndicator(
      org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory,
      RabbitMqListenerRegistrar listenerRegistrar) {
    this.connectionFactory = connectionFactory;
    this.listenerRegistrar = listenerRegistrar;
  }

  @Override
  public Health health() {
    Health.Builder builder = Health.up().withDetail("host", host).withDetail("port", port);

    try (var connection = connectionFactory.createConnection()) {
      builder.withDetail("connected", connection.isOpen());
      checkLocalConsumers(builder);
      return builder.build();
    } catch (Exception e) {
      log.warn("RabbitMQ health check failed: {}");
      return Health.down(e).withDetail("host", host).withDetail("port", port).build();
    }
  }

  private void checkLocalConsumers(Health.Builder builder) {
    List<SimpleMessageListenerContainer> containers = listenerRegistrar.getContainers();
    if (containers.isEmpty()) {
      builder.withDetail("localConsumers", 0);
      return;
    }

    int running = 0;
    for (SimpleMessageListenerContainer container : containers) {
      if (container.isRunning()) {
        running++;
      } else {
        throw new IllegalStateException(
            "Consumer not running: queues=" + String.join(",", container.getQueueNames()));
      }
    }
    builder.withDetail("localConsumers", containers.size()).withDetail("runningConsumers", running);
  }
}
