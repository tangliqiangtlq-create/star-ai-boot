package org.jeecg.config;

import cn.hutool.core.util.StrUtil;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.mq.RocketMqTopicDiscovery;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
@Slf4j
@ConditionalOnProperty(name = "spring.rabbitmq.host")
public class RabbitMQConfig {

  public static final String DLX_EXCHANGE = "ops.dlx";
  public static final String DLQ_QUEUE = "ops.alert";
  public static final String DLQ_ROUTING_KEY = "ops.alert";

  public static final int CONSUMER_RETRY_MAX_ATTEMPTS = 3;
  public static final long CONSUMER_RETRY_INITIAL_MS = 1_000L;
  public static final double CONSUMER_RETRY_MULTIPLIER = 2.0;
  public static final long CONSUMER_RETRY_MAX_MS = 5_000L;

  @Value("${jeecg.mq.auto-create-queues:true}")
  private boolean autoCreateQueues;

  /**
   * Local/SIT only: when an existing queue lacks {@code x-dead-letter-*} args, delete and redeclare
   * with DLX. Default false — production must drain/delete in a change window (see handbook C1-1a).
   */
  @Value("${jeecg.mq.recreate-on-arg-mismatch:false}")
  private boolean recreateOnArgMismatch;

  @Value("${jeecg.mq.topic-scan-packages:" + RocketMqTopicDiscovery.DEFAULT_SCAN_PACKAGE + "}")
  private String topicScanPackages;

  private final ConnectionFactory connectionFactory;

  public RabbitMQConfig(ConnectionFactory connectionFactory) {
    this.connectionFactory = connectionFactory;
  }

  @Bean
  public MessageConverter rabbitMessageConverter() {
    return new Jackson2JsonMessageConverter();
  }

  @Bean
  public RabbitTemplate rabbitTemplate(
      ConnectionFactory connectionFactory, MessageConverter rabbitMessageConverter) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(rabbitMessageConverter);

    template.setConfirmCallback(
        (correlationData, ack, cause) -> {
          if (ack) {
            return;
          }
          String id =
              correlationData == null ? "(null)" : (correlationData.getId() == null ? "(no-id)" : correlationData.getId());
          log.warn(
              "[RMQ-PUBLISHER-CONFIRM] NACK correlationData={}, cause={}", id, cause);
        });

    template.setReturnsCallback(
        returned -> {
          if (returned == null) {
            return;
          }
          log.warn(
              "[RMQ-PUBLISHER-RETURN] replyCode={} replyText={} exchange={} routingKey={} message={}",
              returned.getReplyCode(),
              returned.getReplyText(),
              returned.getExchange(),
              returned.getRoutingKey(),
              returned.getMessage() == null ? null : returned.getMessage().getMessageProperties());
        });
    return template;
  }

  @Bean
  public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
    return new RabbitAdmin(connectionFactory);
  }

  @Bean
  public TopicExchange dlxExchange() {
    return new TopicExchange(DLX_EXCHANGE, true, false);
  }

  @Bean
  public Queue dlqOpsAlert() {
    return new Queue(DLQ_QUEUE, true, false, false);
  }

  @Bean
  public Binding dlqOpsAlertBinding(Queue dlqOpsAlert, TopicExchange dlxExchange) {
    return BindingBuilder.bind(dlqOpsAlert).to(dlxExchange).with(DLQ_ROUTING_KEY);
  }

  /**
   * Must declare as {@link SimpleRabbitListenerContainerFactory} (not the interface) so
   * {@link RabbitMqListenerRegistrar} can inject it by type + {@code @Qualifier}.
   */
  @Bean
  public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
      ConnectionFactory connectionFactory, MessageConverter rabbitMessageConverter) {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setMessageConverter(rabbitMessageConverter);

    factory.setDefaultRequeueRejected(false);

    RetryTemplate retryTemplate = new RetryTemplate();
    RetryPolicy retryPolicy = new SimpleRetryPolicy(CONSUMER_RETRY_MAX_ATTEMPTS);
    retryTemplate.setRetryPolicy(retryPolicy);

    ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
    backOff.setInitialInterval(CONSUMER_RETRY_INITIAL_MS);
    backOff.setMultiplier(CONSUMER_RETRY_MULTIPLIER);
    backOff.setMaxInterval(CONSUMER_RETRY_MAX_MS);
    retryTemplate.setBackOffPolicy(backOff);

    RetryOperationsInterceptor retryInterceptor = RetryInterceptorBuilder.stateless()
        .retryOperations(retryTemplate)
        .build();
    factory.setAdviceChain(retryInterceptor);

    factory.setConcurrentConsumers(2);
    factory.setMaxConcurrentConsumers(8);
    factory.setPrefetchCount(50);
    return factory;
  }

  @PostConstruct
  public void declareQueues() {
    RabbitAdmin admin = new RabbitAdmin(connectionFactory);
    // Build infra objects locally — do NOT call @Bean methods from @PostConstruct
    // (triggers "bean currently in creation" circular reference).
    TopicExchange dlx = new TopicExchange(DLX_EXCHANGE, true, false);
    Queue dlq = new Queue(DLQ_QUEUE, true, false, false);
    Binding dlqBinding = BindingBuilder.bind(dlq).to(dlx).with(DLQ_ROUTING_KEY);
    try {
      admin.declareExchange(dlx);
      admin.declareQueue(dlq);
      admin.declareBinding(dlqBinding);
      log.info(
          "RabbitMQ DLQ declared: exchange={} queue={} routingKey={}",
          DLX_EXCHANGE,
          DLQ_QUEUE,
          DLQ_ROUTING_KEY);
    } catch (Exception e) {
      log.warn("RabbitMQ DLQ declare failed (service continues): {}", e.getMessage());
    }

    if (!autoCreateQueues) {
      log.info("RabbitMQ auto-create-queues=false, skip queue pre-declare");
      return;
    }

    String[] packages =
        StrUtil.splitToArray(
            StrUtil.blankToDefault(topicScanPackages, RocketMqTopicDiscovery.DEFAULT_SCAN_PACKAGE),
            ',');
    Set<String> queues = RocketMqTopicDiscovery.discover(packages);
    if (queues.isEmpty()) {
      log.info("RabbitMQ: no queues discovered under packages={}", topicScanPackages);
      return;
    }

    Map<String, Object> dlqArgs = new HashMap<>();
    dlqArgs.put("x-dead-letter-exchange", DLX_EXCHANGE);
    dlqArgs.put("x-dead-letter-routing-key", DLQ_ROUTING_KEY);

    for (String queueName : queues) {
      Queue q = new Queue(queueName, true, false, false, dlqArgs);
      try {
        admin.declareQueue(q);
        log.info("RabbitMQ queue declared (DLX attached): {}", queueName);
      } catch (Exception e) {
        if (recreateOnArgMismatch && isPreconditionFailed(e)) {
          try {
            admin.deleteQueue(queueName);
            admin.declareQueue(q);
            log.warn(
                "RabbitMQ queue recreated with DLX after arg mismatch: {} (jeecg.mq.recreate-on-arg-mismatch=true)",
                queueName);
            continue;
          } catch (Exception recreateEx) {
            log.warn(
                "RabbitMQ queue recreate failed (service continues): queue={}, reason={}",
                queueName,
                recreateEx.toString());
            continue;
          }
        }
        log.warn(
            "RabbitMQ queue declare failed (service continues): queue={}, reason={} — local/SIT: delete queue and restart, or set jeecg.mq.recreate-on-arg-mismatch=true",
            queueName,
            e.toString());
      }
    }
  }

  private static boolean isPreconditionFailed(Throwable t) {
    for (Throwable c = t; c != null; c = c.getCause()) {
      String msg = c.getMessage();
      if (msg != null
          && (msg.contains("PRECONDITION_FAILED") || msg.contains("inequivalent arg"))) {
        return true;
      }
    }
    return false;
  }
}
