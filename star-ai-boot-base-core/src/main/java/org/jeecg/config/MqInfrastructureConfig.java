package org.jeecg.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * MQ infrastructure: uniformly enable async send and retry so microservice startup classes
 * do not miss @EnableAsync / @EnableRetry.
 *
 * <p>{@link org.jeecg.common.util.RocketMQUtil#sendRetry} always uses {@code mqSendExecutor},
 * regardless of whether rabbitmq.host is explicitly configured (AMQP defaults to localhost).
 */
@Configuration
@EnableAsync
@EnableRetry
@EnableScheduling
public class MqInfrastructureConfig {

  @Bean(name = "mqSendExecutor")
  @ConditionalOnMissingBean(name = "mqSendExecutor")
  public Executor mqSendExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(16);
    executor.setQueueCapacity(500);
    executor.setThreadNamePrefix("mq-send-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.initialize();
    return executor;
  }
}
