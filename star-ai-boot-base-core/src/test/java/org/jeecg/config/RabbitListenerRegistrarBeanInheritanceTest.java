package org.jeecg.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.util.List;
import org.jeecg.common.mq.RocketMQListener;
import org.jeecg.common.mq.RocketMQMessageListener;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

class RabbitListenerRegistrarBeanInheritanceTest {

  @RocketMQMessageListener(topic = "unit.test.queue.a", consumerGroup = "gA")
  static class QueueAListener implements RocketMQListener<String> {
    @Override
    public void onMessage(String message) {}
  }

  @RocketMQMessageListener(topic = "unit.test.queue.b", consumerGroup = "gB")
  static class QueueBListener implements RocketMQListener<byte[]> {
    @Override
    public void onMessage(byte[] message) {}
  }

  @Test
  void registrar_creates_container_per_listener_sets_requeue_false_and_starts()
      throws Exception {
    ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(connectionFactory);
    factory.setConcurrentConsumers(2);
    factory.setMaxConcurrentConsumers(8);
    factory.setPrefetchCount(50);
    factory.setDefaultRequeueRejected(false);

    RabbitMqListenerRegistrar registrar =
        new RabbitMqListenerRegistrar(
            connectionFactory,
            factory,
            mock(org.springframework.amqp.support.converter.MessageConverter.class));

    java.lang.reflect.Method registerListener =
        RabbitMqListenerRegistrar.class.getDeclaredMethod("registerListener", Object.class);
    registerListener.setAccessible(true);
    registerListener.invoke(registrar, new QueueAListener());
    registerListener.invoke(registrar, new QueueBListener());

    Field containersField = RabbitMqListenerRegistrar.class.getDeclaredField("containers");
    containersField.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<SimpleMessageListenerContainer> containers =
        (List<SimpleMessageListenerContainer>) containersField.get(registrar);

    assertThat(containers).hasSize(2);

    SimpleMessageListenerContainer cA = containers.get(0);
    SimpleMessageListenerContainer cB = containers.get(1);

    assertThat(readStringArrFrom(cA, "queueNames")).containsExactly("unit.test.queue.a");
    assertThat(readStringArrFrom(cB, "queueNames")).containsExactly("unit.test.queue.b");
    assertThat(readBoolFrom(cA, "defaultRequeueRejected")).isFalse();
    assertThat(readBoolFrom(cB, "defaultRequeueRejected")).isFalse();

    int ccA = readIntFrom(cA, "concurrentConsumers");
    int mcA = readIntFrom(cA, "maxConcurrentConsumers");
    int pfA = readIntFrom(cA, "prefetchCount");
    int ccB = readIntFrom(cB, "concurrentConsumers");
    int mcB = readIntFrom(cB, "maxConcurrentConsumers");
    int pfB = readIntFrom(cB, "prefetchCount");

    assertThat(ccA).isEqualTo(2);
    assertThat(mcA).isEqualTo(8);
    assertThat(pfA).isEqualTo(50);
    assertThat(ccB).isEqualTo(2);
    assertThat(mcB).isEqualTo(8);
    assertThat(pfB).isEqualTo(50);

    assertThat(cA.isRunning()).isTrue();
    assertThat(cB.isRunning()).isTrue();

    assertThat(readObjFrom(cA, "messageListener", MessageListener.class)).isNotNull();
    assertThat(readObjFrom(cB, "messageListener", MessageListener.class)).isNotNull();
  }

  private static String[] readStringArrFrom(Object o, String fieldName) {
    return readObjFrom(o, fieldName, String[].class);
  }

  private static int readIntFrom(Object o, String fieldName) {
    Class<?> c = o.getClass();
    while (c != null && c != Object.class) {
      try {
        Field f = c.getDeclaredField(fieldName);
        f.setAccessible(true);
        Object v = f.get(o);
        if (v instanceof Integer i) return i;
      } catch (NoSuchFieldException ignored) {
        c = c.getSuperclass();
      } catch (IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }
    throw new AssertionError("Field " + fieldName + " not found on " + o.getClass());
  }

  private static boolean readBoolFrom(Object o, String fieldName) {
    Class<?> c = o.getClass();
    while (c != null && c != Object.class) {
      try {
        Field f = c.getDeclaredField(fieldName);
        f.setAccessible(true);
        Object v = f.get(o);
        if (v instanceof Boolean b) return b;
      } catch (NoSuchFieldException ignored) {
        c = c.getSuperclass();
      } catch (IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }
    throw new AssertionError("Field " + fieldName + " not found on " + o.getClass());
  }

  @SuppressWarnings("unchecked")
  private static <T> T readObjFrom(Object o, String fieldName, Class<T> type) {
    Class<?> c = o.getClass();
    while (c != null && c != Object.class) {
      try {
        Field f = c.getDeclaredField(fieldName);
        f.setAccessible(true);
        Object v = f.get(o);
        return (T) v;
      } catch (NoSuchFieldException ignored) {
        c = c.getSuperclass();
      } catch (IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }
    throw new AssertionError("Field " + fieldName + " not found on " + o.getClass());
  }
}
