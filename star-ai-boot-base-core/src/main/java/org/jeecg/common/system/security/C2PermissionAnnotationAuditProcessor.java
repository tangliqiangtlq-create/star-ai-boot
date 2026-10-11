package org.jeecg.common.system.security;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaIgnore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@Component
public class C2PermissionAnnotationAuditProcessor implements SmartInitializingSingleton, ApplicationContextAware {

  private static final String C2_APT_PHASE_ENV = "C2_APT_PHASE";
  private static final String PHASE_WARN = "WARN";
  private static final String PHASE_BLOCK = "BLOCK";

  private static final Set<String> C2_ANON_WHITELIST = Set.of("health", "ready", "live");

  private static final List<Class<? extends java.lang.annotation.Annotation>> AUTH_ANNOTATIONS =
      Arrays.asList(SaCheckLogin.class, SaCheckPermission.class, SaCheckRole.class, SaIgnore.class);

  private ApplicationContext ctx;

  @Autowired(required = false)
  private MeterRegistry meterRegistry;

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.ctx = applicationContext;
  }

  @Override
  public void afterSingletonsInstantiated() {
    String phase = PHASE_WARN;
    try {
      String envPhase = ctx.getEnvironment().getProperty(C2_APT_PHASE_ENV);
      if (envPhase != null && !envPhase.trim().isEmpty()) {
        phase = envPhase.trim().toUpperCase();
      }
    } catch (Exception ignore) {
    }

    log.info("[SEC-C2-APT] Startup scan phase={} (set {}=BLOCK after 7-day WARN window)", phase, C2_APT_PHASE_ENV);

    String[] beanNames = ctx.getBeanNamesForAnnotation(RestController.class);
    int warnCount = 0;

    for (String bn : beanNames) {
      Object bean;
      try {
        bean = ctx.getBean(bn);
      } catch (BeansException ignore) {
        continue;
      }
      Class<?> clazz = bean.getClass();

      RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(clazz, RequestMapping.class);
      String classPath = "";
      if (classMapping != null && classMapping.value().length > 0) {
        classPath = classMapping.value()[0];
      }

      for (Method m : clazz.getDeclaredMethods()) {
        int mods = m.getModifiers();
        if (!java.lang.reflect.Modifier.isPublic(mods)) {
          continue;
        }
        if (!isHttpHandlerMethod(m)) {
          continue;
        }

        boolean hasAuth = hasAnyAuthAnnotation(clazz, m);
        if (hasAuth) {
          continue;
        }

        String methodName = m.getName();
        if (C2_ANON_WHITELIST.contains(methodName)) {
          continue;
        }
        boolean whitelistedPath = false;
        for (String w : C2_ANON_WHITELIST) {
          if (classPath.endsWith("/" + w) || classPath.equals(w)) {
            whitelistedPath = true;
            break;
          }
        }
        if (whitelistedPath) {
          continue;
        }

        warnCount++;
        String fqmn = clazz.getName() + "#" + methodName;

        if (PHASE_BLOCK.equals(phase)) {
          String msg =
              "SEC-C2-APT-BLOCK Phase-2 BLOCK: No auth annotation on public method "
                  + fqmn
                  + "; add @SaCheckLogin/@SaCheckRole/@SaCheckPermission or add to C2_ANON_WHITELIST.";
          log.error("\n========== C2 APT GUARD BLOCK STARTUP ==========\n{}\n=================================================", msg);
          throw new BeanInitializationException(msg);
        }

        log.error(
            "SEC-C2-APT-WARN Phase-1 No auth annotation on Class#method, will BLOCK startup in Phase-2 after 7d. class={} method={} classPath={}",
            clazz.getName(),
            methodName,
            classPath);

        if (meterRegistry != null) {
          try {
            Counter.builder("c2_apt_phase1_hit_total")
                .tag("class", clazz.getSimpleName())
                .tag("method", methodName)
                .description("C2 APT Phase-1 WARN hit count (public REST method without auth annotation)")
                .register(meterRegistry)
                .increment();
          } catch (Exception ignore) {
          }
        }
      }
    }

    log.info("[SEC-C2-APT] Startup scan done. total_hits={} phase={}", warnCount, phase);
  }

  private static boolean isHttpHandlerMethod(Method m) {
    return AnnotatedElementUtils.hasAnnotation(m, RequestMapping.class)
        || AnnotatedElementUtils.hasAnnotation(m, GetMapping.class)
        || AnnotatedElementUtils.hasAnnotation(m, PostMapping.class)
        || AnnotatedElementUtils.hasAnnotation(m, PutMapping.class)
        || AnnotatedElementUtils.hasAnnotation(m, DeleteMapping.class)
        || AnnotatedElementUtils.hasAnnotation(m, PatchMapping.class);
  }

  private static boolean hasAnyAuthAnnotation(Class<?> clazz, Method m) {
    for (Class<? extends java.lang.annotation.Annotation> ann : AUTH_ANNOTATIONS) {
      if (AnnotatedElementUtils.hasAnnotation(m, ann)) {
        return true;
      }
      if (AnnotatedElementUtils.hasAnnotation(clazz, ann)) {
        return true;
      }
    }
    return false;
  }
}
