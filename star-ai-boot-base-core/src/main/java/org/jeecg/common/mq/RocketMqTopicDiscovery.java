package org.jeecg.common.mq;

import cn.hutool.core.util.StrUtil;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.mq.RocketMQMessageListener;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

/**
 * Automatically discover MQ queue names from classpath, no need to maintain hard coding list.
 *
 * <p>Discovered sources (merge by priority to remove duplicates):
 *
 * <ol>
 *   <li>{@link RocketMQMessageListener#topic()} — Consumer Listener class
 *   <li>{@link RocketMQTopic#value()} — Producer Topic declaration on class/Field (optional, for pure producer services without Listener)
 * </ol>
 */
@Slf4j
public final class RocketMqTopicDiscovery {

  /** The default scan package path is consistent with {@code jeecg.mq.topic-scan-packages} when it is not configured. */
  public static final String DEFAULT_SCAN_PACKAGE = "org.jeecg";

  private RocketMqTopicDiscovery() {}

  /** Topics are discovered using the default package {@link #DEFAULT_SCAN_PACKAGE}. */
  public static Set<String> discover() {
    return discover(DEFAULT_SCAN_PACKAGE);
  }

  public static Set<String> discover(String... basePackages) {
    Set<String> topics = new LinkedHashSet<>();
    if (basePackages == null) {
      return Collections.emptySet();
    }

    for (String basePackage : basePackages) {
      if (StrUtil.isBlank(basePackage)) {
        continue;
      }
      String pkg = basePackage.trim();
      Reflections reflections = createReflections(pkg);
      collectFromListeners(reflections, topics);
      collectFromTopicAnnotations(reflections, topics);
    }

    log.debug("MQ queues discovered from classpath: {}", topics);
    return Collections.unmodifiableSet(topics);
  }

  private static Reflections createReflections(String basePackage) {
    // reflections 0.10+: getTypesAnnotatedWith depends on TypesAnnotated + SubTypes;
    // Field declaration Topic also requires FieldsAnnotated. Don't expandSuperTypes, avoid classpath
    // Refresh the old javax.servlet/Sentinel interface signature WARN.
    return new Reflections(
        new ConfigurationBuilder()
            .forPackages(basePackage)
            .setExpandSuperTypes(false)
            .setScanners(Scanners.TypesAnnotated, Scanners.SubTypes, Scanners.FieldsAnnotated));
  }

  private static void collectFromListeners(Reflections reflections, Set<String> topics) {
    for (Class<?> clazz : reflections.getTypesAnnotatedWith(RocketMQMessageListener.class)) {
      RocketMQMessageListener annotation = clazz.getAnnotation(RocketMQMessageListener.class);
      if (annotation == null) {
        continue;
      }
      addTopic(topics, annotation.topic(), clazz.getName());
    }
  }

  private static void collectFromTopicAnnotations(Reflections reflections, Set<String> topics) {
    for (Class<?> clazz : reflections.getTypesAnnotatedWith(RocketMQTopic.class)) {
      RocketMQTopic annotation = clazz.getAnnotation(RocketMQTopic.class);
      if (annotation != null) {
        addTopic(topics, annotation.value(), clazz.getName());
      }
    }

    for (Field field : reflections.getFieldsAnnotatedWith(RocketMQTopic.class)) {
      RocketMQTopic annotation = field.getAnnotation(RocketMQTopic.class);
      if (annotation == null) {
        continue;
      }
      String topic = annotation.value();
      if (StrUtil.isBlank(topic)) {
        topic = readStringConstant(field);
      }
      addTopic(topics, topic, field.getDeclaringClass().getName() + "#" + field.getName());
    }
  }

  private static String readStringConstant(Field field) {
    if (!String.class.equals(field.getType()) || !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
      return null;
    }
    try {
      Object value = field.get(null);
      return value instanceof String ? (String) value : null;
    } catch (IllegalAccessException e) {
      return null;
    }
  }

  private static void addTopic(Set<String> topics, String topic, String source) {
    if (StrUtil.isBlank(topic)) {
      log.warn("Skip blank MQ queue from {}", source);
      return;
    }
    topics.add(topic.trim());
  }
}
