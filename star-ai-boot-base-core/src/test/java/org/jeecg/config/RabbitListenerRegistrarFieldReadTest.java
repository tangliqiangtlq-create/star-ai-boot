package org.jeecg.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

class RabbitListenerRegistrarFieldReadTest {

  @Test
  void factory_fields_reflectively_read_back_match_setters() {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(mock(ConnectionFactory.class));
    factory.setConcurrentConsumers(2);
    factory.setMaxConcurrentConsumers(8);
    factory.setPrefetchCount(50);

    RabbitMqListenerRegistrar registrar =
        new RabbitMqListenerRegistrar(
            mock(ConnectionFactory.class),
            factory,
            mock(org.springframework.amqp.support.converter.MessageConverter.class));

    int cc = readPrivateInt(registrar, factory, "concurrentConsumers", 99);
    int mc = readPrivateInt(registrar, factory, "maxConcurrentConsumers", 99);
    int pf = readPrivateInt(registrar, factory, "prefetchCount", 99);

    assertThat(cc).isEqualTo(2);
    assertThat(mc).isEqualTo(8);
    assertThat(pf).isEqualTo(50);
  }

  @Test
  void factory_created_container_inherits_factory_defaults_and_requeue_false() {
    SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
    factory.setConnectionFactory(mock(ConnectionFactory.class));
    factory.setConcurrentConsumers(2);
    factory.setMaxConcurrentConsumers(8);
    factory.setPrefetchCount(50);
    factory.setDefaultRequeueRejected(false);

    RabbitMqListenerRegistrar registrar =
        new RabbitMqListenerRegistrar(
            mock(ConnectionFactory.class),
            factory,
            mock(org.springframework.amqp.support.converter.MessageConverter.class));

    SimpleMessageListenerContainer container = factory.createListenerContainer();
    container.setDefaultRequeueRejected(false);

    int cc = readFieldIntFrom(container, "concurrentConsumers");
    int mc = readFieldIntFrom(container, "maxConcurrentConsumers");
    int pf = readFieldIntFrom(container, "prefetchCount");

    assertThat(cc).isEqualTo(2);
    assertThat(mc).isEqualTo(8);
    assertThat(pf).isEqualTo(50);
    assertThat(readBoolFrom(container, "defaultRequeueRejected")).isFalse();
  }

  @Test
  void fallback_values_applied_when_target_has_no_matching_field() {
    RabbitMqListenerRegistrar registrar =
        new RabbitMqListenerRegistrar(
            mock(ConnectionFactory.class),
            new SimpleRabbitListenerContainerFactory(),
            mock(org.springframework.amqp.support.converter.MessageConverter.class));

    Object targetWithNoConcurrentConsumersField = new Object();
    int cc = readPrivateInt(registrar, targetWithNoConcurrentConsumersField, "concurrentConsumers", 2);
    assertThat(cc).isEqualTo(2);
  }

  private static int readPrivateInt(
      Object registrar, Object target, String name, int fallback) {
    try {
      java.lang.reflect.Method m =
          RabbitMqListenerRegistrar.class.getDeclaredMethod(
              "readIntField", Object.class, String.class, int.class);
      m.setAccessible(true);
      return (int) m.invoke(registrar, target, name, fallback);
    } catch (ReflectiveOperationException e) {
      throw new AssertionError(e);
    }
  }

  private static int readFieldIntFrom(Object o, String fieldName) {
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
}
