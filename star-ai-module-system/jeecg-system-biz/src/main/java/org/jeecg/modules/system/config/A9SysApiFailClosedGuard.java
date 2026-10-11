package org.jeecg.modules.system.config;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckSafe;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A9 FAIL-CLOSED guard: at startup, scans every Spring MVC controller whose request-mapping
 * covers {@code /sys/api/**} and refuses to boot if any handler method on that controller lacks
 * an Sa-Token authentication / authorization annotation.
 *
 * <p>The {@code /sys/api/**} namespace is reserved for Feign S2S calls and must NEVER be
 * anonymously callable. Two layers of defence protect this boundary:
 * <ol>
 *   <li>Gateway {@code InternalSysApiBlockWebFilter} — blocks any public ingress request whose
 *       path contains {@code /sys/api/}.</li>
 *   <li><em>THIS GUARD</em> — boot-time enumerates every mapped controller, finds those that
 *       handle {@code /sys/api*} URLs, and throws {@link BeanInitializationException} for every
 *       method that is missing ANY of {@code @SaCheckLogin}, {@code @SaCheckRole},
 *       {@code @SaCheckPermission}, {@code @SaCheckSafe}.</li>
 * </ol>
 *
 * <p>The guard intentionally tolerates annotations declared either on the method OR on the
 * enclosing class (type-level @SaCheckLogin counts towards every method). Type-level coverage is
 * counted once; the report lists only the methods whose effective annotation set is EMPTY so
 * engineers can fix them one-by-one.
 */
@Slf4j
@Component
public class A9SysApiFailClosedGuard implements BeanPostProcessor {

  private static final List<Class<? extends Annotation>> AUTH_ANNOTATIONS =
      Arrays.asList(
          SaCheckLogin.class,
          SaCheckRole.class,
          SaCheckPermission.class,
          SaCheckSafe.class);

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    Class<?> beanClass = bean.getClass();

    boolean isController =
        AnnotatedElementUtils.hasAnnotation(beanClass, RestController.class)
            || AnnotatedElementUtils.hasAnnotation(beanClass, Controller.class);
    if (!isController) {
      return bean;
    }

    boolean coversSysApi = coversSysApiNamespace(beanClass);
    if (!coversSysApi) {
      return bean;
    }

    List<String> unguarded = scanUnguardedMethods(beanClass);
    if (!unguarded.isEmpty()) {
      String err =
          "[A9 FAIL-CLOSED][sys/api] controller "
              + beanClass.getName()
              + " exposes "
              + unguarded.size()
              + " handler method(s) under /sys/api* WITHOUT any Sa-Token auth annotation. "
              + "Anonymous access to S2S APIs is disallowed. Add @SaCheckLogin / "
              + "@SaCheckPermission / @SaCheckRole to each listed method (or declare the "
              + "annotation at the type level on the controller class). Unguarded: "
              + String.join(" | ", unguarded);
      log.error(err);
      throw new BeanInitializationException(err);
    }

    log.info(
        "[A9 FAIL-CLOSED][sys/api] controller={} OK — all methods under /sys/api* carry Sa-Token auth annotations",
        beanClass.getName());
    return bean;
  }

  private static boolean coversSysApiNamespace(Class<?> kls) {
    RequestMapping typeMapping =
        AnnotatedElementUtils.findMergedAnnotation(kls, RequestMapping.class);
    if (typeMapping != null) {
      String[] paths = typeMapping.value().length > 0 ? typeMapping.value() : typeMapping.path();
      for (String p : paths) {
        if (p.startsWith("/sys/api")) {
          return true;
        }
        if (p.startsWith("sys/api")) {
          return true;
        }
      }
    }
    for (Method m : kls.getDeclaredMethods()) {
      RequestMapping methodMapping =
          AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class);
      if (methodMapping == null) {
        continue;
      }
      String[] paths =
          methodMapping.value().length > 0 ? methodMapping.value() : methodMapping.path();
      for (String p : paths) {
        if (p.contains("/sys/api/") || p.startsWith("/sys/api") || p.startsWith("sys/api")) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean hasAnyAuthAnnotationOnClass(Class<?> kls) {
    for (Class<? extends Annotation> a : AUTH_ANNOTATIONS) {
      if (AnnotatedElementUtils.hasAnnotation(kls, a)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasAnyAuthAnnotationOnMethod(Method m) {
    for (Class<? extends Annotation> a : AUTH_ANNOTATIONS) {
      if (AnnotatedElementUtils.hasAnnotation(m, a)) {
        return true;
      }
    }
    return false;
  }

  private static List<String> scanUnguardedMethods(Class<?> kls) {
    boolean typeLevelCovered = hasAnyAuthAnnotationOnClass(kls);
    List<String> out = new ArrayList<>();
    for (Method m : collectHandlerMethods(kls)) {
      if (typeLevelCovered) {
        continue;
      }
      if (!hasAnyAuthAnnotationOnMethod(m)) {
        out.add(describe(m));
      }
    }
    return out;
  }

  private static List<Method> collectHandlerMethods(Class<?> kls) {
    List<Method> all = new ArrayList<>();
    Class<?> cursor = kls;
    while (cursor != null && cursor != Object.class) {
      for (Method m : cursor.getDeclaredMethods()) {
        if (isHandlerMethod(m)) {
          all.add(m);
        }
      }
      cursor = cursor.getSuperclass();
    }
    return all;
  }

  private static boolean isHandlerMethod(Method m) {
    return AnnotatedElementUtils.hasAnnotation(m, RequestMapping.class)
        || AnnotatedElementUtils.hasAnnotation(m, org.springframework.web.bind.annotation.GetMapping.class)
        || AnnotatedElementUtils.hasAnnotation(m, org.springframework.web.bind.annotation.PostMapping.class)
        || AnnotatedElementUtils.hasAnnotation(m, org.springframework.web.bind.annotation.PutMapping.class)
        || AnnotatedElementUtils.hasAnnotation(m, org.springframework.web.bind.annotation.DeleteMapping.class)
        || AnnotatedElementUtils.hasAnnotation(m, org.springframework.web.bind.annotation.PatchMapping.class);
  }

  private static String describe(Method m) {
    String params =
        Arrays.stream(m.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(","));
    return m.getDeclaringClass().getSimpleName() + "#" + m.getName() + "(" + params + ")";
  }
}
