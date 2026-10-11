package org.jeecg.common.mq;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares an MQ queue (optional; keeps the original Topic naming).
 *
 * <p>Used so producer-only microservices without {@link RocketMQMessageListener} on the classpath
 * can still pre-declare queues at startup.
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * @RocketMQTopic("some_topic_name")
 * @Service
 * public class SomeProducerService { ... }
 * }</pre>
 */
@Documented
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RocketMQTopic {

  /** Queue name; may be omitted on a static String field, in which case the field constant is used. */
  String value() default "";
}
