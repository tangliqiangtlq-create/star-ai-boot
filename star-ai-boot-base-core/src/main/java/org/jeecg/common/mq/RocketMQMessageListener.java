package org.jeecg.common.mq;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an MQ consumer (queue names keep the original RocketMQ Topic naming for migration).
 *
 * <p>Registered as a RabbitMQ listener by {@link org.jeecg.config.RabbitMqListenerRegistrar}.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RocketMQMessageListener {

  /** Queue name (original RocketMQ Topic name). */
  String topic();

  /** Consumer group (competing consumers on the same RabbitMQ queue; kept for logging/monitoring). */
  String consumerGroup();

  /** Max retry count (on RabbitMQ failure the message is requeued; default -1 means no retry). */
  int maxReconsumeTimes() default -1;
}
