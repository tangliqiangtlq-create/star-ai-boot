package org.jeecg.common.mq;

/**
 * MQ message consumer interface (compatible with the original RocketMQ Listener API).
 *
 * @param <T> message body type
 */
public interface RocketMQListener<T> {

  void onMessage(T message);
}
