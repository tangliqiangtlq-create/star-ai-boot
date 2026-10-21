package org.jeecg.config.aspect;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.aspect.H8DenyCallAuditAspect;
import org.jeecg.common.aspect.annotation.H8LoginAudit;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

@Component
@Slf4j
public class H8DenyAuditStartupVerifier {

    private static final String BASE_PACKAGE = "org.jeecg.modules.system.controller";
    private static final String[] REQUIRED_ENDPOINT_KEYWORDS = {
            "/sys/login",
            "/verifyTotp"
    };
    private static final Class<?>[] AUDIT_ANNOTATIONS = {H8LoginAudit.class, AutoLog.class};

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private H8DenyCallAuditAspect denyCallAuditAspect;

    @PostConstruct
    public void verify() {
        verifyAspectPresent();
        // H8 login-endpoint coverage targets system module only (LoginController classpath).
        // dcs/processengine/gateway load base-core but do not host /sys/login — aspect-only check.
        if (!isSystemLoginModulePresent()) {
            log.info(
                "H8DenyAuditStartupVerifier: PASSED (aspect loaded; system LoginController absent — skip endpoint keywords)"
            );
            return;
        }
        verifyLoginEndpointsAudited();
        log.info("H8DenyAuditStartupVerifier: PASSED (aspect loaded + 2 endpoints audited)");
    }

    private static boolean isSystemLoginModulePresent() {
        try {
            Class.forName(
                "org.jeecg.modules.system.controller.LoginController",
                false,
                Thread.currentThread().getContextClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void verifyAspectPresent() {
        if (denyCallAuditAspect == null) {
            throw new BeanInitializationException(
                    "H8_VERIFY_FAIL: H8DenyCallAuditAspect Bean not loaded; deny audit will be skipped"
            );
        }
    }

    private void verifyLoginEndpointsAudited() {
        Set<String> matchedKeywords = new HashSet<>();
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        scanner.addIncludeFilter(new AssignableTypeFilter(Object.class));

        try {
            Set<BeanDefinition> candidates = scanner.findCandidateComponents(BASE_PACKAGE);
            for (BeanDefinition bd : candidates) {
                String className = bd.getBeanClassName();
                if (className == null) continue;
                Class<?> clazz;
                try {
                    clazz = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
                } catch (Throwable ignore) {
                    continue;
                }
                RequestMapping classLevel = clazz.getAnnotation(RequestMapping.class);
                String[] classPaths = classLevel != null ? classLevel.value() : new String[]{""};
                for (Method m : clazz.getDeclaredMethods()) {
                    if (!hasAnyAuditAnnotation(m)) continue;
                    String methodPath = extractMethodPath(m);
                    for (String cp : classPaths) {
                        String combined = (cp == null ? "" : cp) + (methodPath == null ? "" : methodPath);
                        checkKeywords(combined, matchedKeywords);
                        checkKeywords(m.getName(), matchedKeywords);
                    }
                }
            }
        } catch (Throwable ignore) {
        }

        try {
            String[] beanNames = applicationContext.getBeanNamesForAnnotation(RestController.class);
            for (String beanName : beanNames) {
                Object bean = applicationContext.getBean(beanName);
                if (bean == null) continue;
                Class<?> clazz = bean.getClass();
                // Unwrap CGLIB/JDK proxy so class-level @RequestMapping("/sys") is visible
                while (clazz.getName().contains("$$")) {
                    clazz = clazz.getSuperclass();
                    if (clazz == null || Object.class.equals(clazz)) break;
                }
                if (clazz == null) continue;
                RequestMapping classLevel = clazz.getAnnotation(RequestMapping.class);
                String[] classPaths = classLevel != null
                    ? (classLevel.value().length > 0 ? classLevel.value() : classLevel.path())
                    : new String[]{""};
                if (classPaths == null || classPaths.length == 0) classPaths = new String[]{""};
                while (clazz != null && !Object.class.equals(clazz)) {
                    for (Method m : clazz.getDeclaredMethods()) {
                        if (!hasAnyAuditAnnotation(m)) continue;
                        String methodPath = extractMethodPath(m);
                        // Also match annotation description text (e.g. "Login API: /sys/login")
                        H8LoginAudit h8 = m.getAnnotation(H8LoginAudit.class);
                        if (h8 != null) {
                            checkKeywords(h8.value(), matchedKeywords);
                            checkKeywords(h8.endpoint(), matchedKeywords);
                        }
                        for (String cp : classPaths) {
                            String combined = (cp == null ? "" : cp) + (methodPath == null ? "" : methodPath);
                            checkKeywords(combined, matchedKeywords);
                            checkKeywords(m.getName(), matchedKeywords);
                        }
                    }
                    clazz = clazz.getSuperclass();
                }
            }
        } catch (Throwable ignore) {
        }

        if (matchedKeywords.size() < REQUIRED_ENDPOINT_KEYWORDS.length) {
            StringBuilder missing = new StringBuilder();
            for (String kw : REQUIRED_ENDPOINT_KEYWORDS) {
                if (!matchedKeywords.contains(kw)) {
                    if (missing.length() > 0) missing.append(",");
                    missing.append(kw);
                }
            }
            throw new BeanInitializationException(
                    "H8_VERIFY_FAIL: Missing @H8LoginAudit/@AutoLog(LOGIN) on endpoints: "
                            + missing + " (matched=" + matchedKeywords + ")"
            );
        }
        log.info("H8 login endpoints audit coverage: matchedKeywords={}", matchedKeywords);
    }

    private static boolean hasAnyAuditAnnotation(Method m) {
        for (Class<?> a : AUDIT_ANNOTATIONS) {
            if (m.isAnnotationPresent((Class<? extends java.lang.annotation.Annotation>) a)) {
                return true;
            }
        }
        return false;
    }

    private static String extractMethodPath(Method m) {
        try {
            RequestMapping rm = m.getAnnotation(RequestMapping.class);
            if (rm != null && rm.value().length > 0) return rm.value()[0];
            org.springframework.web.bind.annotation.PostMapping pm = m.getAnnotation(org.springframework.web.bind.annotation.PostMapping.class);
            if (pm != null && pm.value().length > 0) return pm.value()[0];
            org.springframework.web.bind.annotation.GetMapping gm = m.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class);
            if (gm != null && gm.value().length > 0) return gm.value()[0];
            org.springframework.web.bind.annotation.PutMapping pum = m.getAnnotation(org.springframework.web.bind.annotation.PutMapping.class);
            if (pum != null && pum.value().length > 0) return pum.value()[0];
            org.springframework.web.bind.annotation.DeleteMapping dm = m.getAnnotation(org.springframework.web.bind.annotation.DeleteMapping.class);
            if (dm != null && dm.value().length > 0) return dm.value()[0];
        } catch (Throwable ignore) {
        }
        return null;
    }

    private static void checkKeywords(String text, Set<String> matched) {
        if (text == null) return;
        for (String kw : REQUIRED_ENDPOINT_KEYWORDS) {
            if (matched.contains(kw)) continue;
            if (text.toLowerCase().contains(kw.toLowerCase())) {
                matched.add(kw);
            }
        }
    }
}
